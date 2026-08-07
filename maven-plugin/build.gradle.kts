plugins {
    `java-library`
    alias(libs.plugins.mavenPluginDev)
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

mavenPlugin {
    goalPrefix.set("tddoc")
}

dependencies {
    implementation(project(":"))
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.plugin.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(group.toString(), "tddoc-maven-plugin", version.toString())

    pom {
        name.set("tddoc-maven-plugin")
        description.set("Maven plugin for tddoc — test-driven documentation. Thin wrapper over SiteGen.")
        inceptionYear.set("2026")
        url.set("https://github.com/tddoc/tddoc")
        packaging = "maven-plugin"
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
