package no.skatteetaten.aurora.databasehotel.dao.oracle

import org.springframework.core.env.Environment

import com.zaxxer.hikari.HikariDataSource

import no.skatteetaten.aurora.databasehotel.TestConfig
import no.skatteetaten.aurora.databasehotel.dao.DataSourceUtils
import no.skatteetaten.aurora.databasehotel.service.oracle.OracleJdbcUrlBuilder

class Datasources {

  static port = 1521

  static HikariDataSource createTestDs(String user = null, String password = null) {

    Environment environment = TestConfig.loadTestConfig()

    String host = environment.getProperty("databaseConfig.databases[0].host")
    String service = environment.getProperty("databaseConfig.databases[0].service")
    user = user ?: environment.getProperty("databaseConfig.databases[0].username")
    password = password ?: environment.getProperty("databaseConfig.databases[0].password")

    DataSourceUtils.createDataSource(createJdbcUrl(host, port, service), user, password)
  }

  private static String createJdbcUrl(String host, int port, String service) {

    new OracleJdbcUrlBuilder(service).create(host, port)
  }
}
