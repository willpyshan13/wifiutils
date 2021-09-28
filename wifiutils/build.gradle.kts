import java.util.*

plugins {
    id(GradlePluginId.ANDROID_LIBRARY)
    kotlin("android")
    // Documentation for our code
    id(GradlePluginId.DOKKA) version GradlePluginVersion.DOKKA_VERSION
    // Maven publication
    `maven-publish`
}

android {
    compileSdk = AndroidConfig.COMPILE_SDK_VERSION
    //buildToolsVersion("30.0.3")


    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK_VERSION
        targetSdk = AndroidConfig.TARGET_SDK_VERSION

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {

        debug {
            isMinifyEnabled = BuildTypeDebug.isMinifyEnabled
            isDefault = true
        }

        release {
            isMinifyEnabled = BuildTypeRelease.isMinifyEnabled
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    addLibModuleDependencies()
    //addTestDependencies()
}

val sourcesJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles sources JAR"
    archiveClassifier.set("sources")
    from(android.sourceSets.getByName("main").java.srcDirs)
}

artifacts {
    archives(sourcesJar)
}

afterEvaluate {
    publishing {
        repositories {
            maven {
                url = uri("https://packages.aliyun.com/maven/repository/2132462-release-o7KSVF/")
                credentials {
                    username = "6127ae307cbfb3c62b93e6a8"
                    password = ""
                }
            }
        }
        publications {
            create<MavenPublication>("release") {
                // Applies the component for the release build variant.
                from(components["release"])
                artifactId = Artifact.ARTIFACT_NAME
                groupId = Artifact.ARTIFACT_GROUP
                version = Artifact.VERSION_NAME
                artifact(sourcesJar)
            }
        }
    }
}