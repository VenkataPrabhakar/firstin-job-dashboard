package com.firstin.dashboard.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Production datasource wiring. Supabase mandates TLS, so {@code sslmode=require}
 * is appended to the JDBC URL here — in code, not just in documentation — when
 * the operator did not already request an sslmode.
 */
@Configuration
@Profile("prod")
public class ProdDataSourceConfig {

    @Bean
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(withSslModeRequire(url));
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }

    static String withSslModeRequire(String url) {
        if (url == null || url.contains("sslmode=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "sslmode=require";
    }
}
