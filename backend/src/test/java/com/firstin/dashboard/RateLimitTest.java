package com.firstin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * QA: per-IP rate limiting on /api/** (Bucket4j, in-memory). Own context with
 * a tiny bucket so the test stays fast.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = {"job-leads.raw", "job-leads.dlq"})
@ActiveProfiles("test")
@TestPropertySource(properties = "app.rate-limit.per-minute=3")
class RateLimitTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void overLimitGets429WithGenericBody() {
        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> ok = rest.getForEntity("/api/health", String.class);
            assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        ResponseEntity<String> limited = rest.getForEntity("/api/health", String.class);
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getBody()).contains("\"error\"");
    }
}
