package com.firstin.dashboard.ingest;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Quarantines poison records to {@code job-leads.dlq} with failure headers.
 * A malformed record can never crash the consumer loop — the batch continues.
 */
@Component
public class DlqPublisher {

    private static final Logger log = LoggerFactory.getLogger(DlqPublisher.class);

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final String dlqTopic;
    private final AtomicLong quarantined = new AtomicLong();

    public DlqPublisher(KafkaTemplate<String, byte[]> kafkaTemplate,
                        @Value("${app.kafka.dlq-topic:job-leads.dlq}") String dlqTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.dlqTopic = dlqTopic;
    }

    public void publish(byte[] rawBytes, String originalTopic, String failureReason) {
        Message<byte[]> message = MessageBuilder.withPayload(rawBytes == null ? new byte[0] : rawBytes)
                .setHeader(KafkaHeaders.TOPIC, dlqTopic)
                .setHeader("failure-reason", failureReason)
                .setHeader("original-topic", originalTopic)
                .build();
        kafkaTemplate.send(message);
        long n = quarantined.incrementAndGet();
        // Never log the raw bytes: they may contain recruiter PII.
        log.warn("Quarantined poison record to {} ({} bytes, reason: {}). Total quarantined: {}",
                dlqTopic, rawBytes == null ? 0 : rawBytes.length, failureReason, n);
    }

    public long quarantinedCount() {
        return quarantined.get();
    }
}
