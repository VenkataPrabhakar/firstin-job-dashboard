package com.firstin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * QA: the Maven build bundles the React SPA into {@code target/classes/static}
 * (via frontend-maven-plugin + the resources copy). A jar without the UI must
 * fail the build, not ship silently.
 */
class StaticBundleTest {

    private static final Path STATIC = Paths.get("target/classes/static");

    @Test
    void spaBundleIsCopiedIntoStatic() throws Exception {
        Path index = STATIC.resolve("index.html");
        assertThat(Files.isRegularFile(index))
                .as("expected the bundled SPA at %s", index.toAbsolutePath())
                .isTrue();

        String html = Files.readString(index);
        Matcher refs = Pattern.compile("/assets/([^\"]+)").matcher(html);
        List<String> assets = new ArrayList<>();
        while (refs.find()) {
            assets.add(refs.group(1));
        }
        assertThat(assets)
                .as("index.html should reference hashed /assets/ bundles")
                .isNotEmpty();

        for (String asset : assets) {
            assertThat(Files.isRegularFile(STATIC.resolve("assets").resolve(asset)))
                    .as("referenced asset missing: %s", asset)
                    .isTrue();
        }

        try (Stream<Path> files = Files.list(STATIC.resolve("assets"))) {
            assertThat(files.findAny())
                    .as("expected at least one generated asset")
                    .isPresent();
        }
    }
}
