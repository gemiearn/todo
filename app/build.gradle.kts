plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.personal.todo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.personal.todo"
        minSdk = 26
        // Target Android 15 (API 35). Runs on Android 15 and 16 devices.
        targetSdk = 35

        versionCode = providers.gradleProperty("appVersionCode").get().toInt()
        versionName = providers.gradleProperty("appVersionName").get()

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Robolectric based tests run on the JVM and need the Android resources
        // of this module (string labels, ...).
        unitTests.isIncludeAndroidResources = true
        // 4 个 Compose 冒烟测试放在 `src/testDebug/`：只有 debug 变体的单测会编译并运行它们
        // （CI 跑的正是 testDebugUnitTest），release 变体的单测不跑。其余单测在 `src/test/`，
        // 两个变体都会跑。
        //
        // CI 把「门禁」和「整屏冒烟」拆成两步跑：
        //   ./gradlew testDebugUnitTest -PskipComposeSmoke  → 只跑 src/test 的逻辑用例（门禁）
        //   ./gradlew testDebugUnitTest                     → 连 src/testDebug 的冒烟一起跑
        // 冒烟测试要把整屏真的组合出来，靠的是 Robolectric 对布局 / 空闲的模拟，是整个测试里
        // 最不稳的一层：同一份代码在本机（Windows）0.1 秒过、在 CI 的 Linux 上偶发
        // `AppNotIdleException`。它红了先怀疑测试环境，不该把整个构建判红——详见
        // .github/workflows/android-ci.yml。
        // 约定：`src/testDebug/` 里新加的「整屏组合」测试请以 `SmokeTest` 结尾，否则它会
        // 被算进门禁那一步。
        if (providers.gradleProperty("skipComposeSmoke").isPresent) {
            unitTests.all { it.filter.excludeTestsMatching("*SmokeTest") }
        }
    }
}

// Room exports the schema of every version to app/schemas. Those JSON files are
// committed to Git: they are the reference for future migrations and migration
// tests.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    // Compose screens are composed on the JVM too: the smoke tests catch a
    // crash in the composition itself, which no pure logic test can see.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
