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

package com.netflix.spinnaker.fiat.model.resources.groups;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Add all the permissions from the matching groups to the resource
 *
 * <p>For example, if we have: "*": { "WRITE": ["group1"] }, "cool*": { "WRITE": ["group2"] } }, and
 * the resource has "cool_api": { "WRITE": ["group3"] } Then resource "cool_api" will have all three
 * groups in its `WRITE` authorization
 */
@Component
public class AdditiveGroupResolutionStrategy implements GroupResolutionStrategy {
  @Override
  public Permissions resolve(
      Set<ResourceGroup> matchingGroups, Resource.AccessControlled resource) {
    return Permissions.Builder.combineFactory(
            Stream.concat(
                    Stream.of(resource.getPermissions()),
                    matchingGroups.stream().map(ResourceGroup::getPermissions))
                .collect(Collectors.toSet()))
        .build();
  }
}
