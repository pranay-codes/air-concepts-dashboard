package com.airconcepts.dashboard;

import com.airconcepts.dashboard.time.BusinessTime;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DashboardApiApplication {

  public static void main(String[] args) {
    // Log timestamps use the JVM default zone. Setting it to the business zone makes every log line show Auckland time.
    TimeZone.setDefault(TimeZone.getTimeZone(BusinessTime.ZONE));
    SpringApplication.run(DashboardApiApplication.class, args);
  }
}
