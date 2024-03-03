/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.fiat.providers.ProviderHealthTracker;
import com.netflix.spinnaker.fiat.providers.internal.*;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;

@Configuration
@EnableConfigurationProperties(ProviderCacheConfig.class)
@PropertySource("classpath:resilience4j-defaults.properties")
@RequiredArgsConstructor
public class ResourcesConfig {
  private final ServiceClientProvider provider;

  @Bean
  Front50Api front50Api(@Value("${services.front50.base-url}") String front50Endpoint) {
    return provider.getService(
        Front50Api.class, new DefaultServiceEndpoint("front50", front50Endpoint));
  }

  @Bean
  Front50ApplicationLoader front50ApplicationLoader(
      ProviderHealthTracker tracker, Front50Api front50Api) {
    return new Front50ApplicationLoader(tracker, front50Api);
  }

  @Bean
  Front50ServiceAccountLoader front50ServiceAccountLoader(
      ProviderHealthTracker tracker, Front50Api front50Api) {
    return new Front50ServiceAccountLoader(tracker, front50Api);
  }

  @Bean
  Front50Service front50Service(
      Front50ApplicationLoader front50ApplicationLoader,
      Front50ServiceAccountLoader front50ServiceAccountLoader) {
    return new Front50Service(front50ApplicationLoader, front50ServiceAccountLoader);
  }

  @Bean
  ClouddriverApi clouddriverApi(
      @Value("${services.clouddriver.base-url}") String clouddriverEndpoint) {
    return provider.getService(
        ClouddriverApi.class, new DefaultServiceEndpoint("clouddriver", clouddriverEndpoint));
  }

  @Bean
  ClouddriverAccountLoader clouddriverAccountLoader(
      ProviderHealthTracker providerHealthTracker, ClouddriverApi clouddriverApi) {
    return new ClouddriverAccountLoader(providerHealthTracker, clouddriverApi);
  }

  @Bean
  @ConditionalOnProperty(
      name = "resource.provider.application.clouddriver.load-applications",
      havingValue = "true",
      matchIfMissing = true)
  ClouddriverApplicationLoader clouddriverApplicationLoader(
      ProviderHealthTracker providerHealthTracker,
      ClouddriverApi clouddriverApi,
      ResourceProviderConfig resourceProviderConfig) {
    return new ClouddriverApplicationLoader(
        providerHealthTracker, clouddriverApi, resourceProviderConfig.getApplication());
  }

  @Bean
  @ConditionalOnProperty(
      name = "resource.provider.application.clouddriver.load-applications",
      havingValue = "true",
      matchIfMissing = true)
  @Primary
  ClouddriverService clouddriverService(
      ClouddriverApplicationLoader clouddriverApplicationLoader,
      ClouddriverAccountLoader clouddriverAccountLoader) {
    return new ClouddriverService(clouddriverApplicationLoader, clouddriverAccountLoader);
  }

  @Bean
  @ConditionalOnProperty(
      name = "resource.provider.application.clouddriver.load-applications",
      havingValue = "false")
  ClouddriverService clouddriverServiceWithoutApplicationLoader(
      ClouddriverAccountLoader clouddriverAccountLoader) {
    return new ClouddriverService(clouddriverAccountLoader);
  }

  @Bean
  @ConditionalOnProperty("services.igor.enabled")
  IgorApi igorApi(@Value("${services.igor.base-url}") String igorEndpoint) {
    return provider.getService(IgorApi.class, new DefaultServiceEndpoint("igor", igorEndpoint));
  }

  @Bean
  @ConditionalOnProperty("services.igor.enabled")
  IgorBuildServiceLoader igorBuildServiceLoader(
      ProviderHealthTracker providerHealthTracker, IgorApi igorApi) {
    return new IgorBuildServiceLoader(providerHealthTracker, igorApi);
  }

  @Bean
  @ConditionalOnProperty("services.igor.enabled")
  IgorService igorService(IgorBuildServiceLoader igorBuildServiceLoader) {
    return new IgorService(igorBuildServiceLoader);
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  ProviderHealthTracker providerHealthTracker(ProviderCacheConfig config) {
    return new ProviderHealthTracker(config.getMaximumStalenessTimeMs());
  }
}
