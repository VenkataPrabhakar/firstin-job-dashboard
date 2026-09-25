package com.firstin.dashboard;

import com.firstin.dashboard.repo.IngestRunRepository;
import com.firstin.dashboard.repo.JobPostingRepository;
import com.firstin.dashboard.repo.JobSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared integration-test context: H2 for JPA, one embedded Kafka broker for
 * the consumer/DLQ path. KRaft mode (no ZooKeeper) matches the local
 * docker-compose setup. The context (and broker) is cached across subclasses.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"job-leads.raw", "job-leads.dlq"}, kraft = true)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected JobPostingRepository postings;

    @Autowired
    protected JobSourceRepository sources;

    @Autowired
    protected IngestRunRepository runs;

    @BeforeEach
    void cleanDb() {
        sources.deleteAll();
        postings.deleteAll();
        runs.deleteAll();
    }
}
