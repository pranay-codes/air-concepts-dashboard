package com.airconcepts.dashboard.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public liveness-style greeting. Returns a fixed message and no data. Exposed by SecurityConfig, exact path only. */
@RestController
public class HelloController {

  @GetMapping("/api/hello")
  public HelloResponse hello() {
    return new HelloResponse("Hello from the Air Concepts API");
  }
}
