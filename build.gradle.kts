/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    java
    `maven-publish`
    kotlin("jvm") version "1.6.10"
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10")
    }
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }

    maven {
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.10")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-common:1.6.10")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.0.0.202111291000-r")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.4.2")
    implementation("org.spigotmc:spigot-api:1.18.1-R0.1-SNAPSHOT")
}

group = "kiwi.hoonkun.plugins"
version = "1.0"
description = "Pixel"
java.sourceCompatibility = JavaVersion.VERSION_1_8

sourceSets.main {
    java.srcDirs("src/main/kotlin")
    resources {
        java.srcDirs("src/main/resources")
    }
}

sourceSets.test {
    java.srcDirs("src/test/kotlin")
    resources {
        java.srcDirs("src/test/resources")
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<Jar> {
    from({
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    }) {
        exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    }
}
