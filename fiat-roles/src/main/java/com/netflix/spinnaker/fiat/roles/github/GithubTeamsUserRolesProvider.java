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

package com.netflix.spinnaker.fiat.roles.github;

import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import com.netflix.spinnaker.fiat.roles.github.client.GitHubMaster;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import retrofit.RetrofitError;
import retrofit.client.Response;

import java.util.*;

@Slf4j
@Component
@ConditionalOnProperty(value = "auth.groupMembership.service", havingValue = "github")
public class GithubTeamsUserRolesProvider implements UserRolesProvider, InitializingBean {

  @Autowired
  @Setter
  GitHubMaster master;

  @Autowired
  @Setter
  GitHubProperties gitHubProperties;

  @Override
  public List<String> loadRoles(String userName) {
    if (StringUtils.isEmpty(userName)|| StringUtils.isEmpty(gitHubProperties.getOrganization())) {
      return Collections.emptyList();
    }
    // check organization if set.
    // If organization is unset, all GitHub users can login and have full access
    // If an organization is set, add it to roles to restrict users to this organization
    // If organization is set AND requiredGroupMembership set, organization members will have RO access
    // and requiredGroupMembership members RW access
    Boolean isMemberOfOrg = false;

    try {
      Response response = master.getGitHubClient()
                                .isMemberOfOrganization(gitHubProperties.getOrganization(),
                                                        userName);
      isMemberOfOrg = (response.getStatus() == 204);
    } catch (RetrofitError e) {
      if (e.getKind() == RetrofitError.Kind.NETWORK) {
        log.error("Could not find the server {master.baseUrl}", e);
        return Collections.emptyList();
      } else if (e.getResponse().getStatus() == 404) {
        log.error("Could not find the GitHub organization {gitHubProperties.getOrganization()}", e);
        return Collections.emptyList();
      } else if (e.getResponse().getStatus() == 401) {
        log.error("Cannot get GitHub organization {gitHubProperties.getOrganization()} information: Not authorized.", e);
        return Collections.emptyList();
      }
    }

    if (!isMemberOfOrg) {
      return Collections.emptyList();
    }

    List<String> result = new ArrayList<>();
    result.add(gitHubProperties.getOrganization());

    // Get teams of the current user
    List<GitHubMaster.Team> teams = new ArrayList<>();
    try {
      teams = master.getGitHubClient().getOrgTeams(gitHubProperties.getOrganization());

    } catch (RetrofitError e) {
      log.error("RetrofitError ${e.response.status} ${e.response.reason} ", e);
      if (e.getKind() == RetrofitError.Kind.NETWORK) {
        log.error("Could not find the server ${master.baseUrl}", e);
      } else if (e.getResponse().getStatus() == 404) {
        log.error(" 404 when getting teams");
        return result;
      } else if (e.getResponse().getStatus() == 401) {
        log.error("Cannot get GitHub organization ${gitHubProperties.getOrganization()} teams: Not authorized.", e);
        return result;
      }
    }

    teams.forEach(t -> {
      if (isMemberOfTeam(t, userName)) {
        result.add(t.getSlug());
      }
    });

    return result;
  }


  private boolean isMemberOfTeam(GitHubMaster.Team t, String userName) {
    String ACTIVE = "active";
    try {
      GitHubMaster.TeamMembership response = master.getGitHubClient().isMemberOfTeam(t.getId(), userName);
      return (response.getState().equals(ACTIVE));
    } catch (RetrofitError e) {
      if (e.getKind() == RetrofitError.Kind.NETWORK) {
        log.error("Could not find the server ${master.baseUrl}");
      } else if (e.getResponse().getStatus() == 401) {
        log.error("Cannot check if $userName is member of ${t.name} teams: Not authorized.", e);
      }
    }
    return false;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    Assert.state(gitHubProperties.getOrganization ()!= null, "Supply an organization");
  }

  @Override
  public Map<String, Collection<String>> multiLoadRoles(Collection<String> userEmails) {
    if (userEmails == null || userEmails.isEmpty()) {
      return Collections.emptyMap();
    }

    val emailGroupsMap = new HashMap<String, Collection<String>>();
    userEmails.forEach(email -> emailGroupsMap.put(email, loadRoles(email)));

    return emailGroupsMap;
  }
}
