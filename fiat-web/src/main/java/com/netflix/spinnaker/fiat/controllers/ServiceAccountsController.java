/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.controllers;

import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.fiat.providers.ResourceProvider;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping(path = "/serviceAccounts")
public class ServiceAccountsController {
  private final ResourceProvider<ServiceAccount> serviceAccountResourceProvider;

  public ServiceAccountsController(ResourceProvider<ServiceAccount> serviceAccountResourceProvider) {
    this.serviceAccountResourceProvider = serviceAccountResourceProvider;
  }
  @RequestMapping(value = "/{serviceAccountName:.+}", method = RequestMethod.PUT)
  public Map<String, String> updateApplication(@PathVariable String serviceAccountName, @RequestBody @NonNull List<String> memberOf, HttpServletResponse response) {
    ServiceAccount serviceAccount = new ServiceAccount();
    serviceAccount.setName(serviceAccountName);
    serviceAccount.setMemberOf(memberOf);
    log.info("Updating serviceAccount {}", serviceAccountName);

    serviceAccountResourceProvider.addItem(serviceAccount);
    return Collections.singletonMap("status", "success");
  }
}
