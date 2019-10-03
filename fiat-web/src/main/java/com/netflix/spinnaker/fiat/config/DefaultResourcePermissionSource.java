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

import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.providers.ClouddriverAccountResourcePermissionProvider;
import com.netflix.spinnaker.fiat.providers.Front50ApplicationResourcePermissionProvider;
import com.netflix.spinnaker.fiat.providers.ResourcePermissionProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    value = "auth.permissions-source",
    havingValue = "default",
    matchIfMissing = true)
class DefaultResourcePermissionSource {

  @Bean
  public ResourcePermissionProvider<Account> getAccountPermissionProvider() {
    return new ClouddriverAccountResourcePermissionProvider();
  }

  @Bean
  public ResourcePermissionProvider<Application> getApplicationPermissionProvider(
      FiatServerConfigurationProperties properties) {
    return new Front50ApplicationResourcePermissionProvider(properties.getExecuteFallback());
  }
}
