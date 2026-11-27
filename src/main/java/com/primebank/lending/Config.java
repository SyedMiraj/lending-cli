package com.primebank.lending;

import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads config/merchants.json (base URL, merchant credentials, defaults and answers). */
final class Config {

    static final ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    static final String DEFAULT_PATH = "config/merchants.json";

    final String baseUrl;
    final boolean showRequestPayload;
    final List<Merchant> merchants = new ArrayList<>();

    private Config(JsonNode root) {
        baseUrl = root.path("baseUrl").asText();
        showRequestPayload = root.path("showRequestPayload").asBoolean(false);
        for (JsonNode m : root.path("merchants")) {
            merchants.add(new Merchant(m));
        }
    }

    /**
     * Path order: -Dlending.config, then LENDING_CONFIG env var, then config/merchants.json in the
     * current directory, then config/merchants.json next to the executable (or jar). The last one
     * makes a double-clicked executable or a desktop shortcut find its config wherever it starts.
     */
    static Path resolvePath() {
        String path = System.getProperty("lending.config");
        if (path == null || path.isBlank()) {
            path = System.getenv("LENDING_CONFIG");
        }
        if (path != null && !path.isBlank()) {
            return Path.of(path);
        }
        Path inWorkingDir = Path.of(DEFAULT_PATH);
        if (Files.isRegularFile(inWorkingDir)) {
            return inWorkingDir;
        }
        Path appDir = appDir();
        if (appDir != null) {
            Path besideApp = appDir.resolve(DEFAULT_PATH);
            if (Files.isRegularFile(besideApp)) {
                return besideApp;
            }
        }
        return inWorkingDir;
    }

    /** Folder holding the native executable or the jar, or null if it can't be determined. */
    static Path appDir() {
        try {
            if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
                return ProcessHandle.current().info().command()
                        .map(cmd -> Path.of(cmd).toAbsolutePath().getParent())
                        .orElse(null);
            }
            var source = Config.class.getProtectionDomain().getCodeSource();
            if (source == null) {
                return null;
            }
            Path location = Path.of(source.getLocation().toURI()).toAbsolutePath();
            return Files.isDirectory(location) ? location : location.getParent();
        } catch (Exception e) {
            return null;
        }
    }

    static Config load(Path path) throws IOException {
        Config config = new Config(JSON.readTree(Files.readString(path)));
        if (config.baseUrl.isBlank() || config.merchants.isEmpty()) {
            throw new IOException("baseUrl and at least one merchant are required in " + path);
        }
        return config;
    }

    static final class Merchant {
        final String name;
        final String key;
        final JsonNode auth;
        final JsonNode defaults;
        final JsonNode answerCategories;
        final List<String> supportedLoanTypes = new ArrayList<>();

        Merchant(JsonNode node) {
            name = node.path("name").asText();
            key = node.path("key").asText();
            auth = node.path("auth");
            defaults = node.path("defaults");
            answerCategories = node.path("answerCategories");
            node.path("supportedLoanTypes").forEach(t -> supportedLoanTypes.add(t.asText()));
            if (supportedLoanTypes.isEmpty()) {
                supportedLoanTypes.add("retail");
            }
        }

        boolean authEnabled() {
            return auth.path("enabled").asBoolean(false);
        }

        /** Text value from "auth", or null when missing/blank. */
        String auth(String field) {
            return text(auth, field);
        }

        /** Text value from "defaults", or null when missing/blank. */
        String def(String field) {
            return text(defaults, field);
        }

        private static String text(JsonNode node, String field) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull() || value.asText().isBlank()) {
                return null;
            }
            return value.asText();
        }
    }
}
