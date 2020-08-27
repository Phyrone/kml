plugins {
    java
    kotlin("jvm")
    maven
}

group = "de.phyrone"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    testCompile("junit", "junit", "4.12")
    val slf4jVersion = "1.7.30"
    implementation("org.slf4j", "slf4j-api", slf4jVersion)
    testCompile("org.slf4j", "slf4j-simple", slf4jVersion)
    implementation(project(":kml-module-api"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}