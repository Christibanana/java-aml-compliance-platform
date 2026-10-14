package com.example.aml.integration.fineract;

import com.example.aml.integration.ExternalIntegrationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class FineractApiAdapterTest {
    private HttpServer server;
    private String baseUrl;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/fineract-provider/api/v1";
    }
    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void mapsOfficialSavingsAccountContractAndSendsRequiredHeaders() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> tenant = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/fineract-provider/api/v1/savingsaccounts/7", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            tenant.set(exchange.getRequestHeaders().getFirst("fineract-platform-tenantid"));
            query.set(exchange.getRequestURI().getQuery());
            byte[] response = ("{\"id\":7,\"accountNo\":\"ACC-7\",\"currency\":{\"code\":\"USD\"},\"transactions\":[" +
                    "{\"id\":31,\"date\":[2026,10,14],\"amount\":125.50,\"reversed\":false," +
                    "\"transactionType\":{\"code\":\"savingsAccountTransactionType.deposit\",\"deposit\":true,\"withdrawal\":false}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        FineractApiAdapter adapter = adapter(true);
        var account = adapter.fetchSavingsAccount(7);
        assertThat(account.accountNo()).isEqualTo("ACC-7");
        assertThat(account.currency()).isEqualTo("USD");
        assertThat(account.transactions()).singleElement().satisfies(transaction -> {
            assertThat(transaction.id()).isEqualTo(31);
            assertThat(transaction.deposit()).isTrue();
            assertThat(transaction.amount()).isEqualByComparingTo("125.50");
        });
        assertThat(authorization.get()).isEqualTo("Basic bWlmb3M6cGFzc3dvcmQ=");
        assertThat(tenant.get()).isEqualTo("default");
        assertThat(query.get()).isEqualTo("associations=transactions");
    }

    @Test void reportsUnavailableAndExternalHttpFailureWithoutFakeData() throws Exception {
        assertThatThrownBy(() -> adapter(false).fetchSavingsAccount(1))
                .isInstanceOf(ExternalIntegrationException.class).hasMessageContaining("未启用");
        server.createContext("/fineract-provider/api/v1/savingsaccounts/1", exchange -> {
            exchange.sendResponseHeaders(503, -1); exchange.close();
        });
        server.start();
        assertThatThrownBy(() -> adapter(true).fetchSavingsAccount(1))
                .isInstanceOf(ExternalIntegrationException.class).hasMessageContaining("HTTP 503");
    }

    private FineractApiAdapter adapter(boolean enabled) {
        return new FineractApiAdapter(HttpClient.newHttpClient(), new ObjectMapper(), enabled, baseUrl,
                "mifos", "password", "default", Duration.ofSeconds(2));
    }
}
