package com.firstin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * QA: the public read-only dashboard UI must be reachable through Spring
 * Security. Regression test for the Phase 3 deploy where
 * {@code .anyRequest().denyAll()} 403'd the SPA entry point and bundles
 * while /api/** stayed public. The deny-by-default posture is preserved:
 * anything not explicitly permitted is still forbidden.
 */
@AutoConfigureMockMvc
class DashboardSecurityTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void spaEntryPointIsPublic() throws Exception {
        // Under MockMvc the welcome-page handler returns 200 with an empty
        // body (test-slice quirk); what matters here is the security rule:
        // "/" must be permitted (200), not denied (403). Real content
        // serving is covered by spaIndexHtmlIsPublic below.
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void spaIndexHtmlIsPublic() throws Exception {
        String body = mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        org.springframework.http.MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("<html");
    }

    @Test
    void spaHashedAssetsArePublic() throws Exception {
        Path assets = Paths.get("target/classes/static/assets");
        String first;
        try (Stream<Path> files = Files.list(assets)) {
            first = files.map(p -> p.getFileName().toString())
                    .sorted()
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "expected bundled assets at " + assets.toAbsolutePath()));
        }
        assertThat(first).isNotEmpty();

        mockMvc.perform(get("/assets/" + first))
                .andExpect(status().isOk());
    }

    @Test
    void unknownPathsStayDenied() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isForbidden());
    }

    @Test
    void apiStaysPublic() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }
}
