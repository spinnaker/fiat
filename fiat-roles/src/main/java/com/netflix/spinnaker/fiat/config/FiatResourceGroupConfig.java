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

package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import com.netflix.spinnaker.fiat.model.resources.groups.ResourceGroup;
import com.netflix.spinnaker.fiat.providers.internal.resourcegroups.AdditiveGroupResolutionStrategy;
import com.netflix.spinnaker.fiat.providers.internal.resourcegroups.GroupResolutionStrategy;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties("fiat.resource-groups")
public class FiatResourceGroupConfig {
  private Set<ResourceGroup> resourceGroups = new HashSet<>();
  private GroupResolutionStrategy groupResolutionStrategy = new AdditiveGroupResolutionStrategy();

  public Set<ResourceGroup> getResourceGroupsForResourceType(ResourceType resourceType) {
    return resourceGroups.stream()
        .filter(group -> group.getResourceType() == resourceType)
        .collect(Collectors.toSet());
  }
}
