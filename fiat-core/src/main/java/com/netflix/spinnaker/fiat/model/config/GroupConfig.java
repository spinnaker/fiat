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

package com.netflix.spinnaker.fiat.model.config;

import com.netflix.spinnaker.fiat.model.resources.groups.AdditiveGroupResolutionStrategy;
import com.netflix.spinnaker.fiat.model.resources.groups.GroupResolutionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class GroupConfig {

  @Bean
  GroupResolutionStrategy groupResolutionStrategy() {
    // Currently, we only have one group resolution strategy, which is the additive one. Later on we
    // might add more,
    // and make it configurable which group resolution strategy applies to which resource type
    return new AdditiveGroupResolutionStrategy();
  }
}
