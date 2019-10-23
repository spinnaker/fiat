/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.fiat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.BuildService;
import com.netflix.spinnaker.fiat.providers.*;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DefaultResourcePermissionConfig {

  @Autowired ObjectMapper objectMapper;

  @Bean
  @ConditionalOnProperty(
      value = "auth.permissions.source.account.resource.enabled",
      matchIfMissing = true)
  ResourcePermissionSource<Account> accountResourcePermissionSource() {
    return new AccessControlledResourcePermissionSource<>();
  }

  @Bean
  @ConditionalOnProperty(
      value = "auth.permissions.provider.account",
      havingValue = "default",
      matchIfMissing = true)
  public ResourcePermissionProvider<Account> defaultAccountPermissionProvider(
      ResourcePermissionSource<Account> accountResourcePermissionSource) {
    return new DefaultResourcePermissionProvider<>(accountResourcePermissionSource);
  }

  @Bean
  @ConditionalOnProperty(value = "auth.permissions.provider.account", havingValue = "aggregate")
  public ResourcePermissionProvider<Account> aggregateAccountPermissionProvider(
      List<ResourcePermissionSource<Account>> sources) {
    return new AggregatingResourcePermissionProvider<>(sources);
  }

  @Bean
  @ConditionalOnProperty(
      value = "auth.permissions.source.application.front50.enabled",
      matchIfMissing = true)
  ResourcePermissionSource<Application> front50ResourcePermissionSource(
      FiatServerConfigurationProperties fiatServerConfigurationProperties) {
    return new Front50ApplicationResourcePermissionSource(
        fiatServerConfigurationProperties.getExecuteFallback());
  }

  @Bean
  @ConditionalOnExpression(
      "T(org.springframework.utils.CollectionUtils).isNotEmpty(${auth.permissions.source.application.prefix})")
  List<ResourcePermissionSource<Application>> applicationPrefixPermissionSource(
      @Value("${auth.permissions.source.application.prefix}") List<Object> prefixes) {
    return Arrays.asList(
        objectMapper.convertValue(prefixes, ApplicationPrefixPermissionSource[].class));
  }

  @Bean
  @ConditionalOnProperty(
      value = "auth.permissions.provider.application",
      havingValue = "default",
      matchIfMissing = true)
  public ResourcePermissionProvider<Application> defaultApplicationPermissionProvider(
      ResourcePermissionSource<Application> front50ResourcePermissionSource) {
    return new DefaultResourcePermissionProvider<>(front50ResourcePermissionSource);
  }

  @Bean
  @ConditionalOnProperty(value = "auth.permissions.provider.application", havingValue = "aggregate")
  public ResourcePermissionProvider<Application> aggregateApplicationPermissionProvider(
      List<ResourcePermissionSource<Application>> sources) {
    return new AggregatingResourcePermissionProvider<>(sources);
  }

  @Bean
  @ConditionalOnProperty(
      value = "auth.permissions.source.build-service.resource.enabled",
      matchIfMissing = true)
  ResourcePermissionSource<BuildService> buildServiceResourcePermissionSource() {
    return new AccessControlledResourcePermissionSource<>();
  }

  @Bean
  @ConditionalOnProperty(
      value = "auth.permissions.provider.build-service",
      havingValue = "default",
      matchIfMissing = true)
  public ResourcePermissionProvider<BuildService> defaultBuildServicePermissionProvider(
      ResourcePermissionSource<BuildService> buildServiceResourcePermissionSource) {
    return new DefaultResourcePermissionProvider<>(buildServiceResourcePermissionSource);
  }

  @Bean
  @ConditionalOnProperty(
      value = "auth.permissions.provider.build-service",
      havingValue = "aggregate")
  public ResourcePermissionProvider<BuildService> aggregateBuildServicePermissionProvider(
      List<ResourcePermissionSource<BuildService>> sources) {
    return new AggregatingResourcePermissionProvider<>(sources);
  }
}
