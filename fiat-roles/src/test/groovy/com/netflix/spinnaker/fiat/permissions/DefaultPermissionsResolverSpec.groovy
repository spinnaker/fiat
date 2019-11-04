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

package com.netflix.spinnaker.fiat.permissions

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.fiat.config.FiatAdminConfig
import com.netflix.spinnaker.fiat.config.FiatRoleConfig
import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Account
import com.netflix.spinnaker.fiat.model.resources.Application
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount
import com.netflix.spinnaker.fiat.providers.AccessControlledResourcePermissionSource
import com.netflix.spinnaker.fiat.providers.AggregatingResourcePermissionProvider
import com.netflix.spinnaker.fiat.providers.DefaultAccountResourceProvider
import com.netflix.spinnaker.fiat.providers.DefaultServiceAccountResourceProvider
import com.netflix.spinnaker.fiat.providers.ResourcePermissionProvider
import com.netflix.spinnaker.fiat.providers.ResourceProvider
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverService
import com.netflix.spinnaker.fiat.providers.internal.Front50Service
import com.netflix.spinnaker.fiat.roles.UserRolesProvider
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DefaultPermissionsResolverSpec extends Specification {
  UserRolesProvider userRolesProvider = Mock(UserRolesProvider)

  @Shared
  Role anonymous = new Role("anonymous")

  @Shared
  Account noReqGroupsAcct = new Account().setName("noReqGroups")

  @Shared
  Account reqGroup1Acct = new Account().setName("reqGroup1")
                                       .setRequiredGroupMembership(["group1"])
  @Shared
  Account reqGroup1and2Acct = new Account().setName("reqGroup1and2")
                                           .setRequiredGroupMembership(["group1", "GrouP2"]) // test case insensitivity.

  @Shared
  Account anonymousRead = new Account().setName("anonymousRead")
                                       .setPermissions(Permissions.factory((Authorization.READ): [anonymous.name], (Authorization.WRITE): ["otherGroup"]))

  @Shared
  ClouddriverService clouddriverService = Mock(ClouddriverService) {
    getAccounts() >> [noReqGroupsAcct, reqGroup1Acct, reqGroup1and2Acct, anonymousRead]
  }

  @Shared
  ResourcePermissionProvider<Account> accountResourcePermissionProvider =
    new AggregatingResourcePermissionProvider<>([new AccessControlledResourcePermissionSource<>()])

  @Shared
  DefaultAccountResourceProvider accountProvider = new DefaultAccountResourceProvider(clouddriverService, accountResourcePermissionProvider)

  @Shared ServiceAccount group1SvcAcct = new ServiceAccount().setName("group1")
                                                             .setMemberOf(["group1"])
  @Shared ServiceAccount group2SvcAcct = new ServiceAccount().setName("group2")
                                                             .setMemberOf(["group2"])

  @Shared
  Front50Service front50Service = Mock(Front50Service) {
    getAllServiceAccounts() >> [group1SvcAcct, group2SvcAcct]
  }

  @Shared
  FiatRoleConfig fiatRoleConfig = Mock(FiatRoleConfig) {
    isOrMode() >> false
  }

  @Shared
  DefaultServiceAccountResourceProvider serviceAccountProvider = new DefaultServiceAccountResourceProvider(front50Service, fiatRoleConfig)

  @Shared
  ResourceProvider<Application> applicationProvider = Mock(ResourceProvider) {
    getAllRestricted(*_) >> []
  }

  @Shared
  List<ResourceProvider> resourceProviders = [accountProvider,
                                              applicationProvider,
                                              serviceAccountProvider]

  def "should resolve the anonymous user permission, when enabled"() {
    setup:
    @Subject DefaultPermissionsResolver resolver = new DefaultPermissionsResolver(
            userRolesProvider, serviceAccountProvider, resourceProviders, new FiatAdminConfig(), new ObjectMapper())

    when:
    def result = resolver.resolveUnrestrictedUser()

    then:
    1 * userRolesProvider.loadUnrestrictedRoles() >> { return [new Role("anonymous")] }

    def expected = new UserPermission().setId("__unrestricted_user__")
                                       .setAccounts([noReqGroupsAcct, anonymousRead] as Set)
                                       .setRoles([anonymous] as Set)
    result == expected
  }

  def "should resolve a single user's permissions"() {
    setup:
    def testUserId = "testUserId"
    UserRolesProvider userRolesProvider = Mock(UserRolesProvider)
    @Subject DefaultPermissionsResolver resolver = new DefaultPermissionsResolver(
            userRolesProvider, serviceAccountProvider, resourceProviders, new FiatAdminConfig(), new ObjectMapper())

    def role1 = new Role("group1")
    def role2 = new Role("gRoUP2") // to test case insensitivity.

    def testUser = new ExternalUser().setId(testUserId).setExternalRoles([role1])

    when:
    resolver.resolve(null as String)

    then:
    thrown IllegalArgumentException

    when:
    def result = resolver.resolve("unknownUser")

    then:
    1 * userRolesProvider.loadRoles({ u -> u.getId() == "unknownUser" }) >> []
    def expected = new UserPermission().setId("unknownUser")
    result == expected

    when:
    result = resolver.resolve(testUserId)
    expected = new UserPermission().setId(testUserId)

    then:
    1 * userRolesProvider.loadRoles({ u -> u.getId() == testUserId }) >> []
    result == expected

    when:
    result = resolver.resolve(testUserId)

    then:
    1 * userRolesProvider.loadRoles({ u -> u.getId() == testUserId }) >> [role2]
    expected.setAccounts([reqGroup1and2Acct] as Set)
            .setServiceAccounts([group2SvcAcct] as Set)
            .setRoles([role2] as Set)
    result == expected

    when: "merge externally provided roles"
    result = resolver.resolveAndMerge(testUser)

    then:
    1 * userRolesProvider.loadRoles({ u -> u.getId() == testUserId }) >> [role2]
    expected.setAccounts([reqGroup1Acct, reqGroup1and2Acct] as Set)
            .setServiceAccounts([group1SvcAcct, group2SvcAcct] as Set)
            .setRoles([role1, role2] as Set)
    result == expected
  }

  def "user should be admin"() {
    FiatAdminConfig fiatAdminConfig = new FiatAdminConfig()
    fiatAdminConfig.getAdmin().getRoles().add("delivery-team")

    def testUserId = "testUserId"
    UserRolesProvider userRolesProvider = Mock(UserRolesProvider)

    @Subject DefaultPermissionsResolver resolver = new DefaultPermissionsResolver(
            userRolesProvider, serviceAccountProvider, resourceProviders, fiatAdminConfig, new ObjectMapper())

    def role1 = new Role("delivery-team")
    def testUser = new ExternalUser().setId(testUserId).setExternalRoles([role1])

    when:
    def result = resolver.resolveAndMerge(testUser)

    then:
    1 * userRolesProvider.loadRoles({ u -> u.getId() == testUserId }) >> [role1]
    def expected = new UserPermission().setId("testUserId")
    expected.setRoles([role1] as Set).setAdmin(true)
            .setServiceAccounts([group1SvcAcct, group2SvcAcct] as Set)
            .setAccounts([reqGroup1Acct, reqGroup1and2Acct, anonymousRead] as Set)
    result == expected

  }

  def "should resolve all user's permissions"() {
    setup:
    UserRolesProvider userRolesProvider = Mock(UserRolesProvider)
    @Subject DefaultPermissionsResolver resolver = new DefaultPermissionsResolver(
            userRolesProvider, serviceAccountProvider, resourceProviders, new FiatAdminConfig(), new ObjectMapper())

    def role1 = new Role("group1")
    def role2 = new Role("group2")

    def extUser1 = new ExternalUser().setId("user1")
    def extUser2 = new ExternalUser().setId("user2")

    when:
    resolver.resolve(null as Collection)

    then:
    thrown IllegalArgumentException

    when:
    1 * userRolesProvider.multiLoadRoles(_) >> [
        user1: [role1],
        user2: [role2],
    ]
    def result = resolver.resolve([extUser1, extUser2])

    then:
    def user1 = new UserPermission().setId("user1")
                                    .setAccounts([reqGroup1Acct, reqGroup1and2Acct] as Set)
                                    .setServiceAccounts([group1SvcAcct] as Set)
                                    .setRoles([role1] as Set)
    def user2 = new UserPermission().setId("user2")
                                    .setAccounts([reqGroup1and2Acct] as Set)
                                    .setServiceAccounts([group2SvcAcct] as Set)
                                    .setRoles([role2] as Set)
    result.remove("user1") == user1
    result.remove("user2") == user2
    result.isEmpty() // Confirm no other values present

    when:
    def extRole = new Role("extRole").setSource(Role.Source.EXTERNAL)
    def extUser3 = new ExternalUser().setId("user3").setExternalRoles([extRole])
    1 * userRolesProvider.multiLoadRoles(_) >> [
        "user3": [role1]
    ]
    result = resolver.resolve([extUser3])

    then:
    def user3 = new UserPermission().setId("user3")
                                    .setAccounts([reqGroup1Acct, reqGroup1and2Acct] as Set)
                                    .setServiceAccounts([group1SvcAcct] as Set)
                                    .setRoles([role1, extRole] as Set)
    result == ["user3": user3]
  }

  def "should resolve service account permissions"() {
    setup:
    UserRolesProvider userRolesProvider = Mock(UserRolesProvider)
    @Subject DefaultPermissionsResolver resolver = new DefaultPermissionsResolver(
            userRolesProvider, serviceAccountProvider, resourceProviders, new FiatAdminConfig(), new ObjectMapper())

    def role1 = new Role(group1SvcAcct.memberOf[0])
    def svc1 = new ExternalUser().setId(group1SvcAcct.name).setExternalRoles([role1])
    def extUser1 = new ExternalUser().setId("user1")

    when:
    0 * userRolesProvider.multiLoadRoles()
    def result = resolver.resolve([svc1])

    then:
    def expectedServiceAcct  = new UserPermission().setId("group1")
            .setAccounts([reqGroup1Acct, reqGroup1and2Acct] as Set)
            .setServiceAccounts([group1SvcAcct] as Set)
            .setRoles([role1] as Set)

    result.remove("group1") == expectedServiceAcct
    result.isEmpty()

    when:
    1 * userRolesProvider.multiLoadRoles([extUser1] as Collection) >> [
            user1: [role1]
    ]
    result = resolver.resolve([svc1, extUser1])

    then:
    def expectedUser1 = new UserPermission().setId("user1")
            .setAccounts([reqGroup1Acct, reqGroup1and2Acct] as Set)
            .setServiceAccounts([group1SvcAcct] as Set)
            .setRoles([role1] as Set)

    result.remove("group1") == expectedServiceAcct
    result.remove("user1") == expectedUser1
    result.isEmpty()
  }
}
