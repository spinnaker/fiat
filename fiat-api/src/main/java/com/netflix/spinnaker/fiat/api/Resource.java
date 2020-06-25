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

package com.netflix.spinnaker.fiat.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.kork.annotations.Alpha;
import org.pf4j.ExtensionPoint;

/** Represents a Fiat resource (e.g., a Role or a Service account). */
@Alpha
public interface Resource extends ExtensionPoint {
  String getName();

  @JsonIgnore
  ResourceType getResourceType();

  /**
   * Represents Resources that have restrictions on permissions (e.g., an Account or an
   * Application). Plugin developers should implement this interface.
   */
  @Alpha
  interface AccessControlled extends Resource {

    Permissions getPermissions();
  }
}
