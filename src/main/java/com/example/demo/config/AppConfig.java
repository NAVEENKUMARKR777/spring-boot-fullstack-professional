package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * LF-205: The RestTemplate used for the government minimum-wage API must have
 * explicit connect and read timeouts. Without them, a slow or unresponsive
 * external API will block a thread (and a DB connection if called inside
 * @Transactional) indefinitely, causing pool exhaustion under load.
 */
@Configuration
public class AppConfig {

    @Value("${app.government.api.timeout-ms:5000}")
    private int apiTimeoutMs;

    @Bean(name = "minimumWageRestTemplate")
    public RestTemplate minimumWageRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(apiTimeoutMs);
        factory.setReadTimeout(apiTimeoutMs);
        return new RestTemplate(factory);
    }
}
