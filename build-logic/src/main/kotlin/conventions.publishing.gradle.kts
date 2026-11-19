plugins {
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name = project.name
                url = "https://github.com/Satherov/conventions"
            }
        }
    }
}
