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

package com.netflix.spinnaker.fiat.providers;

import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverService;
import com.netflix.spinnaker.fiat.providers.internal.Front50Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DefaultApplicationProvider extends BaseProvider<Application> implements ResourceProvider<Application> {

  private final Front50Service front50Service;
  private final ClouddriverService clouddriverService;

  private final boolean allowAccessToUnknownApplications;

  public DefaultApplicationProvider(Front50Service front50Service,
                                    ClouddriverService clouddriverService,
                                    boolean allowAccessToUnknownApplications) {
    super();

    this.front50Service = front50Service;
    this.clouddriverService = clouddriverService;
    this.allowAccessToUnknownApplications = allowAccessToUnknownApplications;
  }

  @Override
  public Set<Application> getAllRestricted(Set<Role> roles, boolean isAdmin) throws ProviderException {
    return getAllApplications(roles, isAdmin, true);
  }

  @Override
  public Set<Application> getAllUnrestricted() throws ProviderException {
    return getAllApplications(Collections.emptySet(), false, false);
  }

  @Override
  protected Set<Application> loadAll() throws ProviderException {
    try {
      Map<String, Application> appByName = front50Service
          .getAllApplicationPermissions()
          .stream()
          .collect(Collectors.toMap(Application::getName,
                                    Function.identity()));

      clouddriverService
          .getApplications()
          .stream()
          .filter(app -> !appByName.containsKey(app.getName()))
          .forEach(app -> appByName.put(app.getName(), app));

      if (allowAccessToUnknownApplications) {
        // no need to include applications w/o explicit permissions if we're allowing access to unknown applications by default
        return appByName
            .values()
            .stream()
            .filter(a -> !a.getPermissions().isEmpty())
            .collect(Collectors.toSet());
      }

      return new HashSet<>(appByName.values());
    } catch (Exception e) {
      throw new ProviderException(this.getClass(), e.getCause());
    }
  }

  private Set<Application> getAllApplications(Set<Role> roles, boolean isAdmin, boolean isRestricted) {
    if (allowAccessToUnknownApplications) {
      /*
       * By default, the `BaseProvider` parent methods will filter out any applications that the authenticated user does
       * not have access to.
       *
       * This is incompatible with `allowAccessToUnknownApplications` which implicitly grants access to any unknown (or
       * filtered) applications.
       *
       * In this case, it is appropriate to just return all applications and allow the subsequent authorization checks
       * to determine whether read, write or nothing should be granted.
       */
      return getAll();
    }

    return isRestricted ? super.getAllRestricted(roles, isAdmin) : super.getAllUnrestricted();
  }
}
