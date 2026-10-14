package com.example.aml.screening;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Component
public class MockScreeningProvider implements ScreeningProvider {
    @Override public String name() { return "MOCK"; }
    @Override public DataMode dataMode() { return DataMode.SIMULATED; }
    @Override public boolean available() { return true; }

    @Override
    public List<Match> search(Query query) {
        if (query.name().toLowerCase(Locale.ROOT).contains("demo")) {
            return List.of(new Match("MOCK-001", "Demo Similar Name", new BigDecimal("0.8600"),
                    "SYNTHETIC_DEMO", "模拟候选，仅用于测试和演示，不是真实名单结论",
                    "{\"synthetic\":true,\"generator\":\"local-mock-v1\"}"));
        }
        return List.of();
    }
}
