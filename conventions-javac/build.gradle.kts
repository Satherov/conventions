plugins {
    id("conventions.java")
    id("conventions.publishing")
}

dependencies {
    testImplementation(libs.lombok)
    testImplementation(libs.jetbrains.annotations)
}
