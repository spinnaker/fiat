/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.fiat.providers.internal.resourcegroups;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.model.resources.groups.ResourceGroup;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class BaseGroupResolutionStrategy implements GroupResolutionStrategy {

  protected abstract Permissions resolveMatchingGroups(
      Set<ResourceGroup> matchingGroups, Resource.AccessControlled resource);

  public Permissions resolve(Set<ResourceGroup> groups, Resource.AccessControlled resource) {

    if (groups == null || groups.isEmpty()) {
      return resource.getPermissions();
    }

    Set<ResourceGroup> matchingGroups =
        groups.stream().filter(entry -> entry.contains(resource)).collect(Collectors.toSet());

    if (matchingGroups.isEmpty()) {
      return resource.getPermissions();
    }

    return resolveMatchingGroups(matchingGroups, resource);
  }
}
