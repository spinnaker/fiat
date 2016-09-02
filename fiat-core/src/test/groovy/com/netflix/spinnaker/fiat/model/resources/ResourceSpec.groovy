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

package com.netflix.spinnaker.fiat.model.resources

import spock.lang.Specification

class ResourceSpec extends Specification {

  def "should parse resource type from Redis key"() {
    expect:
    ResourceType.parse(input) == output

    where:
    input              || output
    ":accounts"        || ResourceType.ACCOUNT
    "abc:accounts"     || ResourceType.ACCOUNT
    "abc:def:accounts" || ResourceType.ACCOUNT
    ":applications"    || ResourceType.APPLICATION
    "account"          || ResourceType.ACCOUNT
    "accounts"         || ResourceType.ACCOUNT
    "aCCoUnTs"         || ResourceType.ACCOUNT
  }

  def "should throw exception on invalid parse input"() {
    when:
    ResourceType.parse(input)

    then:
    thrown e

    where:
    input       || e
    null        || IllegalArgumentException.class
    ""          || IllegalArgumentException.class
    "account:"  || IllegalArgumentException.class
    "account:s" || IllegalArgumentException.class
  }
}
