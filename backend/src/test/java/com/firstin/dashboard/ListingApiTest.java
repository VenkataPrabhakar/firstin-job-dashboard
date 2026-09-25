package com.firstin.dashboard;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstin.dashboard.ingest.IngestionService;
import com.firstin.dashboard.ingest.LeadEvent;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * QA: envelope shape {@code {listings, indexedToday, lastPull}};
 * case-insensitive search; tab filters; empty visa tab → 200 with empty list;
 * edge fixtures (nearly-all-fields-missing, hostile HTML) render without
 * errors and without {@code undefined}/blank headings. Security headers present.
 */
@AutoConfigureMockMvc
class ListingApiTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IngestionService ingestion;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seed() throws Exception {
        Instant now = Instant.now();
        LeadEvent c2c = TestFixtures.lead("Sr. Java Developer", "Keylent", "Reading, PA", "C2C");
        c2c.setFirstSeen(now.minus(1, java.time.temporal.ChronoUnit.HOURS));
        c2c.setPay("$70-75/hr");
        c2c.setPosted("1 hour ago");
        c2c.setContact(TestFixtures.contact("Anil Rao", "anil.rao@example.com", "555-0100"));
        c2c.setVisa(TestFixtures.visa("open", null));

        LeadEvent w2 = TestFixtures.lead("Backend Engineer", "Globex", "Austin, TX", "W2");
        w2.setFirstSeen(now.minus(2, java.time.temporal.ChronoUnit.HOURS));
        w2.setPay("$120K");

        LeadEvent fulltime = TestFixtures.lead("Java Developer", "Initech", "Remote", "Full-Time");
        fulltime.setFirstSeen(now.minus(30, java.time.temporal.ChronoUnit.HOURS));
        fulltime.setVisa(TestFixtures.visa("confirmed", "matched phrase: \"sponsorship\""));

        LeadEvent minimal = TestFixtures.minimal("Dev", "Acme", "Remote", "W2");
        minimal.setFirstSeen(now.minus(3, java.time.temporal.ChronoUnit.HOURS));
        LeadEvent hostile = TestFixtures.hostileHtml();
        hostile.setFirstSeen(now.minus(4, java.time.temporal.ChronoUnit.HOURS));

