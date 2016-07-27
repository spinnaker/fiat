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

package com.netflix.spinnaker.fiat.providers;

import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverService;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DefaultAccountProvider implements AccountProvider {

  @Autowired
  @Setter
  private ClouddriverService clouddriverService;

  public Set<Account> getAccounts(@NonNull Collection<String> groups) {
    try {
      return clouddriverService
          .getAccounts()
          .stream()
          .filter(account ->
                      account.getRequiredGroupMembership().isEmpty() ||
                          !Collections.disjoint(account.getRequiredGroupMembership(), groups))
          .collect(Collectors.toSet());
    } catch (RetrofitError re) {
      throw new ProviderException(re);
    }
  }
}
