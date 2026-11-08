package com.airconcepts.dashboard.config;

import com.airconcepts.dashboard.staff.SupabaseAdminClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SupabaseAdminConfig {
  @Bean
  SupabaseAdminClient supabaseAdminClient(
      @Value("${dashboard.supabase.jwt-issuer-uri}") String issuerUri,
      @Value("${SUPABASE_SERVICE_ROLE_KEY:}") String adminKey) {
    URI issuer = URI.create(issuerUri);
    if (!"https".equals(issuer.getScheme()) || issuer.getHost() == null
        || !"/auth/v1".equals(issuer.getPath()) || issuer.getUserInfo() != null
        || issuer.getQuery() != null || issuer.getFragment() != null) {
      throw new IllegalStateException("Supabase Auth issuer must be an HTTPS Auth origin.");
    }
    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    var requests = new JdkClientHttpRequestFactory(http);
    requests.setReadTimeout(Duration.ofSeconds(20));
    return new SupabaseAdminClient(RestClient.builder().baseUrl(issuer.toString())
        .requestFactory(requests).build(), adminKey);
  }
}
