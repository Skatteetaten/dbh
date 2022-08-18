plugins {
    id("java")
    id("no.skatteetaten.gradle.aurora") version "4.5.4"
}

aurora {
    useKotlinDefaults
    useSpringBootDefaults
    useAsciiDoctor

    versions {
        javaSourceCompatibility = "17"
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.apache.commons:commons-lang3")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.1")
    implementation("com.oracle:ojdbc8:18.3")
    implementation("org.postgresql:postgresql:42.4.1")
    implementation("org.flywaydb:flyway-core:9.0.4")

    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation("io.mockk:mockk:1.12.5")
    testImplementation("com.ninja-squad:springmockk:3.1.1")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.25")
    testImplementation("no.skatteetaten.aurora:mockmvc-extensions-kotlin:1.1.8")
    testImplementation("no.skatteetaten.aurora:mockwebserver-extensions-kotlin:1.3.1")

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
