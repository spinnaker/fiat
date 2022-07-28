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

package com.netflix.spinnaker.fiat.shared;

import com.netflix.spinnaker.fiat.model.SpinnakerAuthorities;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.servlet.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

@Slf4j
public class FiatAuthenticationFilter implements Filter {

  private final FiatStatus fiatStatus;
  private final FiatPermissionEvaluator permissionEvaluator;

  public FiatAuthenticationFilter(
      FiatStatus fiatStatus, FiatPermissionEvaluator permissionEvaluator) {
    this.fiatStatus = fiatStatus;
    this.permissionEvaluator = permissionEvaluator;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    Authentication auth;
    if (!fiatStatus.isEnabled()) {
      List<GrantedAuthority> authorities =
          List.of(SpinnakerAuthorities.ADMIN_AUTHORITY, SpinnakerAuthorities.ANONYMOUS_AUTHORITY);
      auth = new AnonymousAuthenticationToken("anonymous", "anonymous", authorities);
    } else {
      auth =
          AuthenticatedRequest.getSpinnakerUser()
              .map(
                  username -> {
                    UserPermission.View permission = permissionEvaluator.getPermission(username);
                    if (permission == null) {
                      return null;
                    }
                    Set<GrantedAuthority> authorities = permission.toGrantedAuthorities();
                    return (Authentication)
                        new PreAuthenticatedAuthenticationToken(username, null, authorities);
                  })
              .orElseGet(
                  () ->
                      new AnonymousAuthenticationToken(
                          "anonymous",
                          "anonymous",
                          List.of(SpinnakerAuthorities.ANONYMOUS_AUTHORITY)));
    }

    var ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(auth);
    SecurityContextHolder.setContext(ctx);
    log.debug("Set SecurityContext to user: {}", auth.getPrincipal().toString());
    chain.doFilter(request, response);
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  public void destroy() {}
}
