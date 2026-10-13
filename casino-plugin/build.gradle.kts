plugins {
    java
}

group = "dev.casino"
version = "1.0.0"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // WICHTIG: Version auf die aktuelle Paper-API für 26.3 anpassen (siehe docs.papermc.io)
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
}

java {
    // Neue Minecraft-Versionen brauchen ein neues Java – ggf. anpassen (Paper-Doku)
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
