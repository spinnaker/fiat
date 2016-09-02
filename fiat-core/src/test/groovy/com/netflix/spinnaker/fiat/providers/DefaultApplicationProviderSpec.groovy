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

package com.netflix.spinnaker.fiat.providers

import com.netflix.spinnaker.fiat.model.resources.Application
import com.netflix.spinnaker.fiat.providers.internal.Front50Service
import org.apache.commons.io.FileUtils
import spock.lang.Specification
import spock.lang.Subject

class DefaultApplicationProviderSpec extends Specification {

  @Subject DefaultApplicationProvider provider

  def "should get all accounts based on supplied roles"() {
    setup:
    Front50Service front50Service = Mock(Front50Service) {
      getAllApplicationPermissions() >> [
          new Application().setName("noReqGroups"),
          new Application().setName("reqGroup1").setRequiredGroupMembership(["group1"]),
          new Application().setName("reqGroup1and2").setRequiredGroupMembership(["group1", "group2"])
      ]
    }
    provider = new DefaultApplicationProvider(front50Service: front50Service)

    when:
    def result = provider.getAll(input)

    then:
    result*.name.containsAll(values)

    when:
    provider.getAll(null)

    then:
    thrown IllegalArgumentException

    where:
    input                || values
    []                   || ["noReqGroups"]
    ["group1"]           || ["noReqGroups", "reqGroup1", "reqGroup1and2"]
    ["group2"]           || ["noReqGroups", "reqGroup1and2"]
    ["group1", "group2"] || ["noReqGroups", "reqGroup1", "reqGroup1and2"]
    ["group3"]           || ["noReqGroups"]
    ["group2", "group3"] || ["noReqGroups", "reqGroup1and2"]
  }
}
