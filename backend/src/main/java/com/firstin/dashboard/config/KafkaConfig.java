package com.firstin.dashboard.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Kafka wiring. Values deserialize as raw bytes on purpose: JSON parsing and
 * validation happen in {@code IngestionService} so poison records can be
 * quarantined to the DLQ instead of failing the batch.
 *
 * <p>Security comes from the environment only (docs/DESIGN.md §9):
 * <ul>
 *   <li>{@code KAFKA_BOOTSTRAP_SERVERS}</li>
 *   <li>{@code KAFKA_SASL_USERNAME} / {@code KAFKA_SASL_PASSWORD} — when set,
 *       clients use SASL_SSL with SCRAM; the JAAS config is assembled here so
 *       the password never appears in config files or logs.</li>
 *   <li>{@code KAFKA_SASL_MECHANISM} — default {@code SCRAM-SHA-256}.</li>
 *   <li>{@code KAFKA_SECURITY_PROTOCOL} — overrides the default
 *       ({@code SASL_SSL} when SASL is on, {@code PLAINTEXT} otherwise).</li>
 * </ul>
 *
 * <p>{@code @EnableKafka} is declared here because the application excludes
 * Boot's {@code KafkaAutoConfiguration} (its consumer/producer factories would
 * fight these explicit raw-byte beans) — without it, {@code @KafkaListener}
 * annotations would never be processed and no consumer would start.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${KAFKA_SASL_USERNAME:}")
    private String saslUsername;

    @Value("${KAFKA_SASL_PASSWORD:}")
    private String saslPassword;

    @Value("${KAFKA_SASL_MECHANISM:SCRAM-SHA-256}")
    private String saslMechanism;

    @Value("${KAFKA_SECURITY_PROTOCOL:}")
    private String securityProtocol;

    private Map<String, Object> commonProps() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", bootstrapServers);
        if (saslUsername != null && !saslUsername.isBlank()) {
            String protocol = (securityProtocol == null || securityProtocol.isBlank())
                    ? "SASL_SSL"
                    : securityProtocol.trim();
            String mechanism = saslMechanism.trim();
            props.put("security.protocol", protocol);
            props.put("sasl.mechanism", mechanism);
            // JAAS config is built here so the password lives only in the
            // environment — it is never written to a file or a log line.
            String loginModule = "PLAIN".equalsIgnoreCase(mechanism)
                    ? "org.apache.kafka.common.security.plain.PlainLoginModule"
                    : "org.apache.kafka.common.security.scram.ScramLoginModule";
            props.put("sasl.jaas.config",
                    loginModule + " required"
                            + " username=\"" + escapeJaas(saslUsername.trim()) + "\""
                            + " password=\"" + escapeJaas(saslPassword) + "\";");
        } else if (securityProtocol != null && !securityProtocol.isBlank()) {
            props.put("security.protocol", securityProtocol.trim());
        }
        return props;
    }

    private static String escapeJaas(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Bean
    public ConsumerFactory<String, byte[]> byteArrayConsumerFactory() {
        Map<String, Object> props = commonProps();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        // Stable consumer group; replays are idempotent by stable record id.
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "firstin-ingest");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> byteArrayKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(byteArrayConsumerFactory());
        return factory;
    }

    @Bean
    public ProducerFactory<String, byte[]> byteArrayProducerFactory() {
        Map<String, Object> props = commonProps();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, byte[]> byteArrayKafkaTemplate() {
        return new KafkaTemplate<>(byteArrayProducerFactory());
    }
}
