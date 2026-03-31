import java.util.Properties

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use(::load)
    }
}

val amapApiKey = localProperties.getProperty("amap.api.key", "")
val iflytekAppId = localProperties.getProperty("iflytek.appid", "")
val iflytekApiKey = localProperties.getProperty("iflytek.api_key", "")
val iflytekApiSecret = localProperties.getProperty("iflytek.api_secret", "")
val iflytekLlmModel = localProperties.getProperty("iflytek.llm_model", "generalv3.5")
val iflytekMaasApiKey = localProperties.getProperty("iflytek.maas_api_key", "")
val iflytekMaasModelId = localProperties.getProperty("iflytek.maas_model_id", "")
val iflytekMaasBaseUrl = localProperties.getProperty(
    "iflytek.maas_base_url",
    "https://maas-api.cn-huabei-1.xf-yun.com/v2"
)
val hasIflytekConfig = iflytekAppId.isNotBlank() &&
    iflytekApiKey.isNotBlank() &&
    iflytekApiSecret.isNotBlank()

android {
    namespace = "com.example.caremap"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.caremap"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey
        buildConfigField("boolean", "HAS_AMAP_API_KEY", amapApiKey.isNotBlank().toString())
        buildConfigField("String", "IFLYTEK_APP_ID", iflytekAppId.asBuildConfigString())
        buildConfigField("String", "IFLYTEK_API_KEY", iflytekApiKey.asBuildConfigString())
        buildConfigField("String", "IFLYTEK_API_SECRET", iflytekApiSecret.asBuildConfigString())
        buildConfigField("String", "IFLYTEK_LLM_MODEL", iflytekLlmModel.asBuildConfigString())
        buildConfigField("String", "IFLYTEK_MAAS_API_KEY", iflytekMaasApiKey.asBuildConfigString())
        buildConfigField("String", "IFLYTEK_MAAS_MODEL_ID", iflytekMaasModelId.asBuildConfigString())
        buildConfigField("String", "IFLYTEK_MAAS_BASE_URL", iflytekMaasBaseUrl.asBuildConfigString())
        buildConfigField("boolean", "HAS_IFLYTEK_CONFIG", hasIflytekConfig.toString())
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.amap3dmap)
    implementation("com.google.code.gson:gson:2.8.8")
    implementation(files("libs/SparkChain.aar"))
    implementation(files("libs/Codec.aar"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
