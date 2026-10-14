package com.example.aml.screening;
import org.junit.jupiter.api.Test; import static org.assertj.core.api.Assertions.*;
class MockScreeningProviderTest {
    private final MockScreeningProvider provider=new MockScreeningProvider();
    @Test void resultIsExplicitlySimulated(){assertThat(provider.dataMode()).isEqualTo(ScreeningProvider.DataMode.SIMULATED);assertThat(provider.search(new ScreeningProvider.Query("Demo Person","CN","Person"))).singleElement().satisfies(m->{assertThat(m.dataset()).isEqualTo("SYNTHETIC_DEMO");assertThat(m.notice()).contains("模拟");assertThat(m.payloadJson()).contains("synthetic");});}
    @Test void ordinaryNameHasNoFakeMatch(){assertThat(provider.search(new ScreeningProvider.Query("Synthetic Alice","CN","Person"))).isEmpty();}
}
