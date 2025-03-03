plugins {
    `java-library`
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("xyz.jpenilla.run-velocity") version "2.3.1"

}

group = "prd.peurandel"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.mongodb:mongodb-driver-sync:5.3.1")
    implementation("org.litote.kmongo:kmongo-coroutine-serialization:5.2.0")

}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

// tasks.jar -> tasks.shadowJar 로 변경
tasks.shadowJar {
    archiveFileName.set("prdcore.jar") // 최종적으로 plugins 폴더에 생성될 파일 이름
    destinationDirectory.set(file("D:\\Project\\server\\1.21.4\\plugins")) // plugins 폴더 경로
}