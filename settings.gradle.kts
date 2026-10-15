pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "RP-Ideas"
include(":cleaner")
include(":equalizer")
include(":radio")
include(":vocalremover")
include(":secretmessage")
project(":secretmessage").projectDir = file("secretmessage/product")
include(":oorarivippu")
