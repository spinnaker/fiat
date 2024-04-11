/*
 * Copyright 2022 Apple, Inc.
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

package com.netflix.spinnaker.fiat.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Migrated to {@link com.netflix.spinnaker.security.SpinnakerAuthorities} in {@code kork-security}.
 * This is left for backward compatibility.
 */
public class SpinnakerAuthorities extends com.netflix.spinnaker.security.SpinnakerAuthorities {
  public static final String ACCOUNT_MANAGER = "SPINNAKER_ACCOUNT_MANAGER";
  /** Granted authority for Spinnaker account managers. */
  public static final GrantedAuthority ACCOUNT_MANAGER_AUTHORITY =
      new SimpleGrantedAuthority(ACCOUNT_MANAGER);
}
