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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.fiat.model.UserPermission
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.lang.IllegalStateException
import java.util.*

@Primary
@Component
@ConditionalOnProperty("permissions-repository.dual.enabled")
class DualPermissionsRepository(
    @Value("\${permissions-repository.dual.primary-class:}") private val primaryClass: String,
    @Value("\${permissions-repository.dual.previous-class:}") private val previousClass: String,
    allRepositories: List<PermissionsRepository>,
    private val registry: Registry
) : PermissionsRepository {

    private val log = LoggerFactory.getLogger(DualPermissionsRepository::class.java)

    lateinit var primary: PermissionsRepository
    lateinit var previous: PermissionsRepository

    private final var fallbackGet = registry.createId("permissionsRepository.previous.get")

    init {
        allRepositories.forEach {
            log.info("Available PermissionsRepository: $it")
        }

        primary = allRepositories.first { it.javaClass.name == primaryClass }
        previous = allRepositories.first { it.javaClass.name == previousClass }
    }

    override fun put(permission: UserPermission?): PermissionsRepository {
        primary.put(permission)
        return this
    }

    override fun get(id: String?): Optional<UserPermission> {
        val userPermission = primary.get(id)
        if (userPermission.isPresent) {
            return userPermission
        }
        registry.counter(fallbackGet).increment()
        return previous.get(id)
    }

    override fun getAllById(): MutableMap<String, UserPermission> {
        val permissions = mutableMapOf<String, UserPermission>()

        permissions.putAll(primary.getAllById())
        permissions.putAll(previous.getAllById())

        return permissions
    }

    override fun getAllByRoles(anyRoles: MutableList<String>?): MutableMap<String, UserPermission> {
        val permissions = mutableMapOf<String, UserPermission>()

        permissions.putAll(primary.getAllByRoles(anyRoles))
        permissions.putAll(previous.getAllByRoles(anyRoles))

        return permissions
    }

    override fun remove(id: String?) {
        primary.remove(id)
        previous.remove(id)
    }
}