/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.fiat.roles.ldap;

import com.netflix.spinnaker.fiat.config.LdapConfig;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ldap.core.DistinguishedName;
import org.springframework.ldap.core.LdapEncoder;
import org.springframework.security.ldap.LdapUtils;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;
import org.springframework.stereotype.Component;

import javax.naming.InvalidNameException;
import javax.naming.Name;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(value = "auth.groupMembership.service", havingValue = "ldap")
public class LdapUserRolesProvider implements UserRolesProvider {

  @Autowired
  @Setter
  private SpringSecurityLdapTemplate ldapTemplate;

  @Autowired
  @Setter
  private LdapConfig.ConfigProps configProps;

  @Override
  public List<Role> loadRoles(String userId) {
    log.debug("loadRoles for user " + userId);
    if (StringUtils.isEmpty(configProps.getGroupSearchBase())) {
      return new ArrayList<>();
    }

    String[] params = new String[]{getUserFullDn(userId), userId};

    if (log.isDebugEnabled()) {
      log.debug(new StringBuilder("Searching for groups using ")
                    .append("\ngroupSearchBase: ")
                    .append(configProps.getGroupSearchBase())
                    .append("\ngroupSearchFilter: ")
                    .append(configProps.getGroupSearchFilter())
                    .append("\nparams: ")
                    .append(StringUtils.join(params, " :: "))
                    .append("\ngroupRoleAttributes: ")
                    .append(configProps.getGroupRoleAttributes())
                    .toString());
    }

    // Copied from org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator.
    Set<String> userRoles = ldapTemplate.searchForSingleAttributeValues(
        configProps.getGroupSearchBase(),
        configProps.getGroupSearchFilter(),
        params,
        configProps.getGroupRoleAttributes());

    log.debug("Got roles for user " + userId + ": " + userRoles);
    return userRoles.stream()
                    .map(role -> new Role(role).setSource(Role.Source.LDAP))
                    .collect(Collectors.toList());
  }

  @Override
  public Map<String, Collection<Role>> multiLoadRoles(Collection<String> userIds) {
    if (StringUtils.isEmpty(configProps.getGroupSearchBase())) {
      return new HashMap<>();
    }

    // ExternalUser is used here as a simple data type to hold the username/roles combination.
    return userIds.stream()
                  .map(userId -> new ExternalUser().setId(userId).setExternalRoles(loadRoles(userId)))
                  .collect(Collectors.toMap(ExternalUser::getId, ExternalUser::getExternalRoles));
  }

  private String getUserFullDn(String userId) {
    String rootDn = LdapUtils.parseRootDnFromUrl(configProps.getUrl());
    DistinguishedName root = new DistinguishedName(rootDn);
    log.debug("Root DN: " + root.toString());

    String[] formatArgs = new String[]{LdapEncoder.nameEncode(userId)};
    String formattedUser = configProps.getUserDnPattern().format(formatArgs);
    DistinguishedName user = new DistinguishedName(formattedUser);
    log.debug("User portion: " + user.toString());

    try {
      Name fullUser = root.addAll(user);
      log.debug("Full user DN: " + fullUser.toString());
      return fullUser.toString();
    } catch (InvalidNameException ine) {
      log.error("Could not assemble full userDn", ine);
    }
    return null;
  }
}
