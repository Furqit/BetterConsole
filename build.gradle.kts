plugins {
    java
    id("com.gradleup.shadow") version "9.3.+"
}

group = "dev.furq"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/")
}

dependencies {
    compileOnly(files("libs/hytale-api.jar"))
    compileOnly(files("libs/Hyxin-0.0.11.jar"))
    compileOnly("org.spongepowered:mixin:0.8.7")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    filesMatching("manifest.json") {
        expand(
            "version" to project.version,
            "name" to project.name
        )
    }
}
