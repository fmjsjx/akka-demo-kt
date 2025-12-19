import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
    val kotlinVersion = "2.3.0"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
}

group = "com.hiscene.test"
version = "0.0.1-SNAPSHOT"
description = "akka-demo-kt"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

buildscript {
    repositories {
        maven { url = uri("https://repo.akka.io/sSbM-bDRsHOkMSUm-i1GvOVl4J2IFAVa2Sox8IJx88R88QPs/secure") }
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        gradlePluginPortal()
        mavenCentral()
    }
}

repositories {
    maven { url = uri("https://repo.akka.io/sSbM-bDRsHOkMSUm-i1GvOVl4J2IFAVa2Sox8IJx88R88QPs/secure") }
    maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
    mavenCentral()
}

extra["kotlin.version"] = "2.3.0"
extra["kotlin-coroutines.version"] = "1.10.2"

dependencies {
    implementation(platform("com.github.fmjsjx:libcommon-bom:4.0.0-RC"))
    val scalaBinaryVersion = "2.13"
    implementation(platform("com.typesafe.akka:akka-bom_${scalaBinaryVersion}:2.10.12"))

    implementation("com.typesafe.akka:akka-actor-typed_${scalaBinaryVersion}")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-r2dbc")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("com.typesafe.akka:akka-actor-testkit-typed_${scalaBinaryVersion}")
    testImplementation("org.springframework.boot:spring-boot-starter-data-r2dbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-r2dbc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        extraWarnings = true
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
            "-opt-in=kotlin.RequiresOptIn",
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
