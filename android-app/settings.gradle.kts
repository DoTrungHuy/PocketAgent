pluginManagement {
    val useChinaMirrors = providers.gradleProperty("agentpad.useChinaMirrors")
        .orNull
        .toBoolean()
    repositories {
        if (useChinaMirrors) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/public")
        } else {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    val useChinaMirrors = providers.gradleProperty("agentpad.useChinaMirrors")
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

val useChinaMirrors = providers.gradleProperty("agentpad.useChinaMirrors")
    .orNull
    .toBoolean()
if (useChinaMirrors) {
    println("AgentPad 构建源：阿里云 Maven 镜像（需与官方坐标保持一致）")
} else {
    println("AgentPad 构建源：Google Maven / Maven Central 官方源")
}

rootProject.name = "AgentPad"
include(":app")
