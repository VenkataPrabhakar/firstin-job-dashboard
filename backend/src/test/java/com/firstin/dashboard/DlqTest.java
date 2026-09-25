package com.firstin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * QA (criterion 9): corrupt bytes → DLQ with failure headers, and the batch
 * continues — a valid record sent right after still ingests. Test methods
 * share the DLQ topic, so each test scans for its own record instead of
 * assuming it is first.
 */
class DlqTest extends AbstractIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    private Consumer<String, byte[]> dlqConsumer;

    @AfterEach
    void closeConsumer() {
        if (dlqConsumer != null) {
            dlqConsumer.close();
            dlqConsumer = null;
        }
    }

    @Test
    void corruptBytesGoToDlqWithHeadersAndBatchContinues() throws Exception {
        Consumer<String, byte[]> consumer = dlqConsumer("dlq-test-group-a");

        byte[] corrupt = "{ this is not json".getBytes(StandardCharsets.UTF_8);
        kafkaTemplate.send("job-leads.raw", corrupt).get();

        // A valid record immediately after — the batch must continue.
        String valid = """
                {"reported":true,"title":"Kafka Dev","company":"Acme","location":"Remote",
                 "engagement":"W2","source":"Dice"}""";
        kafkaTemplate.send("job-leads.raw", valid.getBytes(StandardCharsets.UTF_8)).get();

        ConsumerRecord<String, byte[]> dlqRecord = awaitRecord(consumer, "{ this is not json");
        assertThat(headerValue(dlqRecord, "failure-reason")).startsWith("deserialization failed");
        assertThat(headerValue(dlqRecord, "original-topic")).isEqualTo("job-leads.raw");

        // The valid record still made it through the same consumer loop.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(
                () -> assertThat(postings.count()).isEqualTo(1));
    }

    @Test
    void validationFailureGoesToDlq() {
        Consumer<String, byte[]> consumer = dlqConsumer("dlq-test-group-b");

        // Valid JSON but missing required title → validation failure.
        String invalid = """
                {"reported":true,"company":"Acme","location":"Remote","engagement":"W2"}""";
        kafkaTemplate.send("job-leads.raw", invalid.getBytes(StandardCharsets.UTF_8));

        ConsumerRecord<String, byte[]> dlqRecord = awaitRecord(consumer, "\"company\":\"Acme\"");
        assertThat(headerValue(dlqRecord, "failure-reason")).startsWith("validation failed");
        assertThat(headerValue(dlqRecord, "original-topic")).isEqualTo("job-leads.raw");
        assertThat(postings.count()).isZero();
    }

    private Consumer<String, byte[]> dlqConsumer(String group) {
        dlqConsumer = new DefaultKafkaConsumerFactory<>(
                KafkaTestUtils.consumerProps(group, "true", embeddedKafka),
                new StringDeserializer(), new ByteArrayDeserializer())
                .createConsumer();
        dlqConsumer.subscribe(List.of("job-leads.dlq"));
        return dlqConsumer;
    }

    /** Polls until a DLQ record whose payload contains the marker appears. */
    private ConsumerRecord<String, byte[]> awaitRecord(Consumer<String, byte[]> consumer, String marker) {
        List<ConsumerRecord<String, byte[]>> seen = new ArrayList<>();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
            records.forEach(seen::add);
            assertThat(seen.stream()
                    .map(r -> new String(r.value(), StandardCharsets.UTF_8))
                    .anyMatch(payload -> payload.contains(marker)))
                    .isTrue();
        });
        return seen.stream()
                .filter(r -> new String(r.value(), StandardCharsets.UTF_8).contains(marker))
                .findFirst()
                .orElseThrow();
    }

    private static String headerValue(ConsumerRecord<String, byte[]> record, String name) {
        for (Header h : record.headers().headers(name)) {
            return new String(h.value(), StandardCharsets.UTF_8);
        }
        return null;
    }
}
