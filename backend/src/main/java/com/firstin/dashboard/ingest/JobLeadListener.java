package com.firstin.dashboard.ingest;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for {@code job-leads.raw} (group {@code firstin-ingest}).
 * There is intentionally NO HTTP ingest endpoint — records enter only
 * through this consumer (threat model: no public ingestion trigger).
 *
 * <p>The daily pipeline stamps data records with a {@code run_id} header and
 * finishes each run with a run-complete control record
 * ({@code run_id} + {@code run_complete=true}). The control record carries no
 * lead payload, so it is intercepted before JSON parsing.
 */
@Component
public class JobLeadListener {

    private static final Logger log = LoggerFactory.getLogger(JobLeadListener.class);

    private final IngestionService ingestionService;
    private final IngestRunRecorder runRecorder;

    public JobLeadListener(IngestionService ingestionService, IngestRunRecorder runRecorder) {
        this.ingestionService = ingestionService;
        this.runRecorder = runRecorder;
    }

    @KafkaListener(
            topics = "${app.kafka.raw-topic:job-leads.raw}",
            groupId = "firstin-ingest",
            containerFactory = "byteArrayKafkaListenerContainerFactory")
    public void listen(ConsumerRecord<String, byte[]> record) {
        log.debug("Received record from {} ({} bytes)", record.topic(),
                record.value() == null ? 0 : record.value().length);
        String runId = headerValue(record, "run_id");
        if ("true".equalsIgnoreCase(headerValue(record, "run_complete")) && runId != null) {
            runRecorder.completeRun(runId);
            log.info("Run {} marked COMPLETED", runId);
            return;
        }
        ingestionService.ingestRaw(record.value(), record.topic(), runId);
    }

    /** Kafka headers are UTF-8; a missing header reads as null. */
    private static String headerValue(ConsumerRecord<String, byte[]> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
