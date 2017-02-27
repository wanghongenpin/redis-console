package com.whe.test;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.Test;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import redis.clients.jedis.*;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Created by trustme on 2017/2/12.
 * Test
 */
public class RedisTest {
    public static void main(String[] args) {
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxIdle(20);
        poolConfig.setMaxTotal(20);
        poolConfig.setMinIdle(10);
        JedisPool jedisPool = new JedisPool(poolConfig, "192.168.88.128", 6379, 2000);

        long l = System.currentTimeMillis();

        IntStream.rangeClosed(0, 2000).parallel().forEach(i -> {
            Jedis jedis = jedisPool.getResource();
            jedis.select(1);
            jedis.zadd("zSet",i,"val"+i);
            // jedis.del("key" + i);
//            jedis.set("key" + i, "val" + i);
            System.out.println(i);
            jedis.close();
        });
        System.out.println("插入耗时:" + (System.currentTimeMillis() - l));
    }
    @Test
    public void clusterTest2(){
        Set<RedisNode> set=new HashSet<>();
        for(int i=6380;i<6386;i++){
            set.add(new RedisNode("192.168.88.128",i));
        }
        RedisClusterConfiguration redisClusterConfiguration=new RedisClusterConfiguration();
        redisClusterConfiguration.setClusterNodes(set);
        JedisConnectionFactory jedisConnectionFactory=new JedisConnectionFactory(redisClusterConfiguration);

        RedisTemplate redisTemplate=new RedisTemplate();
        redisTemplate.setConnectionFactory( jedisConnectionFactory);
    }
    @Test
    public void clusterTest(){
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxIdle(20);
        poolConfig.setMaxTotal(20);
        poolConfig.setMinIdle(10);
        Set<HostAndPort> set=new HashSet<>();
        for(int i=6385;i<6386;i++){
            set.add(new HostAndPort("192.168.88.128",i));
        }
        JedisCluster jedisCluster=new JedisCluster(set,poolConfig);
        IntStream.rangeClosed(0, 100).forEach(i -> {
           jedisCluster.zadd("zSet",i,"val"+i);
           // jedisCluster.del("key" + i);
         //  jedisCluster.set("key" + i, "val" + i);
            System.out.println(i);
        });
    }
    @Test
    public void test1() {
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxIdle(20);
        poolConfig.setMaxTotal(20);
        poolConfig.setMinIdle(10);
        JedisPool jedisPool = new JedisPool(poolConfig, "192.168.88.128", 6379, 2000);
        long l = System.currentTimeMillis();
        Jedis jedis = jedisPool.getResource();

        for (int i = 0; i < 100000; i++) {
            jedis.set("key" + i, "val" + i);
            // jedis.del("key"+i);
            System.out.println(i);
        }
        jedis.close();
        System.out.println("插入耗时:" + (System.currentTimeMillis() - l));
    }
}