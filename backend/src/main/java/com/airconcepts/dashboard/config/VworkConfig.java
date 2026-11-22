package com.airconcepts.dashboard.config;

import com.airconcepts.dashboard.vwork.*;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A provider exists only when its mode is explicitly selected. */
@Configuration
public class VworkConfig {
  @Bean
  VworkSecretCipher vworkSecretCipher(@Value("${VWORK_CREDENTIAL_ENCRYPTION_KEY:}") String key,
      @Value("${XERO_TOKEN_ENCRYPTION_KEY:}") String xeroKey) {
    return new VworkSecretCipher(key, xeroKey);
  }

  @Bean
  VworkConnectionService vworkConnectionService(VworkConnectionRepository repository,
      ObjectProvider<VworkClient> client, VworkSecretCipher cipher, Clock clock) {
    return new VworkConnectionService(repository, client::getIfAvailable, cipher, clock);
  }
  @Bean
  @ConditionalOnProperty(name = "dashboard.vwork.mode", havingValue = "mock")
  VworkClient mockVworkClient() { return new MockVworkClient(); }

  @Bean
  @ConditionalOnProperty(name = "dashboard.vwork.mode", havingValue = "live")
  VworkClient liveVworkClient(Clock clock) { return VworkLiveClient.production(clock); }
}
