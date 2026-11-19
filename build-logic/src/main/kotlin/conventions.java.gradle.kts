plugins {
    `java-library`
}

val libs = the<VersionCatalogsExtension>().named("libs")

val javaVersion = 21

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
    withSourcesJar()
}

dependencies {
    api(libs.findLibrary("jspecify").get())
    api(libs.findLibrary("error-prone-annotations").get())

    compileOnly(libs.findLibrary("jetbrains-annotations").get())
    compileOnly(libs.findLibrary("lombok").get())
    annotationProcessor(libs.findLibrary("lombok").get())
    testCompileOnly(libs.findLibrary("jetbrains-annotations").get())
    testCompileOnly(libs.findLibrary("lombok").get())
    testAnnotationProcessor(libs.findLibrary("lombok").get())

    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("assertj-core").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
    options.compilerArgs.addAll(
        listOf(
            "-parameters",
            "-Xlint:all,-processing,-serial",
            "-Werror",
        )
    )
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes("Implementation-Version" to project.version)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
