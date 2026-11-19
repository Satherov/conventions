plugins {
    java
}

val selfCheck = configurations.create("selfCheck") {
    isCanBeConsumed = false
    isCanBeResolved = false
}

dependencies {
    "selfCheck"(project(":conventions-javac"))
}

configurations.annotationProcessor {
    extendsFrom(selfCheck)
}

configurations.testAnnotationProcessor {
    extendsFrom(selfCheck)
}

tasks.compileJava {
    options.compilerArgs.add("-Xplugin:Conventions root=dev.satherov.conventions set=main")
}

tasks.compileTestJava {
    options.compilerArgs.add("-Xplugin:Conventions root=dev.satherov.conventions set=test")
}
