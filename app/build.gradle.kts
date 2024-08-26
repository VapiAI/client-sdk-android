plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "ai.vapi.android"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("co.daily:client:0.22.1")
    implementation("com.google.code.gson:gson:2.10.1")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "ai.vapi.android"
                artifactId = "vapi"
                version = "0.0.1"

                pom {
                    name.set("Vapi")
                    description.set("Vapi Android SDK")
                    url.set("https://github.com/VapiAI/android")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set("jordancde")
                            name.set("Jordan Dearsley")
                            email.set("jordan@vapi.ai")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/VapiAI/android.git")
                        developerConnection.set("scm:git:ssh://github.com:VapiAI/android.git")
                        url.set("https://github.com/VapiAI/android")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "OSSRH"
                url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                credentials {
                    username = project.findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                    password = project.findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
                }
            }
        }
    }
}

signing {
    val signingKey: String? = project.findProperty("signingKey") as String? ?: System.getenv("GPG_PRIVATE_KEY")
    val signingPassword: String? = project.findProperty("signingPassword") as String? ?: System.getenv("GPG_PASSPHRASE")
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications)
}