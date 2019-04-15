package no.skatteetaten.aurora.databasehotel.web.rest

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import static org.springframework.http.HttpMethod.GET
import static org.springframework.http.HttpMethod.POST

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity

import com.zaxxer.hikari.HikariDataSource

import groovy.json.JsonOutput
import groovy.sql.Sql
import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import no.skatteetaten.aurora.databasehotel.dao.oracle.Datasources
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseManager
import spock.lang.Specification

@SpringBootTest(webEnvironment = RANDOM_PORT)
abstract class AbstractControllerTest extends Specification {

  @Autowired
  TestRestTemplate restTemplate

  def setupSpec() {

    HikariDataSource ds = Datasources.createTestDs()
    def databaseManager = new OracleDatabaseManager(ds)
    databaseManager.deleteSchema(DatabaseInstanceInitializer.DEFAULT_SCHEMA_NAME)
    def sql = new Sql(ds)
    sql.eachRow("select username from dba_users where length(username)>28") {
      try {
        databaseManager.deleteSchema(it.USERNAME)
      } catch (Exception e) {
      }
    }
    sql.execute("delete from RESIDENTS.RESIDENTS")
    ds.close()
  }

  ResponseEntity<String> get(String path, boolean printResponse = false) {

    request(GET, path, printResponse)
  }

  ResponseEntity<String> post(String path, Map<String, Object> body, boolean printResponse = false) {

    request(POST, path, printResponse, body)
  }

  private ResponseEntity<String> request(HttpMethod method, String path, boolean printResponse = false,
      Map<String, Object> body = null) {

    def headers = new HttpHeaders()
    headers.add("Authorization", "aurora-token shared-secret")
    def entity = new HttpEntity<Map<String, Object>>(body, headers)
    ResponseEntity<String> responseEntity = restTemplate.exchange(path, method, entity, String)
    if (printResponse) {
      println JsonOutput.prettyPrint(responseEntity.body)
    }
    responseEntity
  }
}
