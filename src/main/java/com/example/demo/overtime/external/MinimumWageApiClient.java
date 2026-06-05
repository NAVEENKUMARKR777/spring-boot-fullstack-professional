package com.example.demo.overtime.external;

import com.example.demo.worker.Designation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * LF-205 fix: This client is called OUTSIDE any @Transactional boundary in OvertimeService.
 *
 * The original bug: the external call sat inside a @Transactional method, holding a DB
 * connection for 3-5 seconds while waiting on this API. With a pool of 10, 20 concurrent
 * requests would exhaust the pool. Fix: fetch this data first, then enter the transaction.
 *
 * The RestTemplate is configured with connect + read timeouts (see AppConfig) so a slow
 * government API cannot freeze the application.
 */
@Component
@Slf4j
public class MinimumWageApiClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public MinimumWageApiClient(
            RestTemplate minimumWageRestTemplate,
            @Value("${app.government.api.base-url}") String baseUrl) {
        this.restTemplate = minimumWageRestTemplate;
        this.baseUrl      = baseUrl;
    }

    public BigDecimal fetchMinimumWage(Designation designation) {
        try {
            String url      = baseUrl + "?designation=" + designation.name();
            MinimumWageDto response = restTemplate.getForObject(url, MinimumWageDto.class);
            if (response != null && response.getDailyRate() != null) {
                return response.getDailyRate();
            }
        } catch (RestClientException e) {
            log.warn("Government minimum-wage API unreachable ({}): {} — using fallback rate",
                    designation, e.getMessage());
        }
        // Fallback: return a default minimum daily wage if the API is unavailable
        return BigDecimal.valueOf(600);
    }

    // Inner DTO for the government API response
    static class MinimumWageDto {
        private BigDecimal dailyRate;
        public BigDecimal getDailyRate() { return dailyRate; }
        public void setDailyRate(BigDecimal dailyRate) { this.dailyRate = dailyRate; }
    }
}
