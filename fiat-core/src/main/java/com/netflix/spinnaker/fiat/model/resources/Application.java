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

package com.netflix.spinnaker.fiat.model.resources;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.fiat.model.Authorization;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
public class Application implements GroupAccessControlled, Resource, Viewable {
  final ResourceType resourceType = ResourceType.APPLICATION;

  private String name;
  private List<String> requiredGroupMembership = new ArrayList<>();

  @JsonIgnore
  public View getView() {
    return new View(this);
  }

  @Data
  @NoArgsConstructor
  public static class View extends BaseView implements Authorizable {
    String name;
    Set<Authorization> authorizations = ImmutableSet.of(Authorization.READ,
                                                        Authorization.WRITE);

    public View(Application application) {
      this.name = application.name;
    }
  }
}
