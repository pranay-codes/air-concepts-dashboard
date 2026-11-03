package com.airconcepts.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DashboardApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(DashboardApiApplication.class, args);
  }
}
