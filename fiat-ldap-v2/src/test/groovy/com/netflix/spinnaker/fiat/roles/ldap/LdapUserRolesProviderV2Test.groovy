/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.fiat.roles.ldap

import com.netflix.spinnaker.fiat.config.LdapConfigV2
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.permissions.ExternalUser
import org.apache.commons.lang3.tuple.Pair
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.security.ldap.SpringSecurityLdapTemplate
import spock.lang.Specification
import spock.lang.Unroll

import java.text.MessageFormat

class LdapUserRolesProviderV2Test extends Specification {
    @Unroll
    void "loadRoles should return no roles for serviceAccounts when userSearchFilter present"() {
        given:
        def user = new ExternalUser(id: 'foo', externalRoles: [new Role(name: 'bar')])

        def configProps = baseConfigProps()
        configProps.groupSearchBase = groupSearchBase
        configProps.userSearchFilter = "notEmpty"

        def ldapTemplate = Mock(SpringSecurityLdapTemplate) {
            _ * searchForSingleEntry(*_) >> { throw new IncorrectResultSizeDataAccessException(1) }
        }

        def provider = new LdapUserRolesProviderV2(ldapTemplate, configProps)

        when:
        def roles = provider.loadRoles(user)

        then:
        roles == Collections.emptyList()

        where:
        groupSearchBase|_
        ""             |_
        "notEmpty"     |_
    }

    @Unroll
    void "loadRoles should return no roles for serviceAccounts when userSearchFilter absent"() {
        given:
        def user = new ExternalUser(id: 'foo', externalRoles: [new Role(name: 'bar')])

        def configProps = baseConfigProps()
        configProps.groupSearchBase = groupSearchBase

        def ldapTemplate = Mock(SpringSecurityLdapTemplate) {
            (0..1) * searchForSingleEntry(*_) >> { throw new IncorrectResultSizeDataAccessException(1) }
            (0..1) * searchForSingleAttributeValues(*_) >> new HashSet<>()
        }

        def provider = new LdapUserRolesProviderV2(ldapTemplate, configProps)

        when:
        def roles = provider.loadRoles(user)

        then:
        roles == Collections.emptyList()

        where:
        groupSearchBase|_
        ""             |_
        "notEmpty"     |_
    }

    void "multiLoadRoles should do only 2 ldap call"() {
        given:
        def users = [externalUser("user1"), externalUser("user2")]

        def userSearchFilter = "(uid={0})"

        def configProps = baseConfigProps()
                .setGroupSearchBase("notEmpty")
                .setUserSearchBase("userSearchBase")
                .setUserSearchFilter(userSearchFilter)

        def ldapTemplate = Mock(SpringSecurityLdapTemplate)
        def provider = new LdapUserRolesProviderV2(ldapTemplate, configProps)

        when:
        provider.multiLoadRoles(users)

        then:
        1*ldapTemplate.search(configProps.getGroupSearchBase(), _ as String, _) >> new ArrayList<>()

        1*ldapTemplate.search(configProps.getUserSearchBase(), _ as String, _) >> new ArrayList<>()
    }

    void "multiLoadRoles should construct one query for all users"() {
        given:
        def users = [externalUser("user1"), externalUser("user2")]

        def userSearchFilter = "(uid={0})"

        def configProps = baseConfigProps()
                .setGroupSearchBase("notEmpty")
                .setUserSearchBase("userSearchBase")
                .setUserSearchFilter(userSearchFilter)

        def ldapTemplate = Mock(SpringSecurityLdapTemplate)
        def provider = new LdapUserRolesProviderV2(ldapTemplate, configProps)

        when:
        provider.multiLoadRoles(users)

        then:
        1*ldapTemplate.search(configProps.getGroupSearchBase(), _ as String, _) >> new ArrayList<>()

        1*ldapTemplate.search(configProps.getUserSearchBase(), _ as String, _) >> { arguments ->
            def filter = arguments[1] as String

            assert filter.startsWith("(|") // all user search search queries should be combined by OR (|) operator
            assert filter.endsWith(")")

            users.forEach { user ->
                assert filter.contains(MessageFormat.format(userSearchFilter, user.getId()))
            }
            return new ArrayList<>()
        }
    }

    void "multiLoadRoles should use user login instead of LDAP attribute because them are case insensitive by default"() {
        given:
        def users = [externalUser("UsEr")]
        def roles = [new Role(name: 'bar')]

        def userSearchFilter = "(uid={0})"

        def configProps = baseConfigProps()
                .setGroupSearchBase("notEmpty")
                .setUserSearchBase("userSearchBase")
                .setUserSearchFilter(userSearchFilter)



        def ldapTemplate = Mock(SpringSecurityLdapTemplate) {
            1*search(*_) >> roles
            1*search(*_) >> [Pair.of("user", roles)]
        }
        def provider = new LdapUserRolesProviderV2(ldapTemplate, configProps)

        when:
        def result = provider.multiLoadRoles(users)

        then:
        users.forEach({ u -> result.containsKey(u.getId()) })
    }

    private static ExternalUser externalUser(String id) {
        return new ExternalUser().setId(id)
    }

    def baseConfigProps() {
        return new LdapConfigV2.ConfigProps(
                url: "ldap://monkeymachine:11389/dc=springframework,dc=org",
                managerDn: "manager",
                managerPassword: "password",
        )
    }
  
}
