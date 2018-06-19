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
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.spinnaker.fiat.config.ResourceProvidersHealthIndicator;
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.permissions.PermissionResolutionException;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.fiat.permissions.PermissionsResolver;
import com.netflix.spinnaker.fiat.providers.ProviderException;
import com.netflix.spinnaker.fiat.providers.ResourceProvider;
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent;
import com.netflix.spinnaker.kork.lock.LockManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnExpression("${fiat.writeMode.enabled:true}")
public class UserRolesSyncer implements ApplicationListener<RemoteStatusChangedEvent> {
  private final Optional<DiscoveryClient> discoveryClient;

  private final LockManager lockManager;
  private final PermissionsRepository permissionsRepository;
  private final PermissionsResolver permissionsResolver;
  private final ResourceProvider<ServiceAccount> serviceAccountProvider;
  private final ResourceProvidersHealthIndicator healthIndicator;

  private final long retryIntervalMs;
  private final long syncDelayMs;
  private final long syncFailureDelayMs;
  private final long syncDelayTimeoutMs;

  private final AtomicBoolean isEnabled;

  @Autowired
  public UserRolesSyncer(Optional<DiscoveryClient> discoveryClient,
                         LockManager lockManager,
                         PermissionsRepository permissionsRepository,
                         PermissionsResolver permissionsResolver,
                         ResourceProvider<ServiceAccount> serviceAccountProvider,
                         ResourceProvidersHealthIndicator healthIndicator,
                         @Value("${fiat.writeMode.retryIntervalMs:10000}") long retryIntervalMs,
                         @Value("${fiat.writeMode.syncDelayMs:600000}") long syncDelayMs,
                         @Value("${fiat.writeMode.syncFailureDelayMs:600000}") long syncFailureDelayMs,
                         @Value("${fiat.writeMode.syncDelayTimeoutMs:30000}") long syncDelayTimeoutMs) {
    this.discoveryClient = discoveryClient;

    this.lockManager = lockManager;
    this.permissionsRepository = permissionsRepository;
    this.permissionsResolver = permissionsResolver;
    this.serviceAccountProvider = serviceAccountProvider;
    this.healthIndicator = healthIndicator;

    this.retryIntervalMs = retryIntervalMs;
    this.syncDelayMs = syncDelayMs;
    this.syncFailureDelayMs = syncFailureDelayMs;
    this.syncDelayTimeoutMs = syncDelayTimeoutMs;

    this.isEnabled = new AtomicBoolean(
        // default to enabled iff discovery is not available
        !discoveryClient.isPresent()
    );
  }

  @Override
  public void onApplicationEvent(RemoteStatusChangedEvent event) {
    isEnabled.set(isInService());
  }

  @Scheduled(fixedDelay = 30000L)
  public void schedule() {
    if (syncDelayMs < 0 || !isEnabled.get()) {
      return;
    }

    LockManager.LockOptions lockOptions = new LockManager.LockOptions()
        .withLockName("Fiat.UserRolesSyncer".toLowerCase())
        .withMaximumLockDuration(Duration.ofMillis(syncDelayMs + syncDelayTimeoutMs))
        .withSuccessInterval(Duration.ofMillis(syncDelayMs))
        .withFailureInterval(Duration.ofMillis(syncFailureDelayMs));

    lockManager.acquireLock(lockOptions, this::syncAndReturn);
  }

  public long syncAndReturn() {
    FixedBackOff backoff = new FixedBackOff();
    backoff.setInterval(retryIntervalMs);
    backoff.setMaxAttempts(Math.floorDiv(syncDelayTimeoutMs, retryIntervalMs) + 1);
    BackOffExecution backOffExec = backoff.start();

    //after this point the execution will get rescheduled
    final long timeout = System.currentTimeMillis() + syncDelayTimeoutMs;

    if (!isServerHealthy()) {
      log.warn("Server is currently UNHEALTHY. User permission role synchronization and " +
          "resolution may not complete until this server becomes healthy again.");
    }

    while (true) {
      try {
        Map<String, UserPermission> combo = new HashMap<>();
        //force a refresh of the unrestricted user in case the backing repository is empty:
        combo.put(UnrestrictedResourceConfig.UNRESTRICTED_USERNAME, new UserPermission());
        Map<String, UserPermission> temp;
        if (!(temp = getUserPermissions()).isEmpty()) {
          combo.putAll(temp);
        }
        if (!(temp = getServiceAccountsAsMap()).isEmpty()) {
          combo.putAll(temp);
        }

        return updateUserPermissions(combo);
      } catch (ProviderException | PermissionResolutionException ex) {
        Status status = healthIndicator.health().getStatus();
        long waitTime = backOffExec.nextBackOff();
        if (waitTime == BackOffExecution.STOP || System.currentTimeMillis() > timeout) {
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
        .map(ServiceAccount::toUserPermission)
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

    List<ExternalUser> extUsers = permissionsById
        .values()
        .stream()
        .map(permission -> new ExternalUser()
            .setId(permission.getId())
            .setExternalRoles(permission.getRoles()
                .stream()
                .filter(role -> role.getSource() == Role.Source.EXTERNAL)
                .collect(Collectors.toList())))
        .collect(Collectors.toList());

    long count = permissionsResolver.resolve(extUsers)
        .values()
        .stream()
        .map(permission -> permissionsRepository.put(permission))
        .count();
    log.info("Synced {} non-anonymous user roles.", count);
    return count;
  }

  private boolean isInService() {
    InstanceInfo.InstanceStatus remoteStatus = null;
    if (discoveryClient.isPresent()) {
      remoteStatus = discoveryClient.get().getInstanceRemoteStatus();
    }

    boolean isInService = (remoteStatus == null || remoteStatus == InstanceInfo.InstanceStatus.UP);

    log.info(
        "User roles syncing is {} (discoveryStatus: {})",
        isInService ? "active" : "disabled",
        remoteStatus
    );

    return isInService;
  }
}
