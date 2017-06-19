package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.cats.redis.JedisPoolSource;
import com.netflix.spinnaker.cats.redis.JedisSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;

import java.lang.reflect.Field;
import java.net.URI;

@Slf4j
@Configuration
@ConditionalOnProperty("redis.connection")
public class RedisConfig {

  @Bean
  public JedisSource jedisSource(JedisPool jedisPool) {
    return new JedisPoolSource(jedisPool);
  }

  @Bean
  @ConfigurationProperties("redis")
  public GenericObjectPoolConfig redisPoolConfig() {
    GenericObjectPoolConfig config = new GenericObjectPoolConfig();
    config.setMaxTotal(20);
    config.setMaxIdle(20);
    config.setMinIdle(5);
    return config;
  }

  @Bean
  public JedisPool jedisPool(@Value("${redis.connection:redis://localhost:6379}") String connection,
                             @Value("${redis.timeout:2000}") int timeout,
                             GenericObjectPoolConfig redisPoolConfig) {
    return createPool(redisPoolConfig, connection, timeout);
  }

  private static JedisPool createPool(GenericObjectPoolConfig redisPoolConfig,
                                      String connection,
                                      int timeout) {
    URI redisConnection = URI.create(connection);

    String host = redisConnection.getHost();
    int port = redisConnection.getPort() == -1 ? Protocol.DEFAULT_PORT : redisConnection.getPort();

    String path = redisConnection.getPath();
    if (StringUtils.isEmpty(path)) {
      path = "/" + String.valueOf(Protocol.DEFAULT_DATABASE);
    }
    int database = Integer.parseInt(path.split("/", 2)[1]);

    String password = null;
    if (redisConnection.getUserInfo() != null) {
      password = redisConnection.getUserInfo().split(":", 2)[1];
    }

    if (redisPoolConfig == null) {
      redisPoolConfig = new GenericObjectPoolConfig();
    }

    return new JedisPool(redisPoolConfig, host, port, timeout, password, database, null);
  }

  @Bean
  public HealthIndicator redisHealth(final JedisPool jedisPool) {
    return new HealthIndicator() {
      @Override
      public Health health() {
        Health.Builder health;
        try (Jedis jedis = jedisPool.getResource()) {
          if ("PONG".equals(jedis.ping())) {
            health = Health.up();
          } else {
            health = Health.down();
          }
        } catch (Exception ex) {
          health = Health.down(ex);
        }

        try {
          Field f = FieldUtils.getField(JedisPool.class, "internalPool", true /*forceAccess*/);
          Object o = FieldUtils.readField(f, jedisPool, true /*forceAccess*/);
          if (o instanceof GenericObjectPool) {
            GenericObjectPool internal = (GenericObjectPool) o;
            health.withDetail("maxIdle", internal.getMaxIdle());
            health.withDetail("minIdle", internal.getMinIdle());
            health.withDetail("numActive", internal.getNumActive());
            health.withDetail("numIdle", internal.getNumIdle());
            health.withDetail("numWaiters", internal.getNumWaiters());
          }
        } catch (IllegalAccessException iae) {
          log.debug("Can't access jedis' internal pool", iae);
        }

        return health.build();
      }
    };
  }
}
