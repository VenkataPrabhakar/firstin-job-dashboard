package com.firstin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.firstin.dashboard.ingest.PayParser;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** QA: pay parsing — hourly/yearly/K formats → hourly equivalents; garbage → nulls. */
class PayParserTest {

    @Test
    void hourlyRange() {
        var pay = PayParser.parse("$70-75/hr").orElseThrow();
        assertThat(pay.min()).isEqualByComparingTo(new BigDecimal("70"));
        assertThat(pay.max()).isEqualByComparingTo(new BigDecimal("75"));
        assertThat(pay.unit()).isEqualTo("hour");
        assertThat(pay.hourlyEquiv()).isEqualByComparingTo(new BigDecimal("72.50"));
    }

    @Test
    void hourlyRangeWithSpacesAndDollars() {
        var pay = PayParser.parse("$70 - $75 / hr").orElseThrow();
        assertThat(pay.unit()).isEqualTo("hour");
        assertThat(pay.hourlyEquiv()).isEqualByComparingTo(new BigDecimal("72.50"));
    }

    @Test
    void bareHourly() {
        var pay = PayParser.parse("70/hr").orElseThrow();
        assertThat(pay.min()).isEqualByComparingTo(new BigDecimal("70"));
        assertThat(pay.max()).isEqualByComparingTo(new BigDecimal("70"));
        assertThat(pay.unit()).isEqualTo("hour");
        assertThat(pay.hourlyEquiv()).isEqualByComparingTo(new BigDecimal("70.00"));
    }

    @Test
    void kImpliesYearly() {
        var pay = PayParser.parse("$120K").orElseThrow();
        assertThat(pay.min()).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(pay.max()).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(pay.unit()).isEqualTo("year");
        // 120000 / 2080 = 57.6923… → 57.69
        assertThat(pay.hourlyEquiv()).isEqualByComparingTo(new BigDecimal("57.69"));
    }

    @Test
    void yearlyRangeWithCommasAndEnDash() {
        var pay = PayParser.parse("$130,000–$150,000/year").orElseThrow();
        assertThat(pay.min()).isEqualByComparingTo(new BigDecimal("130000"));
        assertThat(pay.max()).isEqualByComparingTo(new BigDecimal("150000"));
        assertThat(pay.unit()).isEqualTo("year");
        // 140000 / 2080 = 67.307… → 67.31
        assertThat(pay.hourlyEquiv()).isEqualByComparingTo(new BigDecimal("67.31"));
    }

    @Test
    void kRangeYearly() {
        var pay = PayParser.parse("$120K-$150K").orElseThrow();
        assertThat(pay.unit()).isEqualTo("year");
        assertThat(pay.min()).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(pay.max()).isEqualByComparingTo(new BigDecimal("150000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "competitive",
            "DOE",
            "$-",
            "70",          // bare number, no unit: ambiguous — do not guess
            "free pizza",
            " ",           // blank
    })
    void garbageYieldsEmpty(String raw) {
        assertThat(PayParser.parse(raw)).isEmpty();
    }

    @Test
    void nullYieldsEmpty() {
        assertThat(PayParser.parse(null)).isEmpty();
    }
}
