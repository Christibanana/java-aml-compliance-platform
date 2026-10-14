package com.example.aml.integration.fineract;

import com.example.aml.integration.ExternalIntegrationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Component
public class FineractApiAdapter implements FineractClient {
    private final HttpClient client;
    private final ObjectMapper json;
    private final URI apiBase;
    private final String authorization;
    private final String tenant;
    private final Duration timeout;
    private final boolean enabled;

    @Autowired
    public FineractApiAdapter(ObjectMapper json,
            @Value("${aml.integration.fineract.enabled:false}") boolean enabled,
            @Value("${aml.integration.fineract.base-url:http://localhost:8443/fineract-provider/api/v1}") String baseUrl,
            @Value("${aml.integration.fineract.username:mifos}") String username,
            @Value("${aml.integration.fineract.password:password}") String password,
            @Value("${aml.integration.fineract.tenant:default}") String tenant,
            @Value("${aml.integration.fineract.timeout-seconds:5}") int timeoutSeconds) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build(), json, enabled,
                baseUrl, username, password, tenant, Duration.ofSeconds(timeoutSeconds));
    }

    FineractApiAdapter(HttpClient client, ObjectMapper json, boolean enabled, String baseUrl,
                       String username, String password, String tenant, Duration timeout) {
        String normalized = Objects.requireNonNull(baseUrl).replaceAll("/+$", "");
        this.apiBase = URI.create(normalized + "/");
        if (!Set.of("http", "https").contains(apiBase.getScheme())) {
            throw new IllegalArgumentException("Fineract base URL 仅支持 HTTP/HTTPS");
        }
        if (tenant == null || tenant.isBlank()) throw new IllegalArgumentException("Fineract tenant 不能为空");
        this.client = client;
        this.json = json;
        this.enabled = enabled;
        this.tenant = tenant.trim();
        this.timeout = timeout;
        String credentials = Objects.requireNonNullElse(username, "") + ":" + Objects.requireNonNullElse(password, "");
        this.authorization = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @Override public boolean available() { return enabled; }

    @Override
    public SavingsAccount fetchSavingsAccount(long savingsAccountId) {
        if (!enabled) throw new ExternalIntegrationException("Fineract 集成未启用");
        if (savingsAccountId <= 0) throw new IllegalArgumentException("Fineract savingsAccountId 必须大于 0");
        URI uri = apiBase.resolve("savingsaccounts/" + savingsAccountId + "?associations=transactions");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Accept", "application/json")
                .header("Authorization", authorization)
                .header("fineract-platform-tenantid", tenant)
                .header("User-Agent", "java-aml-platform/0.1")
                .GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExternalIntegrationException("Fineract 返回 HTTP " + response.statusCode());
            }
            return parse(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalIntegrationException("Fineract 调用被中断", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ExternalIntegrationException integrationException) throw integrationException;
            throw new ExternalIntegrationException("Fineract 调用失败：" + exception.getMessage(), exception);
        }
    }

    private SavingsAccount parse(String body) throws IOException {
        JsonNode root = json.readTree(body);
        long id = requiredLong(root, "id");
        String accountNo = requiredText(root, "accountNo");
        String currency = requiredText(root.path("currency"), "code").toUpperCase(Locale.ROOT);
        JsonNode values = root.path("transactions");
        if (!values.isArray()) throw new ExternalIntegrationException("Fineract 响应缺少 transactions 数组，请确认 associations=transactions");
        List<Transaction> transactions = new ArrayList<>();
        for (JsonNode value : values) {
            JsonNode type = value.path("transactionType");
            transactions.add(new Transaction(requiredLong(value, "id"), parseDate(value.path("date")),
                    requiredDecimal(value, "amount"), type.path("deposit").asBoolean(false),
                    type.path("withdrawal").asBoolean(false), value.path("reversed").asBoolean(false),
                    type.path("code").asText("UNKNOWN")));
        }
        return new SavingsAccount(id, accountNo, currency, List.copyOf(transactions));
    }

    private LocalDate parseDate(JsonNode node) {
        if (!node.isArray() || node.size() < 3) throw new ExternalIntegrationException("Fineract 交易日期格式不合法");
        try { return LocalDate.of(node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt()); }
        catch (DateTimeException exception) { throw new ExternalIntegrationException("Fineract 交易日期格式不合法", exception); }
    }
    private long requiredLong(JsonNode node, String field) {
        if (!node.hasNonNull(field) || !node.path(field).canConvertToLong()) throw new ExternalIntegrationException("Fineract 响应缺少字段：" + field);
        return node.path(field).asLong();
    }
    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isEmpty()) throw new ExternalIntegrationException("Fineract 响应缺少字段：" + field);
        return value;
    }
    private BigDecimal requiredDecimal(JsonNode node, String field) {
        if (!node.hasNonNull(field) || !node.path(field).isNumber()) throw new ExternalIntegrationException("Fineract 响应缺少字段：" + field);
        return node.path(field).decimalValue();
    }
}
