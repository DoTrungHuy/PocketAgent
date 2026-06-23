import org.cyclonedx.model.Component

group = "com.pocketagent"
version = "0.2.4-alpha.1"

plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.kapt") version "2.0.21" apply false
    id("org.cyclonedx.bom") version "3.2.4"
}

tasks.cyclonedxBom {
    projectType.set(Component.Type.APPLICATION)
    componentName.set("PocketAgent")
    componentVersion.set("0.2.4-alpha.1")
    includeLicenseText.set(false)
    includeBuildSystem.set(true)
}

subprojects {
    tasks.cyclonedxDirectBom {
        includeConfigs.set(listOf("releaseRuntimeClasspath"))
        projectType.set(Component.Type.APPLICATION)
        includeLicenseText.set(false)
        includeMetadataResolution.set(true)
        includeBuildEnvironment.set(false)
    }
}
