package com.airconcepts.dashboard.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.zaxxer.hikari.HikariDataSource;

/** Requirement R4: the connection pool is capped, so concurrent requests cannot exhaust the Test database. */
@SpringBootTest
@ActiveProfiles("test")
class DataSourcePoolTest {

  @Autowired
  private DataSource dataSource;

  @Test
  void capsThePoolAtFiveConnections() {
    assertThat(dataSource).isInstanceOf(HikariDataSource.class);
    assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(5);
  }
}
