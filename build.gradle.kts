import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "2.5.4"
	id("io.spring.dependency-management") version "1.0.11.RELEASE"
	id("org.sonarqube") version "3.3"
	kotlin("jvm") version "1.5.21"
	kotlin("plugin.spring") version "1.5.21"
}

group = "ru.devvault.client.vk"
version = "0.0.2-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenLocal()
	mavenCentral()
}

dependencies {
	implementation("com.vk.api:sdk:1.0.14") {
		exclude("org.apache.logging.log4j")
	}
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:2.6.3")
	implementation("org.springframework.boot:spring-boot-starter:2.6.3")
	implementation("org.springframework.boot:spring-boot-starter-validation:2.6.3")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
	implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.0")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.0")
	compileOnly("org.projectlombok:lombok:1.18.22")
	annotationProcessor("org.projectlombok:lombok:1.18.22")
	testImplementation("org.springframework.boot:spring-boot-starter-test:2.6.3")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "11"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}


sonarqube {
  properties {
    property("sonar.projectKey", "GTVolk_VkPoster")
  }
}
