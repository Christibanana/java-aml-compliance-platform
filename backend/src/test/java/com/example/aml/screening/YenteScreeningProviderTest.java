package com.example.aml.screening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class YenteScreeningProviderTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void mapsOfficialMatchContractAndMarksDataReal() throws Exception {
        server.createContext("/match/default", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"responses\":{\"screening\":{\"results\":["
                    .concat("{\"id\":\"Q-1\",\"caption\":\"Demo Real Candidate\",\"score\":0.91,\"datasets\":[\"sanctions\"]},")
                    .concat("{\"id\":\"Q-2\",\"caption\":\"Below Threshold\",\"score\":0.40,\"datasets\":[\"pep\"]}]}}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        var provider = provider(true);
        var matches = provider.search(new ScreeningProvider.Query("Demo Name", "CN", "Person"));
        assertThat(provider.dataMode()).isEqualTo(ScreeningProvider.DataMode.REAL);
        assertThat(matches).singleElement().satisfies(match -> {
            assertThat(match.externalId()).isEqualTo("Q-1");
            assertThat(match.score()).isEqualByComparingTo("0.9100");
            assertThat(match.dataset()).isEqualTo("sanctions");
            assertThat(match.notice()).contains("真实 Yente");
        });
        assertThat(requestBody.get()).contains("\"schema\":\"Person\"").contains("\"country\":[\"cn\"]");
        assertThat(authorization.get()).isEqualTo("ApiKey test-key");
    }

    @Test void exposesExternalFailureAndNeverReturnsFakeEmptyResult() throws Exception {
        server.createContext("/match/default", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.start();
        assertThatThrownBy(() -> provider(true).search(new ScreeningProvider.Query("Name", "CN", "LegalEntity")))
                .isInstanceOf(ScreeningProviderException.class).hasMessageContaining("HTTP 503");
    }

    @Test void disabledProviderFailsBeforeNetworkCall() {
        assertThatThrownBy(() -> provider(false).search(new ScreeningProvider.Query("Name", "CN", "LegalEntity")))
                .isInstanceOf(ScreeningProviderException.class).hasMessageContaining("未启用");
    }

    private YenteScreeningProvider provider(boolean enabled) {
        return new YenteScreeningProvider(HttpClient.newHttpClient(), new ObjectMapper(), enabled, baseUrl,
                "default", "test-key", new BigDecimal("0.70"), Duration.ofSeconds(2));
    }
}
