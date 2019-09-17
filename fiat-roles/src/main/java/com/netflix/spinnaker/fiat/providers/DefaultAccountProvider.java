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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DefaultAccountProvider extends BaseProvider<Account>
    implements ResourceProvider<Account> {

  private final ClouddriverService clouddriverService;

  public DefaultAccountProvider(ClouddriverService clouddriverService) {
    this(clouddriverService, Collections.emptyList());
  }

  @Autowired
  public DefaultAccountProvider(
      ClouddriverService clouddriverService, List<ResourceInterceptor> resourceInterceptors) {
    super(resourceInterceptors);
    this.clouddriverService = clouddriverService;
  }

  @Override
  protected Set<Account> loadAll() throws ProviderException {
    try {
      return new HashSet<>(clouddriverService.getAccounts());
    } catch (Exception e) {
      throw new ProviderException(this.getClass(), e.getCause());
    }
  }
}
