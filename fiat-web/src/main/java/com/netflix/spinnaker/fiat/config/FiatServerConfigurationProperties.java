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

import com.netflix.spinnaker.fiat.model.Authorization;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties("fiat")
public class FiatServerConfigurationProperties {

  /** True if the /authorize endpoint should be available to dump all users in the repo. */
  private boolean getAllEnabled = false;

  private boolean defaultToUnrestrictedUser = false;

  /**
   * If a request for user permissions comes through `/authorize` and fails to find stored
   * permissions, enable this flag to fallback to looking up user permissions from the underlying
   * {@link com.netflix.spinnaker.fiat.permissions.PermissionsResolver} implementation.
   */
  private boolean allowPermissionResolverFallback = false;

  private boolean allowAccessToUnknownApplications = false;

  private Authorization executeFallback = Authorization.READ;

  private boolean restrictApplicationCreation = false;

  private WriteMode writeMode = new WriteMode();

  private int maxExpandedServiceAccounts = 25;

  @NestedConfigurationProperty
  private ChaosMonkeyConfigurationProperties chaosMonkey = new ChaosMonkeyConfigurationProperties();

  @Data
  static class ChaosMonkeyConfigurationProperties {
    private List<String> roles = new ArrayList<>();
  }

  @Data
  static class WriteMode {
    /** True if the /roles endpoint should be enabled. Also turns on the UserRoleSyncer. */
    private boolean enabled = true;

    /** How much of a delay between the end of one sync and the beginning of the next. */
    private int syncDelayMs = 600000;

    /** How much time to between retries of dependent resource providers if they are down. */
    private int retryIntervalMs = 10000;
  }
}
