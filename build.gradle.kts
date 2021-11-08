plugins {
    id("java")
    id("no.skatteetaten.gradle.aurora") version "4.3.22"
}

aurora {
    useKotlinDefaults
    useSpringBootDefaults
    useAsciiDoctor
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.apache.commons:commons-lang3")
    implementation("com.github.ben-manes.caffeine:caffeine:3.0.4")
    implementation("com.oracle:ojdbc8:18.3")
    implementation("org.postgresql:postgresql:42.3.1")
    implementation("org.flywaydb:flyway-core:8.0.2")

    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation("io.mockk:mockk:1.12.0")
    testImplementation("com.ninja-squad:springmockk:3.0.1")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.25")
    testImplementation("no.skatteetaten.aurora:mockmvc-extensions-kotlin:1.1.7")
    testImplementation("no.skatteetaten.aurora:mockwebserver-extensions-kotlin:1.2.0")

    val devtools = "org.springframework.boot:spring-boot-devtools"
    if (project.hasProperty("springBootDevtools")) {
        implementation(devtools)
    } else {
        // Required to compile test code
        testImplementation(devtools)
    }
}
repositories {
    mavenCentral()
}

configurations.forEach {
    it.exclude("org.junit.vintage", "junit-vintage-engine")
    it.exclude("org.springframework.cloud", "spring-cloud-contract-verifier")
    it.exclude("junit", "junit")
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
