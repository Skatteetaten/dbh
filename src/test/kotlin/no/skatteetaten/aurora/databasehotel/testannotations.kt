package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.dao.DatabaseInstanceInitializer
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@EnabledIfSystemProperty(named = "test.include-oracle-tests", matches = "true")
@TestPropertySource(locations = ["file:\${HOME}/.spring-boot-devtools.properties"])
internal annotation class OracleTest

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(SpringExtension::class)
@JsonTest
@ContextConfiguration(classes = [OracleConfig::class, PostgresConfig::class, TestDataSources::class, DatabaseInstanceInitializer::class])
@TestPropertySource(locations = ["file:\${HOME}/.spring-boot-devtools.properties"])
internal annotation class DatabaseTest
