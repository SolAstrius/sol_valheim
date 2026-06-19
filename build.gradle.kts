plugins {
    id("net.neoforged.moddev") version "2.0.78"
}

version = "1.1.2"
group = "vice"
base { archivesName = "sol_valheim" }

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories {
    mavenCentral()
}

neoForge {
    version = "21.1.233"

    parchment {
        mappingsVersion = "2024.11.17"
        minecraftVersion = "1.21.1"
    }

    runs {
        create("client") {
            client()
        }
        create("server") {
            server()
        }
    }

    mods {
        create("sol_valheim") {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}
