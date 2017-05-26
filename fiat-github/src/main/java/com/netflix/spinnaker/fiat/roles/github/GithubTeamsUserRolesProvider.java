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

import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import com.netflix.spinnaker.fiat.roles.github.client.GitHubClient;
import com.netflix.spinnaker.fiat.roles.github.model.GraphqlTeamRequest;
import com.netflix.spinnaker.fiat.roles.github.model.Team;
import com.netflix.spinnaker.fiat.roles.github.model.TeamEdge;
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
import retrofit.client.Header;
import retrofit.client.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(value = "auth.groupMembership.service", havingValue = "github")
public class GithubTeamsUserRolesProvider implements UserRolesProvider, InitializingBean {

  private static List<String> RATE_LIMITING_HEADERS = Arrays.asList(
      "X-RateLimit-Limit",
      "X-RateLimit-Remaining",
      "X-RateLimit-Reset"
  );

  private static String GRAPHQL_QUERY_TPL = "{\norganization(login: \"ORGANIZATION\") {\nteams(first: 100, userLogins: [\"USER\"]) {\nedges {\nnode {\nid\nname\nslug\n}\n}\n}\n}\n}\n\"";
  private static String ORGANIZATION = "ORGANIZATION";
  private static String USER = "USER";

  @Autowired
  @Setter
  private GitHubClient gitHubClient;

  @Autowired
  @Setter
  private GitHubProperties gitHubProperties;

  @Override
  public void afterPropertiesSet() throws Exception {
    Assert.state(gitHubProperties.getOrganization() != null, "Supply an organization");
  }

  @Override
  public List<Role> loadRoles(String username) {
    log.debug("loadRoles for user " + username);
    if (StringUtils.isEmpty(username) || StringUtils.isEmpty(gitHubProperties.getOrganization())) {
      return new ArrayList<>();
    }

    if (!isMemberOfOrg(username)) {
      log.debug(username + "is not a member of organization " + gitHubProperties.getOrganization());
      return new ArrayList<>();
    }
    log.debug(username + "is a member of organization " + gitHubProperties.getOrganization());

    List<Role> result = new ArrayList<>();
    result.add(toRole(gitHubProperties.getOrganization()));

    // Get teams of the org
    List<Team> teams = getTeamsGraphql(username);
    log.debug("Found " + teams.size() + " teams in org for " + username);

    result.addAll(
            teams.stream()
                    .map(Team::getSlug)
                    .map(GithubTeamsUserRolesProvider::toRole)
                    .collect(Collectors.toList())
    );

    return result;
  }

  private boolean isMemberOfOrg(String username) {
    boolean isMemberOfOrg = false;
    try {
      Response response = gitHubClient.isMemberOfOrganization(gitHubProperties.getOrganization(),
                                                              username);
      isMemberOfOrg = (response.getStatus() == 204);
    } catch (RetrofitError e) {
      if (e.getResponse().getStatus() != 404) {
        handleNon404s(e);
      }
    }

    return isMemberOfOrg;
  }

  private List<Team> getTeamsGraphql(String username) {
    log.debug("Fetching " + username + " teams.");
    List<TeamEdge> edges = new ArrayList<>();

    try {
      GraphqlTeamRequest request = new GraphqlTeamRequest();
      request.setQuery(buildGraphqlQuery(gitHubProperties.getOrganization(), username));
      edges = gitHubClient.getUserTeams(request).getData().getOrganizationResult().getTeams().getEdges();
    } catch (RetrofitError e) {
      if (e.getResponse().getStatus() != 404) {
        handleNon404s(e);
      } else {
        log.error("404 when getting teams", e);
      }
    }
    return edges.stream()
            .map(TeamEdge::getNode)
            .collect(Collectors.toList());
  }

  private void handleNon404s(RetrofitError e) {
    String msg = "";
    if (e.getKind() == RetrofitError.Kind.NETWORK) {
      msg = String.format("Could not find the server %s", gitHubProperties.getBaseUrl());
    } else if (e.getResponse().getStatus() == 401) {
      msg = "HTTP 401 Unauthorized.";
    } else if (e.getResponse().getStatus() == 403) {
      val rateHeaders = e.getResponse()
                         .getHeaders()
                         .stream()
                         .filter(header -> RATE_LIMITING_HEADERS.contains(header.getName()))
                         .map(Header::toString)
                         .collect(Collectors.toList());

      msg = "HTTP 403 Forbidden. Rate limit info: " + StringUtils.join(rateHeaders, ", ");
    }
    log.error(msg, e);
  }

  private static Role toRole(String name) {
    return new Role().setName(name.toLowerCase()).setSource(Role.Source.GITHUB_TEAMS);
  }

  @Override
  public Map<String, Collection<Role>> multiLoadRoles(Collection<String> userEmails) {
    if (userEmails == null || userEmails.isEmpty()) {
      return new HashMap<>();
    }

    val emailGroupsMap = new HashMap<String, Collection<Role>>();
    userEmails.forEach(email -> emailGroupsMap.put(email, loadRoles(email)));

    return emailGroupsMap;
  }

  private String buildGraphqlQuery(String organization, String user) {
    return GRAPHQL_QUERY_TPL.replace(ORGANIZATION, organization).replace(USER, user);
  }
}
