/*
 * Copyright 2021 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.permissions

import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig.UNRESTRICTED_USERNAME
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Resource
import com.netflix.spinnaker.fiat.model.resources.ResourceType
import com.netflix.spinnaker.fiat.permissions.sql.tables.references.PERMISSION
import com.netflix.spinnaker.fiat.permissions.sql.tables.references.RESOURCE
import com.netflix.spinnaker.fiat.permissions.sql.tables.references.USER
import com.netflix.spinnaker.fiat.permissions.sql.transactional
import com.netflix.spinnaker.fiat.permissions.sql.withRetry
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.routing.withPool
import org.jooq.*
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class SqlPermissionsRepository(
    private val clock: Clock,
    private val objectMapper: ObjectMapper,
    private val jooq: DSLContext,
    private val sqlRetryProperties: SqlRetryProperties,
    private val poolName: String,
    private val resources: List<com.netflix.spinnaker.fiat.model.resources.Resource>
    ) : PermissionsRepository {

    private val unrestrictedPermission = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofSeconds(10))
        .build(this::reloadUnrestricted)

    private val resourceTypes = resources.associateBy { r -> r.resourceType }.toMap()

    companion object {
        private val log = LoggerFactory.getLogger(SqlPermissionsRepository::class.java)

        private const val NO_UPDATED_AT = 0L

        private val fallbackLastModified = AtomicReference<Long>(null)
    }

    override fun put(permission: UserPermission): PermissionsRepository {
        val allResourcesByType = permission.allResources
            .groupBy { it.resourceType }
            .mapValues { (_, v) -> v.toSet() }

        withPool(poolName) {
            jooq.transactional(sqlRetryProperties.transactions) { ctx ->
                putAllResources(ctx, allResourcesByType)

                putUserPermission(ctx, permission.id, permission.isAdmin, allResourcesByType)
            }
        }

        return this
    }

    override fun putAllById(permissions: Map<String, UserPermission>?) {
        if (permissions == null || permissions.isEmpty()) {
            return
        }

        val allResourcesByType = permissions.values
            .map { it.allResources }
            .flatten()
            .groupBy { it.resourceType }
            .mapValues { (_, v) -> v.toMutableSet() }

        withPool(poolName) {

            jooq.transactional(sqlRetryProperties.transactions) { ctx ->
                putAllResources(ctx, allResourcesByType)
            }

            // insert/update users and permissions
            permissions.values.forEach { p ->
                val allResourcesByTypeForUser = p.allResources.groupBy { it.resourceType }.mapValues { (_, v) -> v.toSet() }

                // transaction per-user to avoid locking too long
                jooq.transactional(sqlRetryProperties.transactions) { ctx ->
                    putUserPermission(ctx, p.id, p.isAdmin, allResourcesByTypeForUser)
                }
            }

            // Tidy up deleted users and permissions
            jooq.transactional(sqlRetryProperties.transactions) { ctx ->
                val batch = mutableListOf<Query>()

                val existingIds = ctx.select(USER.ID).from(USER).fetch(USER.ID).toSet();

                // The `UserRoleSyncer` doesn't pass in the unrestricted username so make sure we don't delete it
                val toDelete = existingIds.minus(permissions.keys)
                    .minus(UNRESTRICTED_USERNAME)

                if (toDelete.isNotEmpty()) {
                    batch += ctx.deleteFrom(PERMISSION).where(PERMISSION.USER_ID.`in`(toDelete))
                    batch += ctx.deleteFrom(USER).where(USER.ID.`in`(toDelete))

                    ctx.batch(batch).execute()
                }
            }

            // Tidy up unreferenced resources
            jooq.transactional(sqlRetryProperties.transactions) { ctx ->
                val batch = mutableListOf<Query>()

                resourceTypes.forEach { (rt, _) ->
                    batch += ctx.deleteFrom(RESOURCE).where(
                        RESOURCE.RESOURCE_TYPE.eq(rt).and(
                            RESOURCE.RESOURCE_NAME.notIn(
                                ctx.selectDistinct(PERMISSION.RESOURCE_NAME)
                                    .from(PERMISSION)
                                    .where(PERMISSION.RESOURCE_TYPE.eq(rt))
                            )
                        )
                    )
                }

                ctx.batch(batch).execute()
            }
        }
    }
    override fun get(id: String): Optional<UserPermission> {
        if (UNRESTRICTED_USERNAME == id) {
            return Optional.of(getUnrestrictedUserPermission())
        }
        return getFromDatabase(id)
    }

    override fun getAllById(): Map<String, UserPermission> {
        return getAllByRoles(null)
    }

    override fun getAllByRoles(anyRoles: List<String>?): Map<String, UserPermission> {
        // If the role list is null, return every user
        // If the role list is empty, return the unrestricted user
        // Otherwise, return the users with the list of roles

        val unrestrictedUser = getUnrestrictedUserPermission()

        val result = mutableMapOf<String, UserPermission>()

        if (anyRoles != null) {
            result[UNRESTRICTED_USERNAME] = unrestrictedUser
            if (anyRoles.isEmpty()) {
                return result
            }
        }

        return withPool(poolName) {
            jooq.withRetry(sqlRetryProperties.reads) { ctx ->
                var resourceQuery = ctx
                    .selectDistinct(RESOURCE.RESOURCE_TYPE, RESOURCE.RESOURCE_NAME, RESOURCE.BODY)
                    .from(RESOURCE)
                    .let {
                        if (anyRoles != null) {
                            it.join(PERMISSION)
                                .on(PERMISSION.RESOURCE_TYPE.eq(RESOURCE.RESOURCE_TYPE).and(
                                    PERMISSION.RESOURCE_NAME.eq(RESOURCE.RESOURCE_NAME)
                                ))
                                .where(PERMISSION.USER_ID.`in`(
                                    ctx.selectDistinct(USER.ID)
                                        .from(USER)
                                        .join(PERMISSION)
                                        .on(USER.ID.eq(PERMISSION.USER_ID))
                                        .where(PERMISSION.RESOURCE_TYPE.eq(ResourceType.ROLE).and(
                                            PERMISSION.RESOURCE_NAME.`in`(anyRoles)
                                        ))
                                ))
                        }
                        it
                    }

                val existingResources = resourceQuery
                    .fetch()
                    .intoGroups(RESOURCE.RESOURCE_TYPE)
                    .mapValues { e ->
                        e.value.intoMap(RESOURCE.RESOURCE_NAME).mapValues { v ->
                            objectMapper.readValue(v.value.get(RESOURCE.BODY), resourceTypes[e.key]!!.javaClass)
                        }
                    }

                // Read in all the users with the role and combine with resources
                ctx.select(USER.ID, USER.ADMIN, PERMISSION.RESOURCE_TYPE, PERMISSION.RESOURCE_NAME)
                    .from(USER)
                    .leftJoin(PERMISSION)
                    .on(USER.ID.eq(PERMISSION.USER_ID))
                    .let {
                        if (anyRoles != null) {
                            it.where(PERMISSION.USER_ID.`in`(
                                    ctx.selectDistinct(USER.ID)
                                        .from(USER)
                                        .join(PERMISSION)
                                        .on(USER.ID.eq(PERMISSION.USER_ID))
                                        .where(PERMISSION.RESOURCE_TYPE.eq(ResourceType.ROLE).and(
                                            PERMISSION.RESOURCE_NAME.`in`(anyRoles)
                                        ))
                                )
                            )
                        }
                        it
                    }
                    .fetch()
                    .groupingBy { r -> r.get(USER.ID) }
                    .foldTo (
                        result,
                        { k, e -> UserPermission().setId(k).setAdmin(e.get(USER.ADMIN)).merge(unrestrictedUser) },
                        { _, acc, e ->
                            val resourcesForType = existingResources.getOrDefault(e.get(PERMISSION.RESOURCE_TYPE), emptyMap())
                            val resource = resourcesForType[e.get(PERMISSION.RESOURCE_NAME)]
                            if (resource != null) {
                                acc.addResource(resource)
                            }
                            acc
                        }
                    )
            }
        }
    }

    override fun remove(id: String) {
        withPool(poolName) {
            jooq.transactional(sqlRetryProperties.transactions) { ctx ->
                // Delete permissions
                ctx.delete(PERMISSION)
                    .where(PERMISSION.USER_ID.eq(id))
                    .execute()

                // Delete user
                ctx.delete(USER)
                    .where(USER.ID.eq(id))
                    .execute()
            }
        }
    }

    private fun putUserPermission(ctx: DSLContext, id: String, admin: Boolean, allResourcesByTypeForUser: Map<ResourceType, Set<Resource>>) {
        // Most of the time we'll be updating the timestamp so this is cheaper
        val rows = ctx.update(USER)
            .set(USER.ADMIN, admin)
            .set(USER.UPDATED_AT, clock.millis())
            .where(USER.ID.eq(id))
            .execute()
        if (rows == 0) {
            ctx.insertInto(USER, USER.ID, USER.ADMIN, USER.UPDATED_AT)
                .values(id, admin, clock.millis())
                .execute()
        }

        // Get current permissions for user
        val existing = ctx
            .select(PERMISSION.RESOURCE_TYPE, PERMISSION.RESOURCE_NAME)
            .from(PERMISSION)
            .where(PERMISSION.USER_ID.eq(id))
            .fetch()
            .intoGroups(PERMISSION.RESOURCE_TYPE, PERMISSION.RESOURCE_NAME)
            .mapValues { (_, v) -> v.toSet() }

        val batch = mutableListOf<Query>()

        var bulkInsert = ctx.insertInto(
            PERMISSION,
            PERMISSION.USER_ID,
            PERMISSION.RESOURCE_TYPE,
            PERMISSION.RESOURCE_NAME
        )

        allResourcesByTypeForUser.forEach { (rt, resources) ->
            val existingOfType = existing.getOrDefault(rt, Collections.emptySet())

            val incomingOfType = resources.map { it.name }.toSet()

            incomingOfType.minus(existingOfType).forEach {
                bulkInsert.values(id, rt, it)
            }

            var toDelete = existingOfType.minus(incomingOfType)
            if (toDelete.isNotEmpty()) {
                batch += ctx.delete(PERMISSION)
                    .where(
                        PERMISSION.USER_ID.eq(id).and(
                            PERMISSION.RESOURCE_TYPE.eq(rt).and(
                                PERMISSION.RESOURCE_NAME.`in`(toDelete)
                            )
                        )
                    )
            }
        }

        // Special case if the user has lost access to all resources of a specific type
        val toDelete = resourceTypes.keys.minus(allResourcesByTypeForUser.keys)
        if (toDelete.isNotEmpty()) {
            batch += ctx.delete(PERMISSION)
                .where(
                    PERMISSION.USER_ID.eq(id).and(
                        PERMISSION.RESOURCE_TYPE.`in`(toDelete)
                    )
                )
        }

        if (bulkInsert.isExecutable) {
            batch += bulkInsert
        }

        if (batch.isNotEmpty()) {
            ctx.batch(batch).execute()
        }
    }

    private fun putAllResources(ctx: DSLContext, allResourcesByType: Map<ResourceType, Set<Resource>>) {
        // Get current resources
        val existing = ctx
            .select(RESOURCE.RESOURCE_TYPE, RESOURCE.RESOURCE_NAME)
            .from(RESOURCE)
            .fetch()
            .intoGroups(RESOURCE.RESOURCE_TYPE, RESOURCE.RESOURCE_NAME)
            .mapValues { (_, v) -> v.toSet() }

        val batch = mutableListOf<Query>()

        val bulkInsert = ctx.insertInto(
            RESOURCE,
            RESOURCE.RESOURCE_TYPE,
            RESOURCE.RESOURCE_NAME,
            RESOURCE.BODY
        )

        allResourcesByType.forEach { (rt, resources) ->
            val existingOfType = existing.getOrDefault(rt, Collections.emptySet())

            val incomingOfType = resources.associateBy { it.name }

            // Inserts
            incomingOfType.minus(existingOfType).forEach {
                val body = objectMapper.writeValueAsString(it.value)
                bulkInsert.values(rt, it.key, body)
            }

            // Updates
            incomingOfType.filterKeys { existingOfType.contains(it) }.forEach {
                val body = objectMapper.writeValueAsString(it.value)

                batch += ctx.update(RESOURCE)
                    .set(RESOURCE.BODY, body)
                    .where(
                        RESOURCE.RESOURCE_TYPE.eq(rt).and(
                            RESOURCE.RESOURCE_NAME.eq(it.key)
                        )
                    )
            }

            // Can't also do deletes here as there may still be permissions pointing to those resources.
            // They get cleaned up in `putAllbyId()`
        }

        if (bulkInsert.isExecutable) {
            batch += bulkInsert
        }

        if (batch.isNotEmpty()) {
            ctx.batch(batch).execute()
        }
    }

    private fun getFromDatabase(id: String): Optional<UserPermission> {
        val userPermission = UserPermission()
            .setId(id)

        if (id != UNRESTRICTED_USERNAME) {
            val result = withPool(poolName) {
                jooq.withRetry(sqlRetryProperties.reads) { ctx ->
                    ctx.select(USER.ADMIN)
                        .from(USER)
                        .where(USER.ID.eq(id))
                        .fetchOne()
                }
            }

            if (result == null) {
                log.debug("request for user {} not found in database", id)
                return Optional.empty()
            }

            userPermission.isAdmin = result.get(USER.ADMIN)
        }

        withPool(poolName) {
            jooq.withRetry(sqlRetryProperties.reads) { ctx ->
                resources.forEach { r ->
                    val userResources = ctx
                        .select(RESOURCE.BODY)
                        .from(RESOURCE)
                        .join(PERMISSION)
                        .on(
                            PERMISSION.RESOURCE_TYPE.eq(RESOURCE.RESOURCE_TYPE).and(
                                PERMISSION.RESOURCE_NAME.eq(RESOURCE.RESOURCE_NAME)
                            )
                        )
                        .join(USER)
                        .on(USER.ID.eq(PERMISSION.USER_ID))
                        .where(USER.ID.eq(id).and(
                            PERMISSION.RESOURCE_TYPE.eq(r.resourceType))
                        )
                        .fetch()
                        .map { record ->
                            objectMapper.readValue(record.get(RESOURCE.BODY), r.javaClass)
                        }
                    userPermission.addResources(userResources)
                }
            }
        }

        if (UNRESTRICTED_USERNAME != id) {
            userPermission.merge(getUnrestrictedUserPermission())
        }

        return Optional.of(userPermission)
    }

    private fun getUnrestrictedUserPermission(): UserPermission {
        var serverLastModified = withPool(poolName) {
            jooq.withRetry(sqlRetryProperties.reads) { ctx ->
                ctx.select(USER.UPDATED_AT)
                    .from(USER)
                    .where(USER.ID.eq(UNRESTRICTED_USERNAME))
                    .fetchOne(USER.UPDATED_AT)
            }
        }

        if (serverLastModified == null) {
            log.debug(
                "no last modified time available in database for user {} using default of {}",
                UNRESTRICTED_USERNAME,
                NO_UPDATED_AT
            )
            serverLastModified = NO_UPDATED_AT
        }

        return try {
            val userPermission = unrestrictedPermission[serverLastModified]
            if (userPermission != null && serverLastModified != NO_UPDATED_AT) {
                fallbackLastModified.set(serverLastModified)
            }
            userPermission!!
        } catch (ex: Throwable) {
            log.error(
                "failed reading user {} from cache for key {}", UNRESTRICTED_USERNAME, serverLastModified, ex
            )
            val fallback = fallbackLastModified.get()
            if (fallback != null) {
                val fallbackPermission = unrestrictedPermission.getIfPresent(fallback)
                if (fallbackPermission != null) {
                    log.warn(
                        "serving fallback permission for user {} from key {} as {}",
                        UNRESTRICTED_USERNAME,
                        fallback,
                        fallbackPermission
                    )
                    return fallbackPermission
                }
                log.warn("no fallback entry remaining in cache for key {}", fallback)
            }
            if (ex is RuntimeException) {
                throw ex
            }
            throw IntegrationException(ex)
        }
    }

    private fun reloadUnrestricted(cacheKey: Long): UserPermission {
        return getFromDatabase(UNRESTRICTED_USERNAME)
            .map { p ->
                log.debug("reloaded user {} for key {} as {}", UNRESTRICTED_USERNAME, cacheKey, p)
                p
            }
            .orElseThrow {
                log.error(
                    "loading user {} for key {} failed, no permissions returned",
                    UNRESTRICTED_USERNAME,
                    cacheKey
                )
                PermissionRepositoryException("Failed to read unrestricted user")
            }
    }
}

