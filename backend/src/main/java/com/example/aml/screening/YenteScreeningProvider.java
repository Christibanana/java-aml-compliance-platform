package com.example.aml.screening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Component
public class YenteScreeningProvider implements ScreeningProvider {
    private final HttpClient client;
    private final ObjectMapper json;
    private final URI endpoint;
    private final String apiKey;
    private final BigDecimal threshold;
    private final Duration timeout;
    private final boolean enabled;

    @Autowired
    public YenteScreeningProvider(ObjectMapper json,
            @Value("${aml.screening.yente.enabled:false}") boolean enabled,
            @Value("${aml.screening.yente.base-url:http://localhost:8000}") String baseUrl,
            @Value("${aml.screening.yente.dataset:default}") String dataset,
            @Value("${aml.screening.yente.api-key:}") String apiKey,
            @Value("${aml.screening.yente.threshold:0.7}") BigDecimal threshold,
            @Value("${aml.screening.yente.timeout-seconds:5}") int timeoutSeconds) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build(), json, enabled,
                baseUrl, dataset, apiKey, threshold, Duration.ofSeconds(timeoutSeconds));
    }

    YenteScreeningProvider(HttpClient client, ObjectMapper json, boolean enabled, String baseUrl, String dataset,
                           String apiKey, BigDecimal threshold, Duration timeout) {
        if (!dataset.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("Yente dataset 配置不合法");
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.endpoint = URI.create(normalized + "/match/" + dataset + "?threshold=" + threshold.toPlainString());
        this.client = client;
        this.json = json;
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.threshold = threshold;
        this.timeout = timeout;
    }

    @Override public String name() { return "YENTE"; }
    @Override public DataMode dataMode() { return DataMode.REAL; }
    @Override public boolean available() { return enabled; }

    @Override
    public List<Match> search(Query query) {
        if (!enabled) throw new ScreeningProviderException("Yente Provider 未启用");
        try {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("name", List.of(query.name()));
            if (query.countryCode() != null && !query.countryCode().isBlank()) {
                properties.put("country", List.of(query.countryCode().toLowerCase(Locale.ROOT)));
            }
            Map<String, Object> entity = Map.of("schema", query.schema(), "properties", properties);
            String body = json.writeValueAsString(Map.of("queries", Map.of("screening", entity)));
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("User-Agent", "java-aml-platform/0.1").POST(HttpRequest.BodyPublishers.ofString(body));
            if (!apiKey.isBlank()) builder.header("Authorization", "ApiKey " + apiKey);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ScreeningProviderException("Yente 返回 HTTP " + response.statusCode());
            }
            return parse(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ScreeningProviderException("Yente 调用被中断", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ScreeningProviderException providerException) throw providerException;
            throw new ScreeningProviderException("Yente 调用失败：" + exception.getMessage(), exception);
        }
    }

    private List<Match> parse(String body) throws IOException {
        JsonNode root = json.readTree(body);
        JsonNode response = root.path("responses").path("screening");
        if (response.isMissingNode()) response = root;
        JsonNode results = response.path("results");
        if (!results.isArray()) throw new ScreeningProviderException("Yente 响应缺少 results 数组");
        List<Match> matches = new ArrayList<>();
        for (JsonNode item : results) {
            BigDecimal score = item.path("score").decimalValue();
            if (score.compareTo(threshold) < 0) continue;
            matches.add(new Match(required(item, "id"), required(item, "caption"),
                    score.setScale(4, RoundingMode.HALF_UP), datasets(item.path("datasets")),
                    "真实 Yente 候选，必须由人工复核；匹配不等于制裁或违法结论", item.toString()));
        }
        return List.copyOf(matches);
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isEmpty()) throw new ScreeningProviderException("Yente 响应缺少字段：" + field);
        return value;
    }

    private String datasets(JsonNode node) {
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(value -> values.add(value.asText()));
            return String.join(",", values);
        }
        String value = node.asText("").trim();
        return value.isEmpty() ? "YENTE" : value;
    }
}
