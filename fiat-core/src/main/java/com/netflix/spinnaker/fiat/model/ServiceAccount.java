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

package com.netflix.spinnaker.fiat.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.fiat.model.resources.Named;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class ServiceAccount implements Named {
  private String name;

  @JsonIgnore
  public String getNameWithoutDomain() {
    return StringUtils.substringBefore(name, "@");
  }

  @JsonIgnore
  public View getView() {
    return new View();
  }

  @Data
  public class View {
    String name = ServiceAccount.this.name;
  }
}
