package com.netflix.spinnaker.fiat.config;

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.telemetry.InstrumentedJedisPool;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.*;

import java.net.URI;

@Slf4j
@Configuration
@ConditionalOnProperty("redis.connection")
public class RedisConfig {
  @Bean
  @ConfigurationProperties("redis")
  public GenericObjectPoolConfig redisPoolConfig() {
    GenericObjectPoolConfig config = new JedisPoolConfig();
    config.setMaxTotal(20);
    config.setMaxIdle(20);
    config.setMinIdle(5);
    return config;
  }

  @Bean
  public JedisPool jedisPool(@Value("${redis.connection:redis://localhost:6379}") String connection,
                             @Value("${redis.timeout:2000}") int timeout,
                             Registry registry,
                             GenericObjectPoolConfig redisPoolConfig) {
    final JedisPool jedisPool = createPool(registry, redisPoolConfig, connection, timeout);
    if (jedisPool instanceof InstrumentedJedisPool) {
      final GenericObjectPool pool = ((InstrumentedJedisPool) jedisPool).getInternalPoolReference();
      PolledMeter.using(registry).withName("jedis.pool.maxIdle").monitorValue(pool, GenericObjectPool::getMaxIdle);
      PolledMeter.using(registry).withName("jedis.pool.minIdle").monitorValue(pool, GenericObjectPool::getMinIdle);
      PolledMeter.using(registry).withName("jedis.pool.numActive").monitorValue(pool, GenericObjectPool::getNumActive);
      PolledMeter.using(registry).withName("jedis.pool.numIdle").monitorValue(pool, GenericObjectPool::getNumIdle);
      PolledMeter.using(registry).withName("jedis.pool.numWaiters").monitorValue(pool, GenericObjectPool::getNumWaiters);
    }

    return jedisPool;
  }

  @Bean
  RedisClientDelegate redisClientDelegate(JedisPool jedisPool) {
    return new JedisClientDelegate(jedisPool);
  }

  private static JedisPool createPool(Registry registry,
                                      GenericObjectPoolConfig redisPoolConfig,
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

    redisPoolConfig = redisPoolConfig != null ? redisPoolConfig : new GenericObjectPoolConfig();
    JedisPool pool = new JedisPool(redisPoolConfig, host, port, timeout, password, database, null);
    return new InstrumentedJedisPool(registry, pool, "unnamed");
  }
}
