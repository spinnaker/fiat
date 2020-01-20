/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.fiat.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import java.util.List;
import javax.annotation.Nonnull;

public class ChaosMonkeyResourcePermissionSource<T extends Resource.AccessControlled>
    implements ResourcePermissionSource<T> {

  private final List<String> roles;
  private final ObjectMapper objectMapper;

  public ChaosMonkeyResourcePermissionSource(List<String> roles, ObjectMapper objectMapper) {
    this.roles = roles;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public Permissions getPermissions(@Nonnull T resource) {
    Permissions.Builder builder = new Permissions.Builder();
    Permissions permissions = resource.getPermissions();

    if (permissions.isRestricted()) {
      if (resource instanceof Application) {
        Application application = (Application) resource;
        if (isChaosMonkeyEnabled(application)) {
          builder.add(Authorization.READ, roles).add(Authorization.WRITE, roles).build();
        }
      }
    }

    return builder.build();
  }

  protected boolean isChaosMonkeyEnabled(Application application) {
    Object config = application.getDetails().get("chaosMonkey");
    if (config == null) {
      return false;
    }
    return objectMapper.convertValue(config, ChaosMonkeyConfig.class).enabled;
  }

  private static class ChaosMonkeyConfig {
    public boolean enabled;
  }
}
