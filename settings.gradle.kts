pluginManagement {
    includeBuild("build-logic")
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "conventions"

include(
    "conventions-archunit",
    "conventions-gradle",
    "conventions-javac"
)
