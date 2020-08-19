import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.72"
    id("org.jetbrains.kotlin.plugin.spring") version "1.3.72"
    id("org.jlleitschuh.gradle.ktlint") version "9.3.0"
    id("org.sonarqube") version "3.0"

    id("org.springframework.boot") version "2.3.3.RELEASE"

    id("com.gorylenko.gradle-git-properties") version "2.2.3"
    id("com.github.ben-manes.versions") version "0.29.0"
    id("se.patrikerdes.use-latest-versions") version "0.2.14"
    id("com.adarshr.test-logger") version "2.1.0"

    id("no.skatteetaten.gradle.aurora") version "3.6.6"
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
    implementation("com.google.guava:guava:29.0-jre")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.oracle:ojdbc8:18.3")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")

    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation("io.mockk:mockk:1.10.0")
    testImplementation("com.ninja-squad:springmockk:2.0.3")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.22")
    testImplementation("no.skatteetaten.aurora:mockmvc-extensions-kotlin:bugfix_AOS_4800-SNAPSHOT")

    val devtools = "org.springframework.boot:spring-boot-devtools"
    if (project.hasProperty("springBootDevtools")) {
        implementation(devtools)
    } else {
        // Required to compile test code
        testImplementation(devtools)
    }
}

configurations.forEach {
    it.exclude("org.junit.vintage", "junit-vintage-engine")
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
