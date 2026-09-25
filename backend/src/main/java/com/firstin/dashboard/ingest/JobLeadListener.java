package com.firstin.dashboard.ingest;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for {@code job-leads.raw} (group {@code firstin-ingest}).
 * There is intentionally NO HTTP ingest endpoint — records enter only
 * through this consumer (threat model: no public ingestion trigger).
 */
@Component
public class JobLeadListener {

    private static final Logger log = LoggerFactory.getLogger(JobLeadListener.class);

    private final IngestionService ingestionService;

    public JobLeadListener(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @KafkaListener(
            topics = "${app.kafka.raw-topic:job-leads.raw}",
            groupId = "firstin-ingest",
            containerFactory = "byteArrayKafkaListenerContainerFactory")
    public void listen(ConsumerRecord<String, byte[]> record) {
        log.debug("Received record from {} ({} bytes)", record.topic(),
                record.value() == null ? 0 : record.value().length);
        ingestionService.ingestRaw(record.value(), record.topic());
    }
}
