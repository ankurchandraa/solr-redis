package com.sematext.solr.redis;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;

/**
 * ParserPlugin which builds a query parser basing on data stored in Redis.
 * <p> RedisQParserPlugin initiates connection with Redis and pass the connection
 * object to RedisQParser which is responsible for fetching data and building a
 * query.
 * <p>
 * Allowed parameters for the RedisQParserPlugin are:
 * <ul>
 * <li><b>command</b> - Redis command. Currently allowed: smembers, zrevrangebyscore. (required)</li>
 * <li><b>key</b> - Key used to fetch data from Redis. (required)</li>
 * <li><b>operator</b> - Operator which connects terms taken from Redis. Allowed values are "AND" and "OR".
 * Default operator is OR. (optional)</li>
 * <li><b>useAnalyzer</b> - Turns on and off query time analyzer true/false (optinal - default is true)</li>
 * </ul>
 * <p>
 * ZREVRANGEBYSCORE specific parameters: <br> <ul>
 * <li><b>min</b> - Minimum value of range. (optional)</li>
 * <li><b>key</b> - Maximum value of range. (required)</li>
 * </ul>
 * <p>Examples of usage: <br>
 * <ul>
 * <li>
 *  <code>{!redis command=smembers key=some_key}field</code>
 * </li>
 * <li>
 *  <code>{!redis command=zrevrangebyscore key=some_key min=1 max=1000}field</code>
 * </li>
 * </ul>
 * <br><p>
 * You should configure that query parser plugin in solrconfig.xml first
 * <br><br><code>
 *   &lt;queryParser name="redis" class="com.sematext.solr.redis.RedisQParserPlugin"&gt;<br>
 *   &nbsp;&nbsp;&lt;str name="host"&gt;localhost&lt;/str&gt;<br>
 *   &nbsp;&nbsp;&lt;str name="maxConnections"&gt;30&lt;/str&gt;<br>
 *   &nbsp;&nbsp;&lt;str name="retries"&gt;2&lt;/str&gt;<br>
 *   &lt;/queryParser&gt;<br>
 * </code>
 */
public class RedisQParserPlugin extends QParserPlugin {

  private static final Logger log = LoggerFactory.getLogger(RedisQParserPlugin.class);

  public static final String NAME = "redis";
  public static final String HOST_FIELD = "host";
  public static final String MAX_CONNECTIONS_FIELD = "maxConnections";
  public static final String RETRIES_FIELD = "retries";
  public static final String DATABASE_FIELD = "database";
  public static final String PASSWORD_FIELD = "password";
  public static final String TIMEOUT_FIELD = "timeout";

  public static final int DEFAULT_MAX_CONNECTIONS = 5;
  public static final int DEFAULT_RETRIES = 1;

  private JedisPool jedisConnectorPool;

  private int retriesNumber = DEFAULT_RETRIES;

  @Override
  public QParser createParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
    return new RedisQParser(qstr, localParams, params, req, jedisConnectorPool, retriesNumber);
  }

  @Override
  public void init(NamedList args) {
    if (args != null) {
      int maxConnections = DEFAULT_MAX_CONNECTIONS;
      int database = Protocol.DEFAULT_DATABASE;
      int timetout = Protocol.DEFAULT_TIMEOUT;
      String password = null;

      GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();

      Object maxConnectionsConfiguration = args.get(MAX_CONNECTIONS_FIELD);
      if (maxConnectionsConfiguration instanceof String) {
        maxConnections = Integer.parseInt((String) maxConnectionsConfiguration);
      }

      Object retriesConfiguration = args.get(RETRIES_FIELD);
      if (retriesConfiguration instanceof String) {
        retriesNumber = Integer.parseInt((String) retriesConfiguration);
      }

      Object timeoutConfiguration = args.get(TIMEOUT_FIELD);
      if (timeoutConfiguration instanceof String) {
        timetout = Integer.parseInt((String) timeoutConfiguration);
      }

      poolConfig.setMaxTotal(maxConnections);

      Object hostConfiguration = args.get(HOST_FIELD);
      if (hostConfiguration instanceof String) {
        String host = (String) hostConfiguration;
        log.info("Initializing RedisQParserPlugin with host: " + host);
        String[] hostAndPort = host.split(":");
        if (hostAndPort.length == 2) {
          jedisConnectorPool = new JedisPool(poolConfig, hostAndPort[0], Integer.parseInt(hostAndPort[1]),
                  timetout, password, database);
        } else {
          jedisConnectorPool = new JedisPool(poolConfig, host, Protocol.DEFAULT_PORT, timetout, password, database);
        }
      } else {
        log.error("Initialization of RedisQParserPlugin failed. No redis host configuration."
                + "Using default host: localhost");
        jedisConnectorPool = new JedisPool(poolConfig, HostAndPort.LOCALHOST_STR, Protocol.DEFAULT_PORT,
                timetout, password, database);
      }
    } else {
      log.error("Initialization of RedisQParserPlugin failed. No redis configuration."
              + "Using default host: localhost");
      jedisConnectorPool = new JedisPool(HostAndPort.LOCALHOST_STR);
    }
  }
}
