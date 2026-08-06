plugins {
    `java-library`
    alias(libs.plugins.mavenPublish)
}

// group and version come from gradle.properties; a release workflow overrides
// version from the git tag with -Pversion=X.Y.Z.

java {
    toolchain {
        // Default JDK for dev machines; CI overrides with -PtoolchainJdk=21|25 to
        // prove the suite passes on the actual floor JDK, not just via --release.
        val toolchainJdk = providers.gradleProperty("toolchainJdk").map(String::toInt).getOrElse(25)
        languageVersion = JavaLanguageVersion.of(toolchainJdk)
    }
}

// Target Java 21 LTS: the toolchain compiles and runs tests on a newer JDK, but
// --release 21 checks all code against the Java 21 API and emits Java 21 class
// files, so the published jar (and the copy-pasted single file) runs on every
// JDK from 21 up.
tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// The published jar is directly runnable (java -jar tddoc-X.Y.Z.jar) and thus
// jbang-friendly; the copy-paste path (java SiteGen.java) is unaffected.
tasks.jar {
    manifest {
        attributes("Main-Class" to "dev.tddoc.SiteGen")
    }
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("source", "21")
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(group.toString(), "tddoc", version.toString())

    pom {
        name.set("tddoc")
        description.set(
            "tddoc — docs that are proven, not promised. Doctest-first article " +
                    "tooling for the JVM: every example is a passing test. Zero dependencies."
        )
        inceptionYear.set("2026")
        url.set("https://github.com/tibtof/tddoc")

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
            url.set("https://github.com/tibtof/tddoc")
            connection.set("scm:git:git://github.com/tibtof/tddoc.git")
            developerConnection.set("scm:git:ssh://git@github.com/tibtof/tddoc.git")
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Production code: ZERO dependencies. Only the standard library. The tool must
    // stay a single copy-paste-able file; a dependency would break that contract.

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
