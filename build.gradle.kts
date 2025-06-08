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
    maven("https://repo.codemc.org/repository/maven-public/") {

    }
    maven("https://repo.dmulloy2.net/repository/public/"){

    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect") // 리플렉션 라이브러리 추가
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.reflections:reflections:0.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2") // 코루틴 코어
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.10.2") // 코루틴 리액티브
    
    // MongoDB 표준 드라이버 (버전 통일)
    implementation("org.mongodb:mongodb-driver-sync:5.1.4")
    implementation("org.mongodb:bson:5.1.4") // BSON 문서 처리
    
    // MongoDB Kotlin 확장 (KMongo 대체)
    implementation("org.mongodb:bson-kotlin:5.1.4")
    
    // GUI 관련
    implementation("net.wesjd:anvilgui:1.10.5-SNAPSHOT")

    //ProtocolLib
    implementation("com.comphenix.protocol:ProtocolLib:4.8.0")
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
    destinationDirectory.set(file("D:\\server\\minecraft_server_data\\plugins")) // plugins 폴더 경로
}
