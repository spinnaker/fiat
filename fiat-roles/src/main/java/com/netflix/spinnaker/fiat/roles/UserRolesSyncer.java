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

package com.netflix.spinnaker.fiat.roles;

import com.diffplug.common.base.Functions;
import com.netflix.spinnaker.fiat.config.ResourceProvidersHealthIndicator;
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.fiat.permissions.PermissionResolutionException;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.fiat.permissions.PermissionsResolver;
import com.netflix.spinnaker.fiat.providers.ProviderException;
import com.netflix.spinnaker.fiat.providers.ServiceAccountProvider;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Status;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class UserRolesSyncer {

  @Autowired
  @Setter
  private PermissionsRepository permissionsRepository;

  @Autowired
  @Setter
  private PermissionsResolver permissionsResolver;

  @Autowired(required = false)
  @Setter
  private ServiceAccountProvider serviceAccountProvider;

  @Autowired
  @Setter
  private ResourceProvidersHealthIndicator healthIndicator;

  @Value("${auth.userSync.retryIntervalMs:10000}")
  @Setter
  private long retryIntervalMs;

  // TODO(ttomsu): Acquire a lock in order to make this scale to multiple instances.
  @Scheduled(fixedDelayString = "${auth.userSync.intervalMs:600000}")
  public void sync() {
    syncAndReturn();
  }

  public long syncAndReturn() {
    FixedBackOff backoff = new FixedBackOff();
    backoff.setInterval(retryIntervalMs);
    BackOffExecution backOffExec = backoff.start();

    if (!isServerHealthy()) {
      log.warn("Server is currently UNHEALTHY. User permission role synchronization and " +
                   "resolution may not complete until this server becomes healthy again.");
    }

    while (true) {
      try {
        Map<String, UserPermission> combo = new HashMap<>();
        Map<String, UserPermission> temp;
        if (!(temp = getUserPermissions()).isEmpty()) {
          combo.putAll(temp);
        }
        if (!(temp = getServiceAccountsAsMap()).isEmpty()) {
          combo.putAll(temp);
        }

        return updateUserPermissions(combo);
      } catch (ProviderException|PermissionResolutionException ex) {
        Status status = healthIndicator.health().getStatus();
        long waitTime = backOffExec.nextBackOff();
        if (waitTime == BackOffExecution.STOP) {
          log.error("Unable to resolve service account permissions.", ex);
          return 0;
        }
        String message = new StringBuilder("User permission sync failed. ")
            .append("Server status is ")
            .append(status)
            .append(". Trying again in ")
            .append(waitTime)
            .append(" ms. Cause:")
            .append(ex.getMessage())
            .toString();
        if (log.isDebugEnabled()) {
          log.debug(message, ex);
        } else {
          log.warn(message);
        }

        try {
          Thread.sleep(waitTime);
        } catch (InterruptedException ignored) {
        }
      } finally {
        isServerHealthy();
      }
    }
  }

  private boolean isServerHealthy() {
    return healthIndicator.health().getStatus() == Status.UP;
  }

  private Map<String, UserPermission> getServiceAccountsAsMap() {
    return serviceAccountProvider
        .getAll()
        .stream()
        .map(serviceAccount -> new UserPermission().setId(serviceAccount.getName()))
        .collect(Collectors.toMap(UserPermission::getId, Functions.identity()));
  }

  private Map<String, UserPermission> getUserPermissions() {
    return permissionsRepository.getAllById();
  }

  public long updateUserPermissions(Map<String, UserPermission> permissionsById) {
    if (permissionsById.remove(UnrestrictedResourceConfig.UNRESTRICTED_USERNAME) != null) {
      permissionsRepository.put(permissionsResolver.resolveUnrestrictedUser());
      log.info("Synced anonymous user role.");
    }

    long count = permissionsResolver.resolve(permissionsById.keySet())
                                    .values()
                                    .stream()
                                    .map(permission -> permissionsRepository.put(permission))
                                    .count();
    log.info("Synced {} non-anonymous user roles.", count);
    return count;
  }
}
