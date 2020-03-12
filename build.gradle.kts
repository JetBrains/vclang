plugins {
    java
    idea
}

var annotationsVersion: String by rootProject.ext
var protobufVersion: String by rootProject.ext
var antlrVersion: String by rootProject.ext

annotationsVersion = "19.0.0"
protobufVersion = "3.11.4"
antlrVersion = "4.8"

idea {
    module {
        outputDir = file("$buildDir/classes/java/main")
        testOutputDir = file("$buildDir/classes/java/test")
    }
}

allprojects {
    group = "org.arend"
    version = "1.2.0"
    repositories {
        jcenter()
        mavenCentral()
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "6.2.1"
}

dependencies {
    testImplementation("org.jetbrains:annotations:$annotationsVersion")
    testImplementation("org.antlr:antlr4-runtime:$antlrVersion")

    testImplementation(project(":api"))
    testImplementation(project(":base"))
    testImplementation(project(":parser"))
    testImplementation(project(":cli"))

    testImplementation("junit:junit:4.12")
    testImplementation("org.hamcrest:hamcrest-library:1.3")
}
