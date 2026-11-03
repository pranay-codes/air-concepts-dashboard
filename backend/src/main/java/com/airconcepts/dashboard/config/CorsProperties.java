package com.airconcepts.dashboard.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Allowed browser origins. Defaults to the Vite dev server only (see application.yml). */
@ConfigurationProperties("dashboard.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
