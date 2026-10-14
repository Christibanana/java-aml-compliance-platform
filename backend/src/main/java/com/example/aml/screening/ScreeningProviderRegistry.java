package com.example.aml.screening;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ScreeningProviderRegistry {
    private final Map<String, ScreeningProvider> providers;
    private final String defaultProvider;

    public ScreeningProviderRegistry(List<ScreeningProvider> providers,
                                     @Value("${aml.screening.provider:MOCK}") String defaultProvider) {
        Map<String, ScreeningProvider> indexed = new LinkedHashMap<>();
        providers.forEach(provider -> indexed.put(provider.name().toUpperCase(Locale.ROOT), provider));
        this.providers = Map.copyOf(indexed);
        this.defaultProvider = defaultProvider.toUpperCase(Locale.ROOT);
    }

    public ScreeningProvider require(String requested) {
        String name = requested == null || requested.isBlank() ? defaultProvider : requested.toUpperCase(Locale.ROOT);
        ScreeningProvider provider = providers.get(name);
        if (provider == null) throw new IllegalArgumentException("未知筛查 Provider：" + name);
        if (!provider.available()) throw new IllegalArgumentException("筛查 Provider 未启用：" + name);
        return provider;
    }

    public List<ProviderInfo> list() {
        return providers.values().stream().map(provider -> new ProviderInfo(provider.name(),
                provider.dataMode().name(), provider.available(), provider.name().equals(defaultProvider))).toList();
    }

    public record ProviderInfo(String name, String dataMode, boolean available, boolean defaultProvider) {}
}
