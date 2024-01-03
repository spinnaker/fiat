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

package com.netflix.spinnaker.fiat.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public enum Authorization {
  READ,
  WRITE,
  EXECUTE,
  CREATE;

  private static final Map<com.netflix.spinnaker.security.Authorization, Authorization>
      KORK_TO_FIAT =
          Map.of(
              com.netflix.spinnaker.security.Authorization.READ, READ,
              com.netflix.spinnaker.security.Authorization.WRITE, WRITE,
              com.netflix.spinnaker.security.Authorization.EXECUTE, EXECUTE,
              com.netflix.spinnaker.security.Authorization.CREATE, CREATE);

  public static final Set<Authorization> ALL =
      Collections.unmodifiableSet(EnumSet.allOf(Authorization.class));

  @CheckForNull
  public static Authorization parse(@Nullable Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof Authorization) {
      return (Authorization) o;
    }
    if (o instanceof com.netflix.spinnaker.security.Authorization) {
      return KORK_TO_FIAT.get(o);
    }
    var string = o.toString().toUpperCase(Locale.ROOT);
    try {
      return valueOf(string);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}
