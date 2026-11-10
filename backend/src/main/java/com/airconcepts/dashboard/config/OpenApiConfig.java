package com.airconcepts.dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.info.Info;

@Configuration
class OpenApiConfig {

  /** Static title and version keep the OpenAPI snapshot stable. */
  @Bean
  OpenAPI dashboardOpenApi() {
    org.springdoc.core.utils.SpringDocUtils.getConfig().replaceWithSchema(java.math.BigDecimal.class,
        new io.swagger.v3.oas.models.media.StringSchema().format("decimal"));
    return new OpenAPI().components(new Components().addSecuritySchemes("bearerAuth",
        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
        .info(new Info()
        .title("Air Concepts Financial Admin Dashboard API")
        .version("v1"));
  }
}
