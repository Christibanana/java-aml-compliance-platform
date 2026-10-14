package com.example.aml.screening;

import java.math.BigDecimal;
import java.util.List;

public interface ScreeningProvider {
    enum DataMode { REAL, SIMULATED }

    String name();
    DataMode dataMode();
    boolean available();
    List<Match> search(Query query);

    record Query(String name, String countryCode, String schema) {}

    record Match(String externalId, String matchedName, BigDecimal score, String dataset,
                 String notice, String payloadJson) {}
}
