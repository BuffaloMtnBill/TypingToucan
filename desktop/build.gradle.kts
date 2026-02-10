plugins {
    kotlin("jvm")
    application
}

val gdxVersion: String by project

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-tools:$gdxVersion")
}

application {
    mainClass.set("com.typingtoucan.DesktopLauncherKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

sourceSets.main {
    resources.srcDirs("../android/assets")
}

tasks.register<JavaExec>("packTextures") {
    mainClass.set("com.typingtoucan.utils.PackTexturesKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    workingDir = File("..")
}
