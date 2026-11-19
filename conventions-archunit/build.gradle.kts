plugins {
    id("conventions.java")
    id("conventions.publishing")
    id("conventions.self-check")
}

dependencies {
    api(libs.archunit)
    api(libs.archunit.junit5)

    testImplementation(libs.jetbrains.annotations)
}
