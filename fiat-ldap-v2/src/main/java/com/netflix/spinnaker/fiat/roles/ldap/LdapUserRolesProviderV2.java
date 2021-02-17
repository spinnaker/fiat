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

import com.netflix.spinnaker.fiat.config.LdapConfigV2;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.DistinguishedName;
import org.springframework.ldap.support.LdapEncoder;
import org.springframework.security.ldap.LdapUtils;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(value = "auth.group-membership.service", havingValue = "ldapv2")
public class LdapUserRolesProviderV2 implements UserRolesProvider {

  @Setter private SpringSecurityLdapTemplate ldapTemplate;

  @Setter private LdapConfigV2.ConfigProps configProps;

  public LdapUserRolesProviderV2(
      @Autowired SpringSecurityLdapTemplate ldapTemplate,
      @Autowired LdapConfigV2.ConfigProps configProps) {
    this.ldapTemplate = ldapTemplate;
    this.configProps = configProps;
  }

  @Override
  public List<Role> loadRoles(ExternalUser user) {
    String userId = user.getId();

    log.debug("loadRoles for user {}", userId);
    if (StringUtils.isEmpty(configProps.getGroupSearchBase())) {
      return new ArrayList<>();
    }

    String fullUserDn = getUserFullDn(userId);

    if (fullUserDn == null) {
      // Likely a service account
      log.debug("fullUserDn is null for {}", userId);
      return new ArrayList<>();
    }

    String[] params = new String[] {fullUserDn, userId};
    log.debug("Searching for groups using \ngroupSearchBase: {}\ngroupSearchFilter: {}\nparams: {}\ngroupRoleAttributes: {}",
            configProps.getGroupSearchBase(),
            configProps.getGroupSearchFilter(),
            StringUtils.join(params, " :: "),
            configProps.getGroupRoleAttributes());


    // Copied from org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator.
    Set<String> userRoles =
        ldapTemplate.searchForSingleAttributeValues(
            configProps.getGroupSearchBase(),
            configProps.getGroupSearchFilter(),
            params,
            configProps.getGroupRoleAttributes());

    log.debug("Got roles for user {}: {}", userId, userRoles);
    return userRoles.stream()
        .map(role -> new Role(role).setSource(Role.Source.LDAP))
        .collect(Collectors.toList());
  }

  private class RoleFullDNtoUserRoleMapper implements AttributesMapper<Role> {
    @Override
    public Role mapFromAttributes(Attributes attrs) throws NamingException {
      return new Role(attrs.get(configProps.getGroupRoleAttributes()).get().toString())
          .setSource(Role.Source.LDAP);
    }
  }

  private class UserRoleMapper implements AttributesMapper<Pair<String, Collection<Role>>> {
    @Override
    public Pair<String, Collection<Role>> mapFromAttributes(Attributes attrs)
        throws NamingException {
      List<Role> roles = new ArrayList<>();
      Attribute memberOfAttribute = attrs.get("memberOf");
      if (memberOfAttribute != null) {
        for (NamingEnumeration<?> memberOf = memberOfAttribute.getAll(); memberOf.hasMore(); ) {
          String roleDN = memberOf.next().toString();
          LdapName ln = org.springframework.ldap.support.LdapUtils.newLdapName(roleDN);
          String role =
              org.springframework.ldap.support.LdapUtils.getStringValue(
                  ln, configProps.getGroupRoleAttributes());
          roles.add(new Role(role).setSource(Role.Source.LDAP));
        }
      }

      return Pair.of(
          attrs.get(configProps.getUserIdAttributes()).get().toString().toLowerCase(), roles);
    }
  }

  @Override
  public Map<String, Collection<Role>> multiLoadRoles(Collection<ExternalUser> users) {
    if (StringUtils.isEmpty(configProps.getGroupSearchBase())) {
      return new HashMap<>();
    }
    StringBuilder filter = new StringBuilder();
    filter.append("(|");
    users.forEach(
        u -> filter.append(MessageFormat.format(configProps.getUserSearchFilter(), u.getId())));
    filter.append(")");

    Map<String, List<ExternalUser>> userIds =
        users.stream().collect(Collectors.groupingBy(e -> e.getId().toLowerCase()));
    List<Role> roles =
        ldapTemplate.search(
            configProps.getGroupSearchBase(),
            MessageFormat.format(configProps.getGroupSearchFilter(), "*", "*"),
            new RoleFullDNtoUserRoleMapper());

    return ldapTemplate
        .search(configProps.getUserSearchBase(), filter.toString(), new UserRoleMapper())
        .stream()
        .flatMap(
            p -> {
              List<ExternalUser> sameUsers = userIds.get(p.getKey().toLowerCase());
              return sameUsers.stream()
                  .flatMap(it -> p.getValue().stream().map(role -> Pair.of(it.getId(), role)));
            })
        .filter(p -> roles.contains(p.getValue()))
        .collect(
            Collectors.groupingBy(
                Pair::getKey,
                Collectors.mapping(Pair::getValue, Collectors.toCollection(ArrayList::new))));
  }

  private String getUserFullDn(String userId) {
    String rootDn = LdapUtils.parseRootDnFromUrl(configProps.getUrl());
    DistinguishedName root = new DistinguishedName(rootDn);
    log.debug("Root DN: {}", root.toString());

    String[] formatArgs = new String[] {LdapEncoder.nameEncode(userId)};

    String partialUserDn;
    if (!StringUtils.isEmpty(configProps.getUserSearchFilter())) {
      try {
        DirContextOperations res =
            ldapTemplate.searchForSingleEntry(
                configProps.getUserSearchBase(), configProps.getUserSearchFilter(), formatArgs);
        partialUserDn = res.getDn().toString();
      } catch (IncorrectResultSizeDataAccessException e) {
        log.error("Unable to find a single user entry", e);
        return null;
      }
    } else {
      partialUserDn = configProps.getUserDnPattern().format(formatArgs);
    }

    DistinguishedName user = new DistinguishedName(partialUserDn);
    log.debug("User portion: {}", user.toString());

    try {
      Name fullUser = root.addAll(user);
      log.debug("Full user DN: {}", fullUser.toString());
      return fullUser.toString();
    } catch (InvalidNameException ine) {
      log.error("Could not assemble full userDn", ine);
    }
    return null;
  }
}
