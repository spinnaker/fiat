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

package com.netflix.spinnaker.fiat.providers;

import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import javax.annotation.Nonnull;
import lombok.Data;

@Data
public class ApplicationPrefixPermissionSource implements ResourcePermissionSource<Application> {

  private String prefix;
  private Permissions permissions;

  public ApplicationPrefixPermissionSource setPrefix(String prefix) {
    if (!prefix.endsWith("*")) {
      throw new IllegalArgumentException("Prefix expressions must end with a *");
    }
    this.prefix = prefix;

    return this;
  }

  @Nonnull
  @Override
  public Permissions getPermissions(@Nonnull Application resource) {
    if (contains(resource)) {
      return permissions;
    } else {
      return Permissions.EMPTY;
    }
  }

  private boolean contains(Application application) {
    String prefixWithoutStar = prefix.substring(0, prefix.length() - 1);
    return application.getName().startsWith(prefixWithoutStar);
  }
}