        for (LeadEvent e : new LeadEvent[]{c2c, w2, fulltime, minimal, hostile}) {
            ingestion.ingestRaw(objectMapper.writeValueAsBytes(e), "job-leads.raw");
        }
    }

    @Test
    void envelopeShape() throws Exception {
        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listings").isArray())
                .andExpect(jsonPath("$.indexedToday").isNumber())
                .andExpect(jsonPath("$.lastPull").isString());
    }

    @Test
    void todayTabIsNewestFirstAndExcludesOld() throws Exception {
        mockMvc.perform(get("/api/listings").param("tab", "today"))
                .andExpect(status().isOk())
                // fulltime record is 30h old → excluded from today
                .andExpect(jsonPath("$.listings[?(@.title=='Java Developer')]", hasSize(0)))
                .andExpect(jsonPath("$.listings", hasSize(4)))
                // newest first: c2c (1h) before hostile (4h)
                .andExpect(jsonPath("$.listings[0].title", is("Sr. Java Developer")));
    }

    @Test
    void categoryTabsFilterSameDataset() throws Exception {
        mockMvc.perform(get("/api/listings").param("tab", "c2c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listings[*].engagement").value(org.hamcrest.Matchers.everyItem(is("C2C"))));

        mockMvc.perform(get("/api/listings").param("tab", "w2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listings[*].engagement").value(org.hamcrest.Matchers.everyItem(is("W2"))));

        mockMvc.perform(get("/api/listings").param("tab", "fulltime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listings", hasSize(1)))
                .andExpect(jsonPath("$.listings[0].engagement", is("FULLTIME")));
    }

    @Test
    void visaTabListsKnownVisaPostings() throws Exception {
        mockMvc.perform(get("/api/listings").param("tab", "visa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listings", hasSize(2)))
                .andExpect(jsonPath("$.listings[*].visaStatus",
                        org.hamcrest.Matchers.hasItems("open", "confirmed")));
    }

    @Test
    void emptyVisaTabIs200WithEmptyListAndNullLastPull() throws Exception {
        sources.deleteAll();
        postings.deleteAll();

        mockMvc.perform(get("/api/listings").param("tab", "visa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listings", hasSize(0)))
                .andExpect(jsonPath("$.indexedToday", is(0)))
                // lastPull is explicitly null (not omitted) so Phase 2 can
                // tell "no sightings yet" apart from a missing field.
                .andExpect(jsonPath("$.lastPull", nullValue()));
    }

    @Test
    void searchIsCaseInsensitiveOnTitleAndCompany() throws Exception {
        mockMvc.perform(get("/api/listings").param("tab", "today").param("q", "keylent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listings", hasSize(1)))
                .andExpect(jsonPath("$.listings[0].company", is("Keylent")));

        // "sr. java developer" matches only the c2c card, not "Sr. Java Dev"
        mockMvc.perform(get("/api/listings").param("tab", "today").param("q", "sr. java developer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listings", hasSize(1)))
                .andExpect(jsonPath("$.listings[0].title", is("Sr. Java Developer")));
    }

    @Test
    void invalidTabIs400WithGenericBody() throws Exception {
        mockMvc.perform(get("/api/listings").param("tab", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.*", hasSize(1))); // nothing but "error"
    }

    @Test
    void listingCardFields() throws Exception {
        mockMvc.perform(get("/api/listings").param("tab", "c2c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listings[0].id").isString())
                .andExpect(jsonPath("$.listings[0].title", is("Sr. Java Developer")))
                .andExpect(jsonPath("$.listings[0].payMin", is(70.0)))
                .andExpect(jsonPath("$.listings[0].payMax", is(75.0)))
                .andExpect(jsonPath("$.listings[0].payUnit", is("hour")))
                .andExpect(jsonPath("$.listings[0].payHourlyEquiv", is(72.5)))
                .andExpect(jsonPath("$.listings[0].postedMinutes", is(60)))
                .andExpect(jsonPath("$.listings[0].postedMinutesConfidence", is("precise")))
                .andExpect(jsonPath("$.listings[0].contact.name", is("Anil Rao")))
                .andExpect(jsonPath("$.listings[0].contact.email", is("anil.rao@example.com")))
                .andExpect(jsonPath("$.listings[0].sources[0].source", is("Dice")))
                .andExpect(jsonPath("$.listings[0].firstSeen").isString());
    }

    @Test
    void minimalFixtureHasNoPayAndNoPostedAge() throws Exception {
        mockMvc.perform(get("/api/listings").param("tab", "w2").param("q", "Acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listings[0].title", is("Dev")))
                .andExpect(jsonPath("$.listings[0].payMin").doesNotExist())
                .andExpect(jsonPath("$.listings[0].postedMinutes").doesNotExist())
                .andExpect(jsonPath("$.listings[0].contact").doesNotExist());
    }

    @Test
    void hostileHtmlRendersAsPlainStringsWithoutErrors() throws Exception {
        mockMvc.perform(get("/api/listings").param("tab", "c2c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listings[?(@.title =~ /.*script.*/)].title",
                        org.hamcrest.Matchers.hasSize(1)))
                // Never blank, never "undefined"
                .andExpect(jsonPath("$.listings[*].title",
                        org.hamcrest.Matchers.everyItem(not(is("")))))
                .andExpect(jsonPath("$.listings[*].title",
                        org.hamcrest.Matchers.everyItem(not(is("undefined")))));
    }

    @Test
    void securityHeadersPresent() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Referrer-Policy"));
    }

    @Test
    void health() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }
}
