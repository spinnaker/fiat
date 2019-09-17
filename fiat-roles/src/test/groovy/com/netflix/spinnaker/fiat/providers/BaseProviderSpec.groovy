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

import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.fiat.model.resources.Resource
import com.netflix.spinnaker.fiat.model.resources.ResourceType
import com.netflix.spinnaker.fiat.model.resources.Role
import groovy.transform.EqualsAndHashCode
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import spock.lang.Specification
import spock.lang.Subject

class BaseProviderSpec extends Specification {

  private static Authorization R = Authorization.READ
  private static Authorization W = Authorization.WRITE

  TestResource noReqGroups
  TestResource reqGroup1
  TestResource reqGroup1and2

  def setup() {
    noReqGroups = new TestResource()
      .setName("noReqGroups")
    reqGroup1 = new TestResource()
      .setName("reqGroup1")
      .setPermissions(new Permissions.Builder().add(R, "group1").build())
    reqGroup1and2 = new TestResource()
      .setName("reqGroup1and2")
      .setPermissions(new Permissions.Builder().add(R, "group1")
        .add(W, "group2")
        .build())
  }

  def "should get all unrestricted"() {
    setup:
    @Subject provider = new TestResourceProvider()

    when:
    provider.testData = [noReqGroups]
    def result = provider.getAllUnrestricted()

    then:
    result.size() == 1
    def expected = noReqGroups
    result.first() == expected

    when:
    provider.testData = [reqGroup1]
    provider.clearCache()
    result = provider.getAllUnrestricted()

    then:
    result.isEmpty()
  }

  def "should get restricted"() {
    setup:
    @Subject provider = new TestResourceProvider()

    when:
    provider.testData = [noReqGroups]
    def result = provider.getAllRestricted([new Role("group1")] as Set, false)

    then:
    result.isEmpty()

    when:
    provider.testData = [reqGroup1]
    provider.clearCache()
    result = provider.getAllRestricted([new Role("group1")] as Set, false)

    then:
    result.size() == 1
    result.first() == reqGroup1

    when:
    provider.testData = [reqGroup1and2]
    provider.clearCache()
    result = provider.getAllRestricted([new Role("group1")] as Set, false)

    then:
    result.size() == 1
    result.first() == reqGroup1and2

    when: "use additional groups that grants additional authorizations."
    result = provider.getAllRestricted([new Role("group1"), new Role("group2")] as Set, false)

    then:
    result.size() == 1
    result.first() == reqGroup1and2

    when:
    provider.getAllRestricted(null, false)

    then:
    thrown IllegalArgumentException
  }

  def "should retain only relevant ResourceInterceptors"() {
    expect:
    new TestResourceProvider(providedInterceptors).inspectResourceInterceptors()*.class == expectedInterceptors

    where:

    providedInterceptors                                               || expectedInterceptors
    null                                                               || []
    []                                                                 || []
    [new NoopResourceInterceptor()]                                    || []
    [new NoopResourceInterceptor(), new ReadOnlyResourceInterceptor()] || [ReadOnlyResourceInterceptor]
    [new ReadOnlyResourceInterceptor()]                                || [ReadOnlyResourceInterceptor]
  }

  def "should apply resource interceptors when loading cache data"() {
    given:
    def provider = new TestResourceProvider([new ReadOnlyResourceInterceptor()])
    provider.testData = [reqGroup1and2]

    when:
    Set<TestResource> resources = provider.getAll()

    then:
    resources.size() == 1
    resources[0].name == "reqGroup1and2"
    resources[0].permissions.getAuthorizations(["group1", "group2"]) == [Authorization.READ] as Set
    !resources[0].permissions.get(Authorization.WRITE)
    !resources[0].permissions.get(Authorization.EXECUTE)
  }

  class ReadOnlyResourceInterceptor implements ResourceInterceptor {
    @Override
    boolean supports(Class<? extends Resource> type) {
      return type == TestResource
    }

    @Override
    <R extends Resource> Set<R> intercept(Set<R> original) {
      original.each { TestResource resource ->
        if (!resource.permissions.isEmpty()) {
          resource.permissions = new Permissions.Builder().add(Authorization.READ, resource.permissions.get(Authorization.READ)).build()
        }
      }
      return original
    }
  }

  class TestResourceProvider extends BaseProvider<TestResource> {

    TestResourceProvider() {
      this(Collections.emptyList());
    }

    TestResourceProvider(List<ResourceInterceptor> resourceInterceptors) {
      super(resourceInterceptors)
    }

    Set<TestResource> testData = new HashSet<>()

    @Override
    protected Set<TestResource> loadAll() throws ProviderException {
      return testData
    }
  }

  @Builder(builderStrategy = SimpleStrategy, prefix = "set")
  @EqualsAndHashCode
  class TestResource implements Resource.AccessControlled {
    final ResourceType resourceType = ResourceType.APPLICATION // Irrelevant for testing.
    String name
    Permissions permissions = Permissions.EMPTY
  }
}
