plugins {
    id("net.neoforged.moddev") version "2.0.78"
    id("com.modrinth.minotaur") version "2.+"
}

version = "2.0.0"
group = "vice"
base { archivesName = "sol_valheim" }

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.shedaniel.me/")
        content {
            includeGroup("me.shedaniel.cloth")
        }
    }
}

dependencies {
    implementation("me.shedaniel.cloth:cloth-config-neoforge:15.0.140")
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

modrinth {
    versionName = project.version.toString()
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set("sol-valheim")
    versionType.set("release")
    changelog.set(
        file("$rootDir/CHANGELOG.md")
            .readText()
            .split("###")[1]
            .let { x -> "###$x" }
    )
    gameVersions.add("1.21.1")
    loaders.add("neoforge")
    uploadFile.set(tasks.jar)
    dependencies {
        required.project("cloth-config")
        optional.project("farmers-delight")
    }
}
