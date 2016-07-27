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

package com.netflix.spinnaker.fiat.permissions;

import com.netflix.spinnaker.fiat.config.AnonymousUserConfig;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.providers.AccountProvider;
import com.netflix.spinnaker.fiat.providers.ApplicationProvider;
import com.netflix.spinnaker.fiat.providers.ProviderException;
import com.netflix.spinnaker.fiat.providers.ServiceAccountProvider;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@NoArgsConstructor
@Slf4j
public class DefaultPermissionsResolver implements PermissionsResolver {

  @Autowired
  @Setter
  private UserRolesProvider userRolesProvider;

  @Autowired
  @Setter
  private AccountProvider accountProvider;

  @Autowired
  @Setter
  private ApplicationProvider applicationProvider;

  @Autowired
  @Setter
  private ServiceAccountProvider serviceAccountProvider;

  @Value("${auth.anonymous.enabled}")
  @Setter
  private boolean anonymousEnabled;

  @Override
  public Optional<UserPermission> resolveAnonymous() {
    if (!anonymousEnabled) {
      return Optional.empty();
    }
    return Optional.of(getUserPermission(AnonymousUserConfig.ANONYMOUS_USERNAME,
                                         new ArrayList<>(0) /* groups */));
  }

  @Override
  public Optional<UserPermission> resolve(@NonNull String userId) {
    return resolveAndMerge(userId, Collections.emptyList());
  }

  @Override
  public Optional<UserPermission> resolveAndMerge(@NonNull String userId, Collection<String> externalRoles) {
    List<String> roles;
    try {
      roles = userRolesProvider.loadRoles(userId);
    } catch (ProviderException e) {
      // Roles are cornerstone to UserPermission resolution, so don't continue if we can't get
      // roles. This is different than a partial UserPermission, where we can't access other
      // Spinnaker components.
      log.warn("Failed to resolve user permission for user " + userId, e);
      return Optional.empty();
    }
    val combo = Stream.concat(roles.stream(), externalRoles.stream())
                      .map(String::toLowerCase)
                      .collect(Collectors.toSet());

    return Optional.of(getUserPermission(userId, combo));
  }

  @Override
  public Map<String, UserPermission> resolve(@NonNull Collection<String> userIds) {
    val roles = userRolesProvider.multiLoadRoles(userIds);
    return roles.entrySet()
                .stream()
                .map(entry -> getUserPermission(entry.getKey(), entry.getValue()))
                .collect(Collectors.toMap(UserPermission::getId, Function.identity()));
  }

  private UserPermission getUserPermission(String userId, Collection<String> groups) {
    UserPermission permission = new UserPermission().setId(userId);

    try {
      permission.setAccounts(accountProvider.getAccounts(groups));
    } catch (ProviderException pe) {
      permission.setPartialPermission(true);
    }

    try {
      permission.setApplications(applicationProvider.getApplications(groups));
    } catch (ProviderException pe) {
      permission.setPartialPermission(true);
    }

    if (!AnonymousUserConfig.ANONYMOUS_USERNAME.equalsIgnoreCase(userId)) {
      try {
        permission.setServiceAccounts(serviceAccountProvider.getAccounts(groups));
      } catch (ProviderException pe) {
        permission.setPartialPermission(true);
      }
    }

    return permission;
  }
}
