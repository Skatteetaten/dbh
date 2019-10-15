import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.50"
    id("org.jetbrains.kotlin.plugin.spring") version "1.3.50"
    id("org.jlleitschuh.gradle.ktlint") version "8.2.0"
    id("org.sonarqube") version "2.7.1"
    id("org.asciidoctor.convert") version "2.3.0"

    id("org.springframework.boot") version "2.1.8.RELEASE"

    id("com.gorylenko.gradle-git-properties") version "2.0.0"
    id("com.github.ben-manes.versions") version "0.22.0"
    id("se.patrikerdes.use-latest-versions") version "0.2.12"
    id("com.adarshr.test-logger") version "1.7.0"

    id("no.skatteetaten.gradle.aurora") version "3.1.0"
}

repositories {
    flatDir {
        dirs("libs")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.apache.commons:commons-lang3")
    implementation("com.google.guava:guava:28.1-jre")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.oracle:ojdbc8:12.2.0.1")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")

    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("com.ninja-squad:springmockk:1.1.3")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.19")
    testImplementation("no.skatteetaten.aurora:mockmvc-extensions-kotlin:1.0.0")

    val devtools = "org.springframework.boot:spring-boot-devtools"
    if (project.hasProperty("springBootDevtools")) {
        implementation(devtools)
    } else {
        // Required to compile test code
        testImplementation(devtools)
    }
}

configurations.forEach {
    it.exclude("org.springframework.cloud", "spring-cloud-contract-verifier")
    it.exclude("junit", "junit")
}

testlogger {
    theme = ThemeType.PLAIN
}

tasks {
    val createSnippetsFolder by registering {
        doLast { File("$buildDir/generated-snippets").mkdirs() }
    }

    test {
        dependsOn(createSnippetsFolder)
        val jenkinsUser: String? = System.getenv("JENKINS_USER")
        if (!jenkinsUser.isNullOrBlank()) {
            // We activate the ci profile when we build on Jenkins
            systemProperties["spring.profiles.active"] = "ci"
            systemProperties["test.include-oracle-tests"] = "true"
        }
    }
}
