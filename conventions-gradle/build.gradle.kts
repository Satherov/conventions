plugins {
    id("conventions.java")
    `java-gradle-plugin`
    `maven-publish`
    id("conventions.self-check")
}

gradlePlugin {
    plugins {
        create("conventions") {
            id = "dev.satherov.conventions"
            implementationClass = "dev.satherov.conventions.gradle.ConventionsPlugin"
        }
    }
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("**/conventions.properties") {
        expand("version" to version)
    }
}
