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
import org.apache.commons.io.FileUtils
import spock.lang.Specification
import spock.lang.Subject

class FileBasedApplicationProviderSpec extends Specification {

  @Subject FileBasedApplicationProvider provider

  def cleanup() {
    if (provider) {
      provider.close()
    }
  }

  def "should get all accounts based on supplied roles"() {
    setup:
    provider = new FileBasedApplicationProvider([
        "noReqGroups"  : new Application().setName("noReqGroups"),
        "reqGroup1"    : new Application().setName("reqGroup1").setRequiredGroupMembership(["group1"]),
        "reqGroup1and2": new Application().setName("reqGroup1and2").setRequiredGroupMembership(["group1", "group2"])
    ]);

    when:
    def result = provider.getApplications(input)

    then:
    result*.name.containsAll(values)

    when:
    provider.getApplications(null)

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

  def "should watch config file directory"() {
    setup:
    String config1Content = """
name: abc
requiredGroupMembership:
- abcGroup
"""
    String config2Content = """
name: def
"""

    File configFile1 = File.createTempFile("abc", ".config")
    configFile1.deleteOnExit()
    FileUtils.write(configFile1, config1Content, "UTF-8", false /* append */)

    Map applicationMap = [:]
    provider = new FileBasedApplicationProvider(applicationMap)

    when:
    provider.watch(configFile1.getParentFile().getAbsolutePath())
    sleep(250)

    then:
    applicationMap["abc"] == new Application().setName("abc").setRequiredGroupMembership(["abcGroup"])

    when:
    File configFile2 = File.createTempFile("abc", ".config")
    configFile2.deleteOnExit()
    FileUtils.write(configFile2, config2Content, "UTF-8", false /* append */)
    sleep(250)

    then:
    applicationMap["def"] == new Application().setName("def")
  }
}
