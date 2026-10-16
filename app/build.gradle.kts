plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// کوچک‌سازی کد با R8 روی ماشین‌های کم‌حافظه (>۱٫۵GB لازم دارد) خاموش است.
// روی ماشین قوی اجرا کنید:   ./gradlew :app:assembleRelease -PnzMinify=true
val enableMinify = (project.findProperty("nzMinify") as String?)?.toBoolean() ?: false
// برای انتشار، دو ABI گوشی را با -PnzAbis=arm64-v8a,armeabi-v7a محدود کنید.
val buildAbis = (project.findProperty("nzAbis") as String?)
    ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
    ?: listOf("arm64-v8a", "armeabi-v7a", "x86_64")
val buildUniversalApk = (project.findProperty("nzUniversalApk") as String?)?.toBoolean() ?: true

android {
    namespace = "nzvpn.nzbozorg.ir"
    compileSdk = 36

    defaultConfig {
        applicationId = "nzvpn.nzbozorg.ir"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.1.0"

        // universal APK در غیر این صورت همهٔ ABIهای موجود در AAR را بسته‌بندی
        // می‌کند، حتی اگر splits.include فقط دو ABI را نام ببرد.
        ndk { abiFilters += buildAbis }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // امضای نسخهٔ انتشار: مقادیر از gradle.properties یا خط فرمان (-P) خوانده می‌شوند
    //   nzKeystore / nzKeystorePassword / nzKeyAlias / nzKeyPassword
    // اگر تنظیم نشده باشند، نسخهٔ release بدون امضا ساخته می‌شود (نصب‌نشدنی).
    val hasSigning = listOf("nzKeystore", "nzKeystorePassword", "nzKeyAlias", "nzKeyPassword")
        .all { (project.findProperty(it) as String?).orEmpty().isNotBlank() }

    signingConfigs {
        if (hasSigning) {
            create("nzRelease") {
                storeFile = rootProject.file(project.property("nzKeystore") as String)
                storePassword = project.property("nzKeystorePassword") as String
                keyAlias = project.property("nzKeyAlias") as String
                keyPassword = project.property("nzKeyPassword") as String
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = enableMinify
            isShrinkResources = enableMinify
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasSigning) signingConfig = signingConfigs.getByName("nzRelease")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // کتابخانهٔ هستهٔ Xray نیاز دارد کتابخانه‌های نیتیو روی دیسک استخراج شوند
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // APK جدا برای هر معماری (هستهٔ Xray ۳۷ مگابایت به‌ازای هر ABI است)
    // خروجی: app-arm64-v8a-debug.apk و ... + یک APK universal
    splits {
        abi {
            isEnable = true
            reset()
            include(*buildAbis.toTypedArray())
            isUniversalApk = buildUniversalApk
        }
    }

    androidResources {
        // فقط زبان‌های فارسی و انگلیسی در APK بمانند (به‌جای resourceConfigurations منسوخ)
        localeFilters += listOf("fa", "en")
    }

    lint {
        abortOnError = false
        // آنالیز lint نسخهٔ release حافظه‌ی بسیار زیادی می‌خواهد و در محیط کم‌حافظه
        // باعث مرگ daemon می‌شد؛ روی ماشین قوی می‌توانید true کنید.
        checkReleaseBuilds = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // ------------------------------------------------------------------
    //  هستهٔ Xray (libv2ray.aar). پیش از build با این دستور بگیرید:
    //    bash tools/fetch-core.sh
    //  یا فایل را دستی در app/libs/libv2ray.aar بگذارید.
    // ------------------------------------------------------------------
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.okhttp)
    implementation(libs.work)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // org.json واقعی برای آزمون پارسِ انتشار گیت‌هاب (android.jar در JVM فقط stub است).
    testImplementation("org.json:json:20240303")
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// ---------------------------------------------------------------------------
//  اگر هستهٔ Xray دانلود نشده باشد، پیام واضح فارسی می‌دهیم
// ---------------------------------------------------------------------------
val coreAar = layout.projectDirectory.file("libs/libv2ray.aar").asFile
tasks.matching { it.name == "preBuild" }.configureEach {
    doFirst {
        if (!coreAar.exists()) {
            throw GradleException(
                """
                |========================================================
                |  فایل app/libs/libv2ray.aar پیدا نشد.
                |  این فایل، هستهٔ Xray است (نسخهٔ اندروید) و بدون آن
                |  برنامه ساخته نمی‌شود.
                |
                |  اجرا کنید:
                |      bash tools/fetch-core.sh
                |
                |  یا دستی دانلود کنید:
                |      curl -L -o app/libs/libv2ray.aar \
                |        https://github.com/2dust/AndroidLibXrayLite/releases/latest/download/libv2ray.aar
                |========================================================
                """.trimMargin(),
            )
        }
    }
}
