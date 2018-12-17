package no.skatteetaten.aurora.databasehotel.web.rest

import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedRequestFields
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters
import static org.springframework.restdocs.request.RequestDocumentation.requestParameters
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import org.apache.commons.lang3.tuple.Pair
import org.junit.Rule
import org.springframework.restdocs.JUnitRestDocumentation
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.setup.MockMvcBuilders

import groovy.json.JsonOutput
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.domain.User
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelService
import spock.lang.Specification

class DatabaseSchemaControllerTest extends Specification {

  public static final String USER_ID = "some_user_id"

  @Rule
  JUnitRestDocumentation restDocumentation = new JUnitRestDocumentation('build/docs/generated-snippets')

  def databaseHotelService = Mock(DatabaseHotelService)

  protected MockMvc mockMvc

  static EXAMPLE_SCHEMA_ID = '4ad5f762-8cbc-46c2-a420-3362e2641b41'

  static EXAMPLE_LABELS = [
      userId      : USER_ID,
      affilitation: "aurora"
  ]

  def EXAMPLE_SCHEMA = new DatabaseSchema(
      EXAMPLE_SCHEMA_ID,
      new DatabaseInstanceMetaInfo('test', 'dbhost.example.com', 1521, true),
      'jdbc:oracle:thin:@dbhost.example.com:1521/dbhotel',
      'AIOIFPXHHLFLTVDPSUWEERCTMMWJUD',
      new Date(),
      new Date(), null
  ).with {
    addUser(new User(USER_ID, 'AIOIFPXHHLFLTVDPSUWEERCTMMWJUD', 'ajyEDqIZLzdedRJpoOtcOCpnyJSYki', 'SCHEMA'))
    labels = EXAMPLE_LABELS
    it
  }

  def EXAMPLE_SCHEMA_EXTERNAL = new DatabaseSchema(
      EXAMPLE_SCHEMA_ID,
      new DatabaseInstanceMetaInfo('external', null, 0, false),
      'jdbc:oracle:thin:@some-other-dbserver.example.com:1521/dbhotel',
      'AIOIFPXHHLFLTVDPSUWEERCTMMWJUD',
      new Date(),
      new Date(), null, DatabaseSchema.Type.EXTERNAL
  ).with {
    addUser(new User('some_user_id', 'AIOIFPXHHLFLTVDPSUWEERCTMMWJUD', 'ajyEDqIZLzdedRJpoOtcOCpnyJSYki', 'SCHEMA'))
    labels = EXAMPLE_LABELS
    it
  }

  def EXAMPLE_RESPONSE = [EXAMPLE_SCHEMA]

  void setup() {
    def databaseSchemaController = new DatabaseSchemaController(databaseHotelService, true, true)
    mockMvc = MockMvcBuilders.
        standaloneSetup(databaseSchemaController, new DeprecatedEndpoints(null, databaseSchemaController))
        .apply(documentationConfiguration(this.restDocumentation))
        .build()
  }

  def "No schemas in hotel"() {
    given:
      databaseHotelService.findAllDatabaseSchemas() >> []

    when:
      ResultActions result = mockMvc.perform(get('/schema/').header("Authorization", "aurora-token shared-secret"))

    then:
      result
          .andExpect(status().isOk())
          .andDo(
          document('empty-response',
              preprocessResponse(prettyPrint()),
              responseFields(
                  fieldWithPath("status").type(JsonFieldType.STRING).optional().
                      description("En status beskjed fra serveren. OK for vellykkede forespørseler."),
                  fieldWithPath("totalCount").type(JsonFieldType.NUMBER).optional().
                      description(
                          "Det totale antallet ressurser tilgjengelig (ikke nødvendigvis antall ressurser returnert i denne responsen."),
                  fieldWithPath("items").type(JsonFieldType.ARRAY).optional().
                      description(
                          "Liste med alle ressursene returnert. Dersom bare én ressurs forespørres inneholder items kun denne ene ressursen.")
              ),
              requestHeaders(
                  headerWithName("Authorization").description("aurora-token <shared-secret>")
              )
          ))
  }

  def "Create schema without labels"() {
    given:
      databaseHotelService.createSchema(null, null) >> EXAMPLE_SCHEMA

    when:
      String payload = JsonOutput.toJson([:])
      ResultActions result = mockMvc.perform(post('/schema/').content(payload).contentType(APPLICATION_JSON))

    then:
      result
          .andExpect(status().isOk())
          .andDo(
          document('schemas-post-no-labels', preprocessResponse(prettyPrint())))
  }

  def "Create schema with labels"() {
    given:
      databaseHotelService.createSchema('test', EXAMPLE_LABELS) >> EXAMPLE_SCHEMA

    when:
      String payload = JsonOutput.toJson([labels: EXAMPLE_LABELS, instanceName: 'test'])
      ResultActions result = mockMvc.perform(post('/schema/').content(payload).contentType(APPLICATION_JSON))

    then:
      result
          .andExpect(status().isOk())
          .andDo(
          document('schemas-post',
              preprocessResponse(prettyPrint()),
              relaxedRequestFields(
                  fieldWithPath("labels").type(JsonFieldType.OBJECT).optional().
                      description(
                          "Labels til skjemaet som opprettes. Kan brukes til å indikere eier og andre metadata, og kan også brukes til filtrering."),
                  fieldWithPath("instanceName").type(JsonFieldType.STRING).optional().
                      description(
                          "Navnet på database instansen skjemaet skal opprettes på. Dette er kun påkrevd dersom API instansen håndterer flere databaseinstanser (som ikke er vanlig). " +
                              "Se <<resources-databaseInstance,DatabaseInstance>> for informasjon om hvordan man finner tilgjengelige instanser.")
              ),
              relaxedResponseFields(
                  fieldWithPath("items[].id").
                      description("Unik id for dette skjemaet. Brukes til å slå opp skjemainformasjon senere."),
                  fieldWithPath("items[].type").
                      description(
                          "Hvorvidt dette skjemaet er håndtert av databasehotellet eller om det er et eksternt skjema. MANAGED i dette tilfellet."),
                  fieldWithPath("items[].jdbcUrl").
                      description("JDBC url som kan brukes for å koble til det genererte skjemaet."),
                  fieldWithPath("items[].users").description(
                      "List over genererte brukere som kan koble til skjemaet. Pt. genereres det kun en SCHEMA bruker som har fulle rettigheter " +
                          "til alle objektene i skjemaet."),
                  fieldWithPath("items[].labels").description("Labels som ble brukt som parametre under opprettelse.")
              )
          ))
  }

  def "Register external schema with labels"() {
    given:
      databaseHotelService.registerExternalSchema(
          EXAMPLE_SCHEMA_EXTERNAL.name,
          EXAMPLE_SCHEMA_EXTERNAL.users.first().password,
          EXAMPLE_SCHEMA_EXTERNAL.jdbcUrl, EXAMPLE_LABELS) >> EXAMPLE_SCHEMA_EXTERNAL

    when:
      String payload = JsonOutput.toJson([labels: EXAMPLE_LABELS, schema: [
          jdbcUrl : EXAMPLE_SCHEMA_EXTERNAL.jdbcUrl,
          username: EXAMPLE_SCHEMA_EXTERNAL.name,
          password: EXAMPLE_SCHEMA_EXTERNAL.users.first().password
      ]])
      ResultActions result = mockMvc.perform(post('/schema/').content(payload).contentType(APPLICATION_JSON))

    then:
      result
          .andExpect(status().isOk())
          .andDo(
          document('schemas-post-external',
              preprocessResponse(prettyPrint()),
              relaxedRequestFields(
                  fieldWithPath("labels").type(JsonFieldType.OBJECT).optional().
                      description(
                          "Labels til skjemaet som opprettes. Kan brukes til å indikere eier og andre metadata, og kan også brukes til filtrering."),
                  fieldWithPath("schema").type(JsonFieldType.OBJECT).
                      description(
                          "Informasjon om det eksterne skjemaet. Påkrevde verdier er 'username', 'password' og 'jdbcUrl'")
              ),
              relaxedResponseFields(
                  fieldWithPath("items[].id").
                      description("Unik id for dette skjemaet. Brukes til å slå opp skjemainformasjon senere."),
                  fieldWithPath("items[].type").
                      description(
                          "Hvorvidt dette skjemaet er håndtert av databasehotellet eller om det er et eksternt skjema. EXTERNAL i dette tilfellet."),
                  fieldWithPath("items[].jdbcUrl").
                      description("JDBC url som kan brukes for å koble til det genererte skjemaet."),
                  fieldWithPath("items[].users").description(
                      "List over genererte brukere som kan koble til skjemaet. Pt. genereres det kun en SCHEMA bruker som har fulle rettigheter " +
                          "til alle objektene i skjemaet."),
                  fieldWithPath("items[].labels").description("Labels som ble brukt som parametre under opprettelse."),
                  fieldWithPath("items[].databaseInstance").
                      description("Vil ikke inneholde informasjon for eksterne skjema.")
              )
          ))
  }

  def "Get schema by id"() {
    given:
      databaseHotelService.findSchemaById(EXAMPLE_SCHEMA_ID) >> Optional.of(Pair.of(EXAMPLE_SCHEMA, null))

    when:
      ResultActions result = mockMvc.perform(get("/schema/{id}", EXAMPLE_SCHEMA_ID))

    then:
      result
          .andExpect(status().isOk())
          .andDo(
          document('schemas-get-with-id',
              preprocessResponse(prettyPrint()),
              pathParameters(
                  parameterWithName("id").description("Skjemaid'en det skal hentes data for")
              )
          ))
  }

  def "List schemas"() {
    given:
      databaseHotelService.findAllDatabaseSchemas() >> EXAMPLE_RESPONSE

    when:
      ResultActions result = mockMvc.perform(get('/schema/'))

    then:
      result
          .andExpect(status().isOk())
          .andDo(document('schemas-get', preprocessResponse(prettyPrint())
      ))
  }

  def "List schemas with labels"() {
    given:
      databaseHotelService.findAllDatabaseSchemasByLabels([userId: USER_ID, affiliation: 'aurora']) >> EXAMPLE_RESPONSE


    when:
      String labelQuery = "userId=$USER_ID,affiliation=aurora"
      String labelQueryEncoded = URLEncoder.encode(labelQuery, "UTF-8")
      ResultActions result = mockMvc.perform(get("/schema/?labels=$labelQueryEncoded"))

    then:
      result
          .andExpect(status().isOk())
          .andDo(document('schemas-get-with-labels', preprocessResponse(prettyPrint()), requestParameters(
          parameterWithName("labels").description(
              "URL encoded liste med labels det skal filtreres på. Eks: `$labelQueryEncoded` Encoded fra `$labelQuery`"))))
  }

  def "Delete schema by id"() {
    given:
      databaseHotelService.deleteSchemaById(EXAMPLE_SCHEMA_ID, null)

    when:
      ResultActions result = mockMvc.perform(delete("/schema/{id}", EXAMPLE_SCHEMA_ID))

    then:
      result
          .andExpect(status().isOk())
          .andDo(
          document('schemas-delete',
              preprocessResponse(prettyPrint()),
              pathParameters(
                  parameterWithName("id").description("Id til skjema som skal slettes")
              )
          ))
  }

  def "ParseLabelsParam"() {

    expect:
      Map<String, String> labels = DatabaseSchemaController.parseLabelsParam(labelsParam)
      labels == expectedLabels

    where:
      labelsParam                                                    | expectedLabels
      "deploymentId%3Dsometestdeployment%2CresourceName%3Ddatabase1" |
          [deploymentId: 'sometestdeployment', resourceName: 'database1']
      "deploymentId=sometestdeployment,resourceName=database1"       |
          [deploymentId: 'sometestdeployment', resourceName: 'database1']
      "deploymentId=sometestdeployment,resourceName="                |
          [deploymentId: 'sometestdeployment', resourceName: null]
      "deploymentId=,resourceName="                                  | [deploymentId: null, resourceName: null]
      "deploymentId,resourceName"                                    | [deploymentId: null, resourceName: null]
      "deploymentId,resourceName=55"                                 | [deploymentId: null, resourceName: "55"]
      ""                                                             | [:]
      null                                                           | [:]
  }
}
