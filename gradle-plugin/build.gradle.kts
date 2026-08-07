plugins {
    `java-gradle-plugin`
    alias(libs.plugins.mavenPublish)
}

java {
    toolchain {
        val toolchainJdk = providers.gradleProperty("toolchainJdk").map(String::toInt).getOrElse(25)
        languageVersion = JavaLanguageVersion.of(toolchainJdk)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("tddoc") {
            id = "dev.tddoc"
            implementationClass = "dev.tddoc.gradle.TddocPlugin"
            displayName = "tddoc"
            description = "Test-driven documentation: doc-tests in, article site out"
        }
    }
}

dependencies {
    implementation(project(":"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(gradleTestKit())
    testRuntimeOnly(libs.junit.platform.launcher)
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(group.toString(), "tddoc-gradle-plugin", version.toString())

    pom {
        name.set("tddoc-gradle-plugin")
        description.set("Gradle plugin for tddoc — test-driven documentation. Thin wrapper over SiteGen.")
        inceptionYear.set("2026")
        url.set("https://github.com/tddoc/tddoc")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit/")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("tibtof")
                name.set("Tiberiu Tofan")
                url.set("https://github.com/tibtof")
            }
        }
        scm {
            url.set("https://github.com/tddoc/tddoc")
            connection.set("scm:git:git://github.com/tddoc/tddoc.git")
            developerConnection.set("scm:git:ssh://git@github.com/tddoc/tddoc.git")
        }
    }
}

repositories {
    mavenCentral()
}
