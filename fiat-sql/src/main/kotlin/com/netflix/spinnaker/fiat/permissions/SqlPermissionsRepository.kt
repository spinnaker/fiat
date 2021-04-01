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

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig.UNRESTRICTED_USERNAME
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Resource
import com.netflix.spinnaker.fiat.model.resources.ResourceType
import com.netflix.spinnaker.fiat.permissions.sql.SqlUtil
import com.netflix.spinnaker.fiat.permissions.sql.tables.references.PERMISSION
import com.netflix.spinnaker.fiat.permissions.sql.tables.references.RESOURCE
import com.netflix.spinnaker.fiat.permissions.sql.tables.references.USER
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.vavr.control.Try
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.jooq.*
import org.jooq.exception.SQLDialectNotSupportedException
import org.jooq.impl.DSL.field
import org.jooq.impl.SQLDataType
import org.jooq.util.mysql.MySQLDSL
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

class SqlPermissionsRepository(
    private val clock: Clock,
    private val objectMapper: ObjectMapper,
    private val jooq: DSLContext,
    private val sqlRetryProperties: SqlRetryProperties,
    resources: List<Resource>,
    private val dispatcher: CoroutineContext?
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
        putResources(permission.allResources)
        putUserPermission(permission)
        return this
    }

    override fun putAllById(permissions: Map<String, UserPermission>?) {
        if (permissions == null || permissions.isEmpty()) {
            return
        }

        val allResources = permissions.values.map { it.allResources }.flatten().toSet()

        putResources(allResources)
        permissions.values.forEach {
            putUserPermission(it)
        }
    }

    override fun get(id: String): Optional<UserPermission> {
        if (UNRESTRICTED_USERNAME == id) {
            return Optional.of(getUnrestrictedUserPermission())
        }
        return getFromDatabase(id)
    }

    override fun getAllById(): Map<String, UserPermission> {
        val unrestrictedUser = getUnrestrictedUserPermission()

        val resourceRecords = withRetry(RetryCategory.READ) {
            jooq
                .select(RESOURCE.RESOURCE_TYPE, RESOURCE.RESOURCE_NAME, RESOURCE.BODY)
                .from(RESOURCE)
                .fetch()
        }

        val existingResources = parseResourceRecords(resourceRecords)

        val userRecords = withRetry(RetryCategory.READ) {
            jooq.select(USER.ID, USER.ADMIN, PERMISSION.RESOURCE_TYPE, PERMISSION.RESOURCE_NAME)
                .from(USER)
                .leftJoin(PERMISSION)
                .on(USER.ID.eq(PERMISSION.USER_ID))
                .fetch()
        }

        return parseAndCombineUserRecords(userRecords, unrestrictedUser, existingResources)
    }

    override fun getAllByRoles(anyRoles: List<String>?): Map<String, UserPermission> {
        if (anyRoles == null) {
            return getAllById()
        } else if (anyRoles.isEmpty()) {
            val unrestricted = getFromDatabase(UNRESTRICTED_USERNAME)
            if (unrestricted.isPresent) {
                return mapOf(UNRESTRICTED_USERNAME to unrestricted.get())
            }
            return mapOf()
        }

        val unrestrictedUser = getUnrestrictedUserPermission()

        val resourceRecords = withRetry(RetryCategory.READ) {
            jooq
                .select(RESOURCE.RESOURCE_TYPE, RESOURCE.RESOURCE_NAME, RESOURCE.BODY)
                .from(RESOURCE)
                .leftSemiJoin(PERMISSION)
                .on(
                    RESOURCE.RESOURCE_TYPE
                        .eq(PERMISSION.RESOURCE_TYPE)
                        .and(RESOURCE.RESOURCE_NAME.eq(PERMISSION.RESOURCE_NAME))
                        .and(
                            PERMISSION.USER_ID.`in`(
                                jooq.select(USER.ID)
                                    .from(USER)
                                    .leftSemiJoin(PERMISSION)
                                    .on(
                                        PERMISSION.USER_ID
                                            .eq(USER.ID)
                                            .and(PERMISSION.RESOURCE_TYPE.eq(ResourceType.ROLE))
                                            .and(PERMISSION.RESOURCE_NAME.`in`(anyRoles))
                                    )
                            )
                        )
                )
                .fetch()
        }

        val existingResources = parseResourceRecords(resourceRecords)

        val userRecords = withRetry(RetryCategory.READ) {
            jooq.select(USER.ID, USER.ADMIN, PERMISSION.RESOURCE_TYPE, PERMISSION.RESOURCE_NAME)
                .from(USER)
                .leftJoin(PERMISSION)
                .on(USER.ID.eq(PERMISSION.USER_ID))
                .where(
                    PERMISSION.USER_ID.`in`(
                        jooq.select(USER.ID)
                            .from(USER)
                            .leftSemiJoin(PERMISSION)
                            .on(
                                PERMISSION.USER_ID
                                    .eq(USER.ID)
                                    .and(PERMISSION.RESOURCE_TYPE.eq(ResourceType.ROLE))
                                    .and(PERMISSION.RESOURCE_NAME.`in`(anyRoles))
                            )
                    )
                )
                .fetch()
        }

        val userPermissions = parseAndCombineUserRecords(userRecords, unrestrictedUser, existingResources)

        userPermissions[UNRESTRICTED_USERNAME] = unrestrictedUser

        return userPermissions
    }

    override fun remove(id: String) {
        withRetry(RetryCategory.WRITE) {
            // Delete permissions
            jooq.delete(PERMISSION)
                .where(PERMISSION.USER_ID.eq(id))
                .execute()

            // Delete user
            jooq.delete(USER)
                .where(USER.ID.eq(id))
                .execute()
        }
    }

    private fun parseResourceRecords(records: Result<Record3<ResourceType, String, String>>): Map<ResourceType, MutableMap<String, Resource>> {
        val resources = mutableMapOf<ResourceType, MutableMap<String, Resource>>()

        if (dispatcher != null) {
            runBlocking {
                records
                    .asFlow()
                    .map { record -> parseResourceRecord(record) }
                    .flowOn(dispatcher)
                    .collect {
                        var resourcesForType = resources.getOrPut(it.first) { mutableMapOf() }
                        resourcesForType[it.second.first] = it.second.second
                    }
            }
        } else {
            records
                .map { record -> parseResourceRecord(record) }
                .forEach {
                    var resourcesForType = resources.getOrPut(it.first) { mutableMapOf() }
                    resourcesForType[it.second.first] = it.second.second
                }
        }

        return resources
    }

    private fun parseResourceRecord(record: Record3<ResourceType, String, String>): Pair<ResourceType, Pair<String, Resource>> {
        val rt = record.get(RESOURCE.RESOURCE_TYPE)
        val name = record.get(RESOURCE.RESOURCE_NAME)
        val body = record.get(RESOURCE.BODY)
        val value = objectMapper.readValue(body, resourceTypes[rt]!!.javaClass)

        return rt to (name to value)
    }

    private fun parseAndCombineUserRecords(
        records: Result<Record4<String, Boolean, ResourceType, String>>,
        unrestrictedUser: UserPermission,
        existingResources: Map<ResourceType, MutableMap<String, Resource>>
    ): MutableMap<String, UserPermission> {
        val users = mutableMapOf<String, UserPermission>()

        if (dispatcher != null) {
            runBlocking {
                records.intoGroups(USER.ID)
                    .asSequence()
                    .asFlow()
                    .map { record -> parseUserRecord(record.value, unrestrictedUser, existingResources) }
                    .flowOn(dispatcher)
                    .collect {
                        users[it.id] = it
                    }
            }
        } else {
            records.intoGroups(USER.ID)
                .asSequence()
                .map { record -> parseUserRecord(record.value, unrestrictedUser, existingResources) }
                .forEach {
                    users[it.id] = it
                }
        }

        return users
    }

    private fun parseUserRecord(
        record: Result<Record4<String, Boolean, ResourceType, String>>,
        unrestrictedUser: UserPermission,
        existingResources: Map<ResourceType, MutableMap<String, Resource>>
    ): UserPermission {
        val id = record.first().get(USER.ID)

        val userPermission = UserPermission()
            .setAdmin(record.first().get(USER.ADMIN))
            .setId(id)

        record.forEach {
            val type = it.get(PERMISSION.RESOURCE_TYPE)
            val name = it.get(PERMISSION.RESOURCE_NAME)
            val resourcesByType = existingResources.getOrDefault(type, emptyMap())
            val resource = resourcesByType[name]
            if (resource != null) {
                userPermission.addResource(resource)
            }
        }

        if (UNRESTRICTED_USERNAME != id) {
            userPermission.merge(unrestrictedUser)
        }

        return userPermission
    }

    private fun putUserPermission(permission: UserPermission) {
        val insert = jooq.insertInto(USER, USER.ID, USER.ADMIN, USER.UPDATED_AT)

        insert.apply {
            values(permission.id, permission.isAdmin, clock.millis())
            // https://github.com/jOOQ/jOOQ/issues/5975 means we have to duplicate field names here
            when (jooq.dialect()) {
                SQLDialect.POSTGRES ->
                    onConflict(USER.ID)
                        .doUpdate()
                        .set(USER.ADMIN, SqlUtil.excluded(field("admin", SQLDataType.BOOLEAN)))
                        .set(USER.UPDATED_AT, SqlUtil.excluded(field("updated_at", SQLDataType.BIGINT)))
                else ->
                    onDuplicateKeyUpdate()
                        .set(USER.ADMIN, MySQLDSL.values(field("admin", SQLDataType.BOOLEAN)))
                        .set(USER.UPDATED_AT, MySQLDSL.values(field("updated_at", SQLDataType.BIGINT)))
            }
        }

        withRetry(RetryCategory.WRITE) {
            insert.execute()
        }

        putUserPermissions(permission.id, permission.allResources)
    }

    private fun putUserPermissions(id: String, resources: Set<Resource>) {
        val existingPermissions = getUserPermissions(id)

        val currentPermissions = mutableSetOf<ResourceId>() // current permissions from request
        val toStore = mutableListOf<ResourceId>() // ids that are new or changed

        resources.forEach() {
            val resourceId = ResourceId(it.resourceType, it.name)
            currentPermissions.add(resourceId)

            if (!existingPermissions.contains(resourceId)) {
                toStore.add(resourceId)
            }
        }

        val insert = jooq.insertInto(
            PERMISSION,
            PERMISSION.USER_ID,
            PERMISSION.RESOURCE_TYPE,
            PERMISSION.RESOURCE_NAME
        )

        insert.apply {
            toStore.forEach { resource ->
                values(id, resource.type, resource.name)
                when (jooq.dialect()) {
                    SQLDialect.POSTGRES ->
                        onConflictDoNothing()
                    else ->
                        onDuplicateKeyIgnore()
                }
            }
        }

        if (insert.isExecutable) {
            withRetry(RetryCategory.WRITE) {
                insert.execute()
            }
        }

        val toDelete = existingPermissions
            .asSequence()
            .filter { !currentPermissions.contains(it) }
            .toSet()

        try {
            toDelete.groupBy { it.type }
                .forEach { (type, ids) ->
                    val names = ids.map { it.name }.sorted()
                    withRetry(RetryCategory.WRITE) {
                        jooq.deleteFrom(PERMISSION)
                            .where(
                                PERMISSION.USER_ID.eq(id).and(
                                    PERMISSION.RESOURCE_TYPE.eq(type).and(
                                        PERMISSION.RESOURCE_NAME.`in`(names)
                                    )
                                )
                            )
                            .execute()
                    }
                }
        } catch (e: Exception) {
            log.error("error deleting old permissions", e)
        }
    }

    private fun getUserPermissions(id: String) =
        withRetry(RetryCategory.READ) {
            jooq
                .select(PERMISSION.RESOURCE_TYPE, PERMISSION.RESOURCE_NAME)
                .from(PERMISSION)
                .where(PERMISSION.USER_ID.eq(id))
                .fetch()
                .map { ResourceId(it.get(PERMISSION.RESOURCE_TYPE), it.get(PERMISSION.RESOURCE_NAME)) }
                .toSet()
        }

    private fun putResources(resources: Set<Resource>, cleanup: Boolean = false) {
        val existingHashIds = getResourceHashes()

        val existingHashes = existingHashIds.values.toSet()
        val existingIds = existingHashIds.keys

        val currentIds = mutableSetOf<ResourceId>() // current resources from the request
        val toStore = mutableListOf<ResourceId>() // ids that are new or changed
        val bodies = mutableMapOf<ResourceId, String>() // id to body
        val hashes = mutableMapOf<ResourceId, String>() // id to sha256(body)

        resources.forEach() {
            val id = ResourceId(it.resourceType, it.name)
            currentIds.add(id)

            val body: String? = objectMapper.writeValueAsString(it)
            val bodyHash = getHash(body)

            if (body != null && bodyHash != null && !existingHashes.contains(bodyHash)) {
                toStore.add(id)
                bodies[id] = body
                hashes[id] = bodyHash
            }
        }

        val now = clock.millis()

        val insert = jooq.insertInto(
            RESOURCE,
            RESOURCE.RESOURCE_TYPE,
            RESOURCE.RESOURCE_NAME,
            RESOURCE.BODY,
            RESOURCE.BODY_HASH,
            RESOURCE.UPDATED_AT
        )

        insert.apply {
            toStore.forEach {
                values(it.type, it.name, bodies[it], hashes[it], now)
                when (jooq.dialect()) {
                    // https://github.com/jOOQ/jOOQ/issues/5975 means we have to duplicate field names here
                    SQLDialect.POSTGRES ->
                        onConflict(RESOURCE.RESOURCE_TYPE, RESOURCE.RESOURCE_NAME)
                            .doUpdate()
                            .set(RESOURCE.BODY, SqlUtil.excluded(field("body", SQLDataType.LONGVARCHAR)))
                            .set(RESOURCE.BODY_HASH, SqlUtil.excluded(field("body_hash", SQLDataType.VARCHAR)))
                            .set(RESOURCE.UPDATED_AT, SqlUtil.excluded(field("updated_at", SQLDataType.BIGINT)))
                    else ->
                        onDuplicateKeyUpdate()
                            .set(RESOURCE.BODY, MySQLDSL.values(field("body", SQLDataType.LONGVARCHAR)))
                            .set(RESOURCE.BODY_HASH, MySQLDSL.values(field("body_hash", SQLDataType.VARCHAR)))
                            .set(RESOURCE.UPDATED_AT, MySQLDSL.values(field("updated_at", SQLDataType.BIGINT)))
                }
            }
        }

        if (insert.isExecutable) {
            withRetry(RetryCategory.WRITE) {
                insert.execute()
            }
        }

        if (cleanup) {
            val toDelete = existingIds
                .asSequence()
                .filter { !currentIds.contains(it) }
                .toSet()

            deleteResources(toDelete)
        }
    }

    private fun getResourceHashes() =
        withRetry(RetryCategory.READ) {
            jooq
                .select(RESOURCE.RESOURCE_TYPE, RESOURCE.RESOURCE_NAME, RESOURCE.BODY_HASH)
                .from(RESOURCE)
                .fetch()
                .map { ResourceId(it.get(RESOURCE.RESOURCE_TYPE), it.get(RESOURCE.RESOURCE_NAME)) to it.get(RESOURCE.BODY_HASH) }
                .toMap()
        }

    data class ResourceId(
        val type: ResourceType,
        val name: String
    )

    private fun deleteResources(ids: Collection<ResourceId>) {
        try {
            ids.groupBy { it.type }
                .forEach { (type, names) ->
                    withRetry(RetryCategory.WRITE) {
                        jooq.deleteFrom(RESOURCE)
                            .where(
                                RESOURCE.RESOURCE_TYPE.eq(type).and(
                                    RESOURCE.RESOURCE_NAME.`in`(names)
                                )
                            )
                            .execute()
                    }
                }
        } catch (e: Exception) {
            log.error("error deleting old resources", e)
        }
    }

    private fun getFromDatabase(id: String): Optional<UserPermission> {
        val userPermission = UserPermission()
            .setId(id)

        if (id != UNRESTRICTED_USERNAME) {
            val result = withRetry(RetryCategory.READ) {
                jooq.select(USER.ADMIN)
                    .from(USER)
                    .where(USER.ID.eq(id))
                    .fetchOne()
            }

            if (result == null) {
                log.debug("request for user {} not found in database", id)
                return Optional.empty()
            }

            userPermission.isAdmin = result.get(USER.ADMIN)
        }

        val resourceRecords = withRetry(RetryCategory.READ) {
            jooq
                .select(RESOURCE.RESOURCE_TYPE, RESOURCE.RESOURCE_NAME, RESOURCE.BODY)
                .from(RESOURCE)
                .leftSemiJoin(PERMISSION)
                .on(
                    PERMISSION.USER_ID.eq(id).and(
                        PERMISSION.RESOURCE_TYPE.eq(RESOURCE.RESOURCE_TYPE).and(
                            PERMISSION.RESOURCE_NAME.eq(RESOURCE.RESOURCE_NAME)
                        )
                    )
                )
                .fetch()
        }

        val existingResources = parseResourceRecords(resourceRecords)

        existingResources.values.forEach {
            userPermission.addResources(it.values)
        }

        if (UNRESTRICTED_USERNAME != id) {
            userPermission.merge(getUnrestrictedUserPermission())
        }

        return Optional.of(userPermission)
    }

    private fun getUnrestrictedUserPermission(): UserPermission {
        var serverLastModified = withRetry(RetryCategory.READ) {
            jooq.select(USER.UPDATED_AT)
                .from(USER)
                .where(USER.ID.eq(UNRESTRICTED_USERNAME))
                .fetchOne(USER.UPDATED_AT)
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

    // Lifted from SqlCache.kt in clouddriver

    private fun getHash(body: String?): String? {
        if (body.isNullOrBlank()) {
            return null
        }
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(body.toByteArray())
            digest.fold("") { str, it ->
                str + "%02x".format(it)
            }
        } catch (e: Exception) {
            log.error("error calculating hash for body: $body", e)
            null
        }
    }

    // TODO: Does this belong in kork-sql?
    private enum class RetryCategory {
        WRITE, READ
    }

    private fun <T> withRetry(category: RetryCategory, action: () -> T): T {
        return if (category == RetryCategory.WRITE) {
            val retry = Retry.of(
                "sqlWrite",
                RetryConfig.custom<T>()
                    .maxAttempts(sqlRetryProperties.transactions.maxRetries)
                    .waitDuration(Duration.ofMillis(sqlRetryProperties.transactions.backoffMs))
                    .ignoreExceptions(SQLDialectNotSupportedException::class.java)
                    .build()
            )

            Try.ofSupplier(Retry.decorateSupplier(retry, action)).get()
        } else {
            val retry = Retry.of(
                "sqlRead",
                RetryConfig.custom<T>()
                    .maxAttempts(sqlRetryProperties.reads.maxRetries)
                    .waitDuration(Duration.ofMillis(sqlRetryProperties.reads.backoffMs))
                    .ignoreExceptions(SQLDialectNotSupportedException::class.java)
                    .build()
            )

            Try.ofSupplier(Retry.decorateSupplier(retry, action)).get()
        }
    }
}
