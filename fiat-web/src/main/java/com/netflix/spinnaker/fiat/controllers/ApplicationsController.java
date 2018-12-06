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

import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.providers.ResourceProvider;
import com.netflix.spinnaker.fiat.providers.internal.Front50Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/applications")
public class ApplicationsController {

  private final ResourceProvider<Application> applicationProvider;

  private final Front50Service front50Service;

  public ApplicationsController(ResourceProvider<Application> applicationProvider, Front50Service front50Service) {
    this.applicationProvider = applicationProvider;
    this.front50Service = front50Service;
  }

  @RequestMapping(value = "/{applicationName:.+}", method = RequestMethod.PUT)
  public ResponseEntity<Map<String, String>> updateApplication(@PathVariable String applicationName) {
    log.info("Updating application {}", applicationName);
    Application app = front50Service.getApplicationPermissions(applicationName.toLowerCase());
    if (app == null) {
      return getResponseEntity("failure", HttpStatus.CONFLICT);
    }
    log.info("some info {}", app.getName());
    applicationProvider.addItem(app);
    return getResponseEntity("success", HttpStatus.CREATED);

  }

  private ResponseEntity<Map<String,String>> getResponseEntity(String status, HttpStatus httpStatus) {
    Map<String, String> body = Collections.singletonMap("status", status);
    return new ResponseEntity(body, httpStatus);
  }

}
