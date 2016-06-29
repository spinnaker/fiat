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

package com.netflix.spinnaker.fiat.controllers

import com.netflix.spinnaker.fiat.permissions.PermissionsRepository
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Account
import spock.lang.Specification

class AuthorizeControllerSpec extends Specification {

  def "should get user from repo"() {
    setup:
      PermissionsRepository repository = Mock(PermissionsRepository)
      AuthorizeController controller = new AuthorizeController(permissionsRepository: repository)

    when:
      controller.getUserPermission("foo")

    then:
      thrown NotFoundException

    when:
      def foo = new UserPermission().setId("foo")
      repository.get("foo") >> foo
      def result = controller.getUserPermission("foo")

    then:
      result == foo
  }

  def "should get user's accounts from repo"() {
    setup:
      PermissionsRepository repository = Mock(PermissionsRepository)
      AuthorizeController controller = new AuthorizeController(permissionsRepository: repository)

    when:
      controller.getUserAccounts("foo")

    then:
      thrown NotFoundException

    when:
      def bar = new Account().setName("bar")
      def foo = new UserPermission().setId("foo").setAccounts([bar] as Set)
      repository.get("foo") >> foo
      def result = controller.getUserAccounts("foo")

    then:
      result == [bar] as Set
  }

  def "should get user's accounts by name from repo"() {
    setup:
      PermissionsRepository repository = Mock(PermissionsRepository)
      AuthorizeController controller = new AuthorizeController(permissionsRepository: repository)

    when:
      controller.getUserAccount("foo", "bar")

    then:
      thrown NotFoundException

    when:
      def bar = new Account().setName("bar")
      def foo = new UserPermission().setId("foo").setAccounts([bar] as Set)
      repository.get("foo") >> foo
      def result = controller.getUserAccount("foo", "bar")

    then:
      result == bar
  }
}
