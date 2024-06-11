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
        maven { url=uri( "https://jitpack.io") }
        maven { url = uri("https://raw.github.com/saki4510t/libcommon/master/repository/") }

        google()
        mavenCentral()
        jcenter()
    }
}

rootProject.name = "WebRTCExample"
include(":app")
 