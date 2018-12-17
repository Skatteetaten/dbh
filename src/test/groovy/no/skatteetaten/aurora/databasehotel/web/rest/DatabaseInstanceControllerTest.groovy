package no.skatteetaten.aurora.databasehotel.web.rest

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import org.junit.Rule
import org.springframework.restdocs.JUnitRestDocumentation
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.setup.MockMvcBuilders

import no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelAdminService
import no.skatteetaten.aurora.databasehotel.service.DatabaseInstance
import no.skatteetaten.aurora.databasehotel.service.JdbcUrlBuilder
import no.skatteetaten.aurora.databasehotel.service.ResourceUsageCollector
import spock.lang.Specification

class DatabaseInstanceControllerTest extends Specification {

  @Rule
  JUnitRestDocumentation restDocumentation = new JUnitRestDocumentation('build/docs/generated-snippets')

  def databaseHotelAdminService = Mock(DatabaseHotelAdminService)

  protected MockMvc mockMvc

  void setup() {
    def databaseInstanceController = new DatabaseInstanceController(databaseHotelAdminService)
    mockMvc = MockMvcBuilders.
        standaloneSetup(databaseInstanceController, new DeprecatedEndpoints(databaseInstanceController, null))
        .apply(documentationConfiguration(this.restDocumentation))
        .build()
  }

  def "List instances"() {
    given:
      databaseHotelAdminService.findAllDatabaseInstances() >> [new DatabaseInstance(
          new DatabaseInstanceMetaInfo("test", "dbhost.example.com", 1521, true),
          Mock(DatabaseManager),
          Mock(DatabaseHotelDataDao),
          Mock(JdbcUrlBuilder),
          Mock(ResourceUsageCollector),
          true,
          6,
          1
      )]

    when:
      ResultActions result = mockMvc.perform(get('/admin/databaseInstance/'))

    then:
      result
          .andExpect(status().isOk())
          .andDo(document('databaseInstance-get', preprocessResponse(prettyPrint())))
  }
}
