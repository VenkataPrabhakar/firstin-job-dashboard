package com.firstin.dashboard.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The prod profile must never talk to Postgres without TLS. */
class ProdDataSourceConfigTest {

    @Test
    void appendsSslmodeRequireWhenMissing() {
        assertThat(ProdDataSourceConfig.withSslModeRequire("jdbc:postgresql://db:5432/postgres"))
                .isEqualTo("jdbc:postgresql://db:5432/postgres?sslmode=require");
    }

    @Test
    void appendsWithAmpersandWhenQueryStringPresent() {
        assertThat(ProdDataSourceConfig.withSslModeRequire(
                        "jdbc:postgresql://db:5432/postgres?applicationName=firstin"))
                .isEqualTo("jdbc:postgresql://db:5432/postgres?applicationName=firstin&sslmode=require");
    }

    @Test
    void leavesAnExplicitSslmodeAlone() {
        String url = "jdbc:postgresql://db:5432/postgres?sslmode=verify-full";
        assertThat(ProdDataSourceConfig.withSslModeRequire(url)).isEqualTo(url);
    }
}
