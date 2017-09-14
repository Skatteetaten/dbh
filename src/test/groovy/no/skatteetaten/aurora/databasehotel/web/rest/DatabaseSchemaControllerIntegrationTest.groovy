package no.skatteetaten.aurora.databasehotel.web.rest

import org.springframework.http.ResponseEntity

import groovy.json.JsonSlurper

class DatabaseSchemaControllerIntegrationTest extends AbstractControllerTest {

  def labels = [
      affiliation: "aurora",
      application: "test",
      environment: "test",
      name       : "db",
      userId     : "test"
  ]

  def setup() {
    // Wait until the database has been configured
    for (i in 0..100) {
      ResponseEntity<String> responseEntity = get("/api/v1/admin/databaseInstance/")
      if (new JsonSlurper().parseText(responseEntity.body).items.size() >= 1) {
        break
      }
      Thread.sleep(1000)
    }
  }

  def "Create schema without required labels fails"() {

    when:
      ResponseEntity<String> responseEntity = post("/schema/", [instanceName: "test-dev"])

    then:
      responseEntity.statusCode.value() == 400
  }

  def "Create schema with labels"() {

    when:
      ResponseEntity<String> responseEntity = post("/schema/", [
          instanceName: "test-dev",
          labels      : labels
      ])

    then:
      responseEntity.statusCode.value() == 200

      def response = new JsonSlurper().parseText(responseEntity.body)
      def schema = response.items[0]

      schema.labels != null
      schema.labels == labels

    when:
      def responseEntityGet = get("/schema/$schema.id")

    then:
      responseEntityGet.statusCode.value() == 200

      def responseGet = new JsonSlurper().parseText(responseEntityGet.body)
      def schemaGet = responseGet.items[0]

      schemaGet.labels != null
      schemaGet.labels == labels
  }

  def "Register schema for external database"() {

    when:
      ResponseEntity<String> responseEntity = post("/schema/", [
          schema: [
              username: "theuser",
              password: "thepassword",
              jdbcUrl : "jdbc:oracle:thin:@some-database-server.example.com:1521/dbhotel"
          ],
          labels: labels
      ])

    then:
      responseEntity.statusCode.value() == 200

      def response = new JsonSlurper().parseText(responseEntity.body)
      def schema = response.items[0]

      schema.labels != null
      schema.labels == labels

      schema.jdbcUrl == "jdbc:oracle:thin:@some-database-server.example.com:1521/dbhotel"
      schema.name == 'theuser'
      schema.users.first().username == 'theuser'
      schema.users.first().password == 'thepassword'

    when:
      def responseEntityGet = get("/schema/$schema.id")

    then:
      responseEntityGet.statusCode.value() == 200

      def responseGet = new JsonSlurper().parseText(responseEntityGet.body)
      def schemaGet = responseGet.items[0]

      schemaGet.labels != null
      schemaGet.labels == labels

      schema.jdbcUrl == "jdbc:oracle:thin:@some-database-server.example.com:1521/dbhotel"
      schema.name == 'theuser'
      schema.users.first().username == 'theuser'
      schema.users.first().password == 'thepassword'

    when:
      responseEntityGet = get("/schema/")
      responseGet = new JsonSlurper().parseText(responseEntityGet.body)

    then:
      responseEntityGet.statusCode.value() == 200
      responseGet.items.any { it.id == schemaGet.id }
  }

  def "Search by labels"() {

    given:
      ResponseEntity<String> responseEntity = post("/schema/", [
          instanceName: "test-dev",
          labels      : labels
      ])
      post("/schema/", [
          instanceName: "test-dev",
          labels      : labels.with { put "application", "test1"; it }
      ])

    expect:
      responseEntity.statusCode.value() == 200
      def response = new JsonSlurper().parseText(responseEntity.body)
      def schema = response.items[0]
      def schemaId = schema.id

    when:
      def labelQuery = URLEncoder.encode("affiliation=aurora,application=test1", "UTF-8")
      def searchResponseEntity = get("/schema/?labels=$labelQuery")

    then:
      def resultSchema = response.items[0]

      searchResponseEntity.statusCode.value() == 200
      def searchResponse = new JsonSlurper().parseText(searchResponseEntity.body)
      searchResponse.items.size() == 1

      resultSchema.id == schemaId
  }

}
