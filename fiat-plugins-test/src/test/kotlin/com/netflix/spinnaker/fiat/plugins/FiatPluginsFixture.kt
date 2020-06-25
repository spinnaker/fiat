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

import com.netflix.spinnaker.fiat.Main
import com.netflix.spinnaker.fiat.api.Resource
import com.netflix.spinnaker.fiat.providers.ResourceProviderRegistry
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverApi
import com.netflix.spinnaker.fiat.providers.internal.Front50Api
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.internal.PluginJar
import java.io.File
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import com.netflix.spinnaker.kork.plugins.tck.PluginsTckFixture
import org.springframework.boot.test.mock.mockito.MockBean

class FiatPluginsFixture : PluginsTckFixture, FiatTestService() {
  final override val plugins = File("build/plugins")

  final override val enabledPlugin: PluginJar
  final override val disabledPlugin: PluginJar
  final override val versionNotSupportedPlugin: PluginJar

  override val extensionClassNames: MutableList<String> = mutableListOf(
      ExtensionResource::class.java.name,
      ExtensionResourceLoader::class.java.name
  )

  final override fun buildPlugin(pluginId: String, systemVersionRequirement: String): PluginJar {
    return PluginJar.Builder(plugins.toPath().resolve("$pluginId.jar"), pluginId)
        .pluginClass(FiatPlugin::class.java.name)
        .pluginVersion("1.0.0")
        .manifestAttribute("Plugin-Requires", "fiat$systemVersionRequirement")
        .extensions(extensionClassNames)
        .build()
  }

  @Autowired
  override lateinit var spinnakerPluginManager: SpinnakerPluginManager

  @Autowired
  lateinit var resourceProviderRegistry: ResourceProviderRegistry

  @Autowired
  lateinit var resources: List<Resource>

  init {
    plugins.delete()
    plugins.mkdir()
    enabledPlugin = buildPlugin("io.armory.fiat.enabled.plugin", ">=1.0.0")
    disabledPlugin = buildPlugin("io.armory.fiat.disabled.plugin", ">=1.0.0")
    versionNotSupportedPlugin = buildPlugin("io.armory.fiat.version.not.supported.plugin", ">=1000.0.0")
  }
}

@SpringBootTest(classes = [Main::class])
@ContextConfiguration(classes = [PluginTestConfiguration::class])
@TestPropertySource(properties = ["spring.config.location=classpath:fiat-plugins-test.yml"])
abstract class FiatTestService

@TestConfiguration
internal class PluginTestConfiguration {
  @MockBean
  var rcd: RedisClientDelegate? = null

  @MockBean
  var front50: Front50Api? = null

  @MockBean
  var clouddriver: ClouddriverApi? = null
}
