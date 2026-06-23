pluginManagement {
    val useChinaMirrors = providers.gradleProperty("pocketagent.useChinaMirrors")
        .orNull
        .toBoolean()
    repositories {
        if (useChinaMirrors) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/public")
            gradlePluginPortal()
        } else {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    val useChinaMirrors = providers.gradleProperty("pocketagent.useChinaMirrors")
        .orNull
        .toBoolean()
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useChinaMirrors) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
            maven("https://maven.aliyun.com/repository/public")
        } else {
            google()
            mavenCentral()
        }
    }
}

val useChinaMirrors = providers.gradleProperty("pocketagent.useChinaMirrors")
    .orNull
    .toBoolean()
if (useChinaMirrors) {
    println("PocketAgent build repositories: Aliyun Maven mirrors")
} else {
    println("PocketAgent build repositories: Google Maven / Maven Central")
}

rootProject.name = "PocketAgent"
include(":app")