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

package com.netflix.spinnaker.fiat.model

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.fiat.permissions.DualPermissionsRepository
import com.netflix.spinnaker.fiat.permissions.RedisPermissionsRepository
import com.netflix.spinnaker.fiat.permissions.SqlPermissionsRepository
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.*
import strikt.api.expectThat
import strikt.assertions.*
import java.util.*

class DualPermissionsRepositoryTest  : JUnit5Minutests {

    val primary: SqlPermissionsRepository = mockk(relaxUnitFun = true)
    val previous: RedisPermissionsRepository = mockk(relaxUnitFun = true)

    val subject: DualPermissionsRepository = DualPermissionsRepository(primary.javaClass.name, previous.javaClass.name, listOf(primary, previous), NoopRegistry())

    fun tests() = rootContext {

        before {
            clearMocks(primary, previous)
        }

        test("should favor 'primary'") {
            every {
                primary.get("user001")
            } returns Optional.of(UserPermission().setId("user001"))

            expectThat(
                subject.get("user001").get().id
            ).isEqualTo("user001")

            verifyAll {
                primary.get("user001")
                previous.get("user001") wasNot Called
            }
        }

        test("should fallback to 'previous' when 'primary' returns empty") {
            every {
                primary.get("user001")
            } returns Optional.empty()

            every {
                previous.get("user001")
            } returns Optional.of(UserPermission().setId("user001"))

            expectThat(
                subject.get("user001").get().id
            ).isEqualTo("user001")

            verifyAll {
                primary.get("user001")
                previous.get("user001")
            }
        }

        test("should only write to 'primary'") {
            val user001 = UserPermission().setId("user001")

            every {
                primary.put(user001)
            } returns primary

            subject.put(user001)

            verifyAll {
                primary.put(user001)
                previous.put(user001) wasNot Called
            }
        }

        test("should merge getAllById results from 'primary' and 'previous'") {
            val user001 = UserPermission().setId("user001")
            val user002 = UserPermission().setId("user002")
            val user003 = UserPermission().setId("user003")

            every {
                primary.getAllById()
            } returns mutableMapOf(
                "user001" to user001,
                "user002" to user002
            )

            every {
                previous.getAllById()
            } returns mutableMapOf(
                "user002" to user002,
                "user003" to user003
            )

            expectThat(
                subject.getAllById()
            ).isEqualTo(mutableMapOf(
                "user001" to user001,
                "user002" to user002,
                "user003" to user003,
            ))

            verifyAll {
                primary.getAllById()
                previous.getAllById()
            }
        }

        test("should merge getAllByRoles results from 'primary' and 'previous'") {
            val user001 = UserPermission().setId("user001")
            val user002 = UserPermission().setId("user002")
            val user003 = UserPermission().setId("user003")

            every {
                primary.getAllByRoles(mutableListOf("role1"))
            } returns mutableMapOf(
                "user001" to user001,
                "user002" to user002
            )

            every {
                previous.getAllByRoles(mutableListOf("role1"))
            } returns mutableMapOf(
                "user002" to user002,
                "user003" to user003
            )

            expectThat(
                subject.getAllByRoles(mutableListOf("role1"))
            ).isEqualTo(mutableMapOf(
                "user001" to user001,
                "user002" to user002,
                "user003" to user003,
            ))

            verifyAll {
                primary.getAllByRoles(listOf("role1"))
                previous.getAllByRoles(listOf("role1"))
            }
        }

        test("should remove from 'primary' and 'previous'") {
            justRun {
                primary.remove("user001")
            }

            justRun {
                previous.remove("user001")
            }

            subject.remove("user001")

            verifyAll {
                primary.remove("user001")
                previous.remove("user001")
            }
        }
    }

}
