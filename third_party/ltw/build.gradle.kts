plugins {
    id("com.android.library")
}

android {
    namespace = "com.insaneclient.ltw"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    defaultConfig {
        minSdk = 27
        ndk {
            abiFilters.addAll(setOf("armeabi-v7a", "arm64-v8a", "x86_64"))
        }
        externalNativeBuild {
            cmake {
                // The published app is development-signed, but shader translation
                // must stay optimized in that build as well as release builds.
                cFlags += "-O2"
                cppFlags += "-O2"
                // LTW is built from the pinned source in this module. Static helper
                // libraries avoid introducing another shared C++ runtime into the app.
                arguments(
                    "-DANDROID_STL=c++_static",
                    "-DBUILD_SHARED_LIBS=OFF",
                    "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384"
                )
                targets += "ltw"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("native/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
