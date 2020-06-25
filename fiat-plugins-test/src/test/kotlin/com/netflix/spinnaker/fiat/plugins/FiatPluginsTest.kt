/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.fiat.plugins

import com.netflix.spinnaker.fiat.api.ResourceLoader
import com.netflix.spinnaker.fiat.api.ResourceType
import com.netflix.spinnaker.fiat.providers.ResourceProviderRegistry.ExtensionResourceProvider
import com.netflix.spinnaker.kork.plugins.tck.PluginsTck
import com.netflix.spinnaker.kork.plugins.tck.serviceFixture
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.*

class FiatPluginsTest : PluginsTck<FiatPluginsFixture>() {
  fun tests() = rootContext<FiatPluginsFixture> {

    context("a fiat integration test environment and a fiat plugin") {
      serviceFixture {
        FiatPluginsFixture()
      }

      defaultPluginTests()

      test("extension resource loader is loaded into the resource provider registry") {
        expectThat(resourceProviderRegistry.all.find { it is ExtensionResourceProvider })
          .isA<ExtensionResourceProvider>()
          .and {
            get { loadAll().first().resourceType }.isEqualTo(ResourceType("extension_resource"))
          }
      }

      test("extension resource type is loaded") {
        expectThat(resources.find { it is ExtensionResource })
          .isNotNull()
          .and {
            get { resourceType }.isEqualTo(ResourceType("extension_resource"))
          }

      }
    }
  }
}