/*
 * Copyright 2024 Apple, Inc.
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

package com.netflix.spinnaker.fiat.shared;

import static org.springframework.security.config.Customizer.withDefaults;

import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.SpinnakerUsers;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.stereotype.Component;

@Component
public class FiatWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {
  private static final String KEY = "spinnaker-anonymous";
  private static final Object PRINCIPAL = SpinnakerUsers.ANONYMOUS;
  private static final List<GrantedAuthority> AUTHORITIES =
      List.of(SpinnakerAuthorities.ANONYMOUS_AUTHORITY);
  static final AnonymousAuthenticationToken ANONYMOUS =
      new AnonymousAuthenticationToken(KEY, PRINCIPAL, AUTHORITIES);

  private final FiatStatus fiatStatus;
  private final AuthenticationConverter authenticationConverter;

  public FiatWebSecurityConfigurerAdapter(
      FiatStatus fiatStatus, AuthenticationConverter authenticationConverter) {
    super(true);
    this.fiatStatus = fiatStatus;
    this.authenticationConverter = authenticationConverter;
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.servletApi(withDefaults())
        .exceptionHandling(withDefaults())
        .anonymous(
            anonymous ->
                // https://github.com/spinnaker/spinnaker/issues/6918
                // match the same anonymous userid as expected elsewhere
                anonymous.principal(PRINCIPAL).key(KEY).authorities(AUTHORITIES))
        .addFilterBefore(
            new FiatAuthenticationFilter(fiatStatus, authenticationConverter),
            AnonymousAuthenticationFilter.class);
  }
}
