package com.primebank.lending;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** HTTP transport: token fetch, merchant headers, POST with one 401 retry. */
final class ApiClient {

    static final String TOKEN_PATH = "/user/get-token";

    private static final String LAN_INTERNET_ERROR_MSG =
            "[-] Connection failed. Make sure LAN is disconnected—this only works over the internet.";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final Config config;
    private final Config.Merchant merchant;
    private String token;

    ApiClient(Config config, Config.Merchant merchant) {
        this.config = config;
        this.merchant = merchant;
    }

    /** Fetches the initial token. Returns false if auth is enabled and no token was obtained. */
    boolean login() {
        if (!merchant.authEnabled()) {
            return true;
        }
        token = fetchToken();
        return token != null;
    }

    private String fetchToken() {
//        System.out.println("[*] Fetching auth token for " + merchant.name + "...");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", merchant.auth("grantType"));
        form.put("client_id", merchant.auth("clientId"));
        form.put("username", merchant.auth("username"));
        form.put("password", merchant.auth("password"));
        form.put("client_secret", merchant.auth("clientSecret"));
        form.put("orgCode", merchant.auth("orgCode"));
        String body = form.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl + TOKEN_PATH))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> resp = send(request);
            if (resp.statusCode() != 200) {
                System.out.println("[-] Token request failed (HTTP " + resp.statusCode() + "): " + resp.body());
                return null;
            }
            String found = extractToken(resp.body());
            if (found == null) {
                System.out.println("[-] Token response 200, but could not find token field in body: " + resp.body());
                return null;
            }
//            System.out.println("[+] Token fetched successfully.");
            return found;
        } catch (IOException e) {
            System.out.println(LAN_INTERNET_ERROR_MSG);
            return null;
        }
    }

    /** POSTs JSON to baseUrl + path. Refreshes the token and retries once on 401. Returns null on network failure. */
    HttpResponse<String> post(String path, ObjectNode payload) {
        String url = config.baseUrl + path;
        try {
            String body = Config.JSON.writeValueAsString(payload);
            if (config.showRequestPayload) {
                printRequest(url, payload);
            }
            HttpResponse<String> resp = send(jsonRequest(url, body));
            if (resp.statusCode() == 401 && merchant.authEnabled()) {
                System.out.println("[!] Received 401; refreshing token and retrying once...");
                String fresh = fetchToken();
                if (fresh != null) {
                    token = fresh;
                    if (config.showRequestPayload) {
                        printRequest(url, payload);
                    }
                    resp = send(jsonRequest(url, body));
                }
            }
            return resp;
        } catch (IOException e) {
            System.out.println(LAN_INTERNET_ERROR_MSG);
            return null;
        }
    }

    private HttpRequest jsonRequest(String url, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("accept", "*/*")
                .header("Content-Type", "application/json");

        String orgCode = merchant.auth("orgCode");
        if (orgCode != null) {
            builder.header("orgCode", orgCode);
        }
        String clientAuth = merchant.auth("clientAuth");
        String clientId = merchant.auth("clientId");
        String clientSecret = merchant.auth("clientSecret");
        if (clientAuth == null && clientId != null && clientSecret != null) {
            clientAuth = Base64.getEncoder()
                    .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        }
        if (clientAuth != null) {
            builder.header("clientAuth", clientAuth);
        }
        if (token != null) {
            String raw = token.startsWith("Bearer ") ? token.substring(7).trim() : token.trim();
            builder.header("token", raw);
            if (merchant.auth.path("sendBearer").asBoolean(true)) {
                builder.header("Authorization", "Bearer " + raw);
            }
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    static void printResponse(HttpResponse<String> resp) {
        System.out.println();
        System.out.println("[+] HTTP Status: " + resp.statusCode());
        if (resp.statusCode() == 401) {
            resp.headers().firstValue("WWW-Authenticate")
                    .ifPresent(v -> System.out.println("[!] WWW-Authenticate: " + v));
        }
        System.out.println("[+] Response Body:");
        JsonNode json = parse(resp.body());
        System.out.println(json != null ? pretty(json) : resp.body());
    }

    private static void printRequest(String url, JsonNode payload) {
        System.out.println();
        System.out.println("[*] POST " + url);
        System.out.println("[*] Request Payload:");
        System.out.println(pretty(payload));
    }

    /** Parses a JSON body; returns null if the body is not valid JSON. */
    static JsonNode parse(String body) {
        try {
            return body == null || body.isBlank() ? null : Config.JSON.readTree(body);
        } catch (IOException e) {
            return null;
        }
    }

    static String extractToken(String body) {
        JsonNode json = parse(body);
        if (json == null) {
            return body == null || body.isBlank() ? null : body.trim();
        }
        String direct = firstText(json, "access_token", "token");
        return direct != null ? direct : firstText(json.path("data"), "access_token", "token");
    }

    /** First non-blank string value among the given keys of a JSON object, or null. */
    static String firstText(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private static String pretty(JsonNode node) {
        try {
            return Config.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (IOException e) {
            return node.toString();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
