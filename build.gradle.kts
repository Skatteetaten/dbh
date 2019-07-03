plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.40"
    id("org.jetbrains.kotlin.plugin.spring") version "1.3.40"
    id("org.jlleitschuh.gradle.ktlint") version "8.1.0"

    id("org.springframework.boot") version "2.1.6.RELEASE"

    // TODO: asciidoc
    // id("org.asciidoctor.convert") version "1.6.0"

    id("com.gorylenko.gradle-git-properties") version "2.0.0"
    id("com.github.ben-manes.versions") version "0.21.0"
    id("se.patrikerdes.use-latest-versions") version "0.2.9"

    id("no.skatteetaten.gradle.aurora") version "2.3.1"
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
    implementation("com.google.guava:guava:28.0-jre")
    implementation("com.oracle:ojdbc8:12.2.0.1")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")

    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.17")
    testImplementation("com.nhaarman:mockito-kotlin:1.6.0")
    testImplementation("no.skatteetaten.aurora:mockmvc-extensions-kotlin:0.6.5")

    val devtools = "org.springframework.boot:spring-boot-devtools"
    if (project.hasProperty("springBootDevtools")) {
        implementation(devtools)
    } else {
        testImplementation(devtools)
    }
}

tasks {
    test {
        val jenkinsUser: String? = System.getenv("JENKINS_USER")
        if (!jenkinsUser.isNullOrBlank()) {
            // We activate the ci profile when we build on Jenkins
            systemProperties["spring.profiles.active"] = "ci"
            systemProperties["test.include-oracle-tests"] = "true"
        }
    }
}
