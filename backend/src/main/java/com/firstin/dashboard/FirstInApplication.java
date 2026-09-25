package com.firstin.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;

/**
 * Kafka is wired explicitly in {@link com.firstin.dashboard.config.KafkaConfig}
 * (raw-byte consumer so poison records can be quarantined); Boot's default
 * Kafka auto-configuration stays out of the way.
 */
@SpringBootApplication(exclude = KafkaAutoConfiguration.class)
public class FirstInApplication {

    public static void main(String[] args) {
        SpringApplication.run(FirstInApplication.class, args);
    }
}
