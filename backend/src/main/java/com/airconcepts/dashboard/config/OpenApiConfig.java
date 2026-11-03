package com.airconcepts.dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
class OpenApiConfig {

  /** Static title and version keep the OpenAPI snapshot stable. */
  @Bean
  OpenAPI dashboardOpenApi() {
    return new OpenAPI().info(new Info()
        .title("Air Concepts Financial Admin Dashboard API")
        .version("v1"));
  }
}
