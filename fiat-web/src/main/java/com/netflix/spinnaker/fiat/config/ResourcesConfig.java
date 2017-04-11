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

package com.netflix.spinnaker.fiat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverApi;
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverService;
import com.netflix.spinnaker.fiat.providers.internal.Front50Api;
import com.netflix.spinnaker.fiat.providers.internal.Front50Service;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoints;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

@Configuration
@EnableConfigurationProperties(ProviderCacheConfig.class)
public class ResourcesConfig {
  @Autowired
  @Setter
  private RestAdapter.LogLevel retrofitLogLevel;

  @Autowired
  @Setter
  private ObjectMapper objectMapper;

  @Autowired
  @Setter
  private OkClient okClient;

  @Value("${services.front50.baseUrl}")
  @Setter
  private String front50Endpoint;

  @Value("${services.clouddriver.baseUrl}")
  @Setter
  private String clouddriverEndpoint;

  @Bean
  Front50Api front50Api() {
    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(front50Endpoint))
        .setClient(okClient)
        .setConverter(new JacksonConverter(objectMapper))
        .setLogLevel(retrofitLogLevel)
        .setLog(new Slf4jRetrofitLogger(Front50Api.class))
        .build()
        .create(Front50Api.class);
  }

  @Bean
  Front50Service front50Service(Front50Api front50Api) {
    return new Front50Service(front50Api);
  }

  @Bean
  ClouddriverApi clouddriverApi() {
    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(clouddriverEndpoint))
        .setClient(okClient)
        .setConverter(new JacksonConverter(objectMapper))
        .setLogLevel(retrofitLogLevel)
        .setLog(new Slf4jRetrofitLogger(ClouddriverApi.class))
        .build()
        .create(ClouddriverApi.class);
  }

  @Bean
  ClouddriverService clouddriverService(ClouddriverApi clouddriverApi) {
    return new ClouddriverService(clouddriverApi);
  }

  private static class Slf4jRetrofitLogger implements RestAdapter.Log {
    private final Logger logger;

    Slf4jRetrofitLogger(Class type) {
      this(LoggerFactory.getLogger(type));
    }

    Slf4jRetrofitLogger(Logger logger) {
      this.logger = logger;
    }

    @Override
    public void log(String message) {
      logger.debug(message);
    }
  }
}
