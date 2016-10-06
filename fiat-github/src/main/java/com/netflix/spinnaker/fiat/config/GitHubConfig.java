package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.fiat.roles.github.GitHubProperties;
import com.netflix.spinnaker.fiat.roles.github.client.GitHubClient;
import com.netflix.spinnaker.fiat.roles.github.client.GitHubMaster;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoints;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

import javax.validation.Valid;

/**
 * Converts the list of GitHub Configuration properties a collection of clients to access the GitHub hosts
 */
@Configuration
@ConditionalOnProperty(value = "auth.groupMembership.service", havingValue = "github")
@Slf4j
public class GitHubConfig {

  @Autowired
  @Setter
  private OkClient okClient;

  @Autowired
  @Setter
  private RestAdapter.LogLevel retrofitLogLevel;

  @Bean
  public GitHubMaster gitHubMasters(@Valid final GitHubProperties gitHubProperties) {
    log.info("bootstrapping " + gitHubProperties.getBaseUrl() + " as github");

    return new GitHubMaster()
        .setGitHubClient(gitHubClient(gitHubProperties.getBaseUrl(),
                                      gitHubProperties.getAccessToken()))
        .setBaseUrl(gitHubProperties.getBaseUrl());
  }

  private GitHubClient gitHubClient(String address, String accessToken) {
    BasicAuthRequestInterceptor interceptor = new BasicAuthRequestInterceptor();

    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(address))
        .setRequestInterceptor(interceptor.setAccessToken(accessToken))
        .setClient(okClient)
        .setConverter(new JacksonConverter())
        .setLogLevel(retrofitLogLevel)
        .setLog(new Slf4jRetrofitLogger(GitHubClient.class))
        .build()
        .create(GitHubClient.class);
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
      logger.info(message);
    }
  }

  private static class BasicAuthRequestInterceptor implements RequestInterceptor {

    @Getter
    @Setter
    private String accessToken;

    @Override
    public void intercept(RequestFacade request) {
      request.addQueryParam("access_token", accessToken);
    }
  }
}
