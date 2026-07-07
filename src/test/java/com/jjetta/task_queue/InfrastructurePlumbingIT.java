package com.jjetta.task_queue;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class InfrastructurePlumbingIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getRedisHost);
        registry.add("spring.data.redis.port", redis::getRedisPort);
    }

    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    void redisRoundTrip() {
        redisTemplate.opsForValue().set("plumbing-check", "ok");
        assertThat(redisTemplate.opsForValue().get("plumbing-check")).isEqualTo("ok");
    }
}
