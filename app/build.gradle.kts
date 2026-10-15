plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "sk.miro.blocky"
    compileSdk = 36

    // Číslo buildu z GitHub Actions → každý build má vyššie versionCode a dá sa nainštalovať ako aktualizácia.
    val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
    // owner/repo – v GitHub Actions sa doplní automaticky; lokálne cez gradle.properties: ghRepo=owner/repo
    val ghRepo = System.getenv("GITHUB_REPOSITORY") ?: (project.findProperty("ghRepo") as String? ?: "")
    // Len pre súkromný repozitár: read-only token zo secretu UPDATE_TOKEN
    val ghToken = System.getenv("UPDATE_TOKEN") ?: ""

    // Spoločná domácnosť (voliteľné): údaje servera Firebase zo súboru google-services.json,
    // ktorý stačí nahrať do repozitára (koreň alebo priečinok app). Bez neho aplikácia funguje, len bez domácnosti.
    // Používa sa len projectId a API kľúč – Firebase SDK ani plugin google-services sa nepoužívajú.
    val gsFile = listOf(rootProject.file("google-services.json"), file("google-services.json")).firstOrNull { it.exists() }
    var fbProject = ""
    var fbKey = ""
    if (gsFile != null) {
        @Suppress("UNCHECKED_CAST")
        val gs = groovy.json.JsonSlurper().parse(gsFile) as Map<String, Any?>
        fbProject = ((gs["project_info"] as? Map<String, Any?>)?.get("project_id") as? String) ?: ""
        val clients = (gs["client"] as? List<Map<String, Any?>>) ?: emptyList()
        val client = clients.firstOrNull {
            ((it["client_info"] as? Map<String, Any?>)?.get("android_client_info") as? Map<String, Any?>)?.get("package_name") == "sk.miro.blocky"
        } ?: clients.firstOrNull()
        fbKey = ((client?.get("api_key") as? List<Map<String, Any?>>)?.firstOrNull()?.get("current_key") as? String) ?: ""
    }

    defaultConfig {
        applicationId = "sk.miro.blocky"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        buildConfigField("String", "GH_REPO", "\"$ghRepo\"")
        buildConfigField("String", "GH_TOKEN", "\"$ghToken\"")
        buildConfigField("String", "FB_PROJECT_ID", "\"$fbProject\"")
        buildConfigField("String", "FB_API_KEY", "\"$fbKey\"")
        // len ARM telefóny (x86 je pre emulátory) – menšie APK
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Pevný testovací kľúč v repozitári: všetky buildy (CI aj lokálne) majú rovnaký podpis,
    // takže Android ich prijme ako aktualizáciu. Na Google Play by sa použil iný, tajný kľúč.
    // Kľúč na nahrávanie do Google Play – len v GitHub Secrets (RELEASE_KEYSTORE_*), nikdy v repozitári.
    val releaseStore = System.getenv("RELEASE_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }

    signingConfigs {
        if (releaseStore != null) create("release") {
            storeFile = releaseStore
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "upload"
            keyPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
        }
        create("test") {
            storeFile = file("keystore/test.keystore")
            storePassword = "blockytest"
            keyAlias = "blocky"
            keyPassword = "blockytest"
        }
    }

    buildTypes {
        // CI zostavuje „debug“ verziu – zapnuté zmenšenie kódu (R8) a zdrojov, aby APK malo pár MB, nie 40.
        debug {
            signingConfig = signingConfigs.getByName("test")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Verzia pre Google Play (AAB): bez samoaktualizácie z GitHubu, podpísaná kľúčom na nahrávanie.
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("test")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "GH_REPO", "\"\"")
            buildConfigField("String", "GH_TOKEN", "\"\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // knižnica ML Kit GenAI môže byť zostavená novším Kotlinom – kontrola verzie metadát by zbytočne zlyhala
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Kamera + QR
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    // ZXing – čítanie QR priamo v telefóne, bez Google služieb a bez odosielania čohokoľvek
    implementation("com.google.zxing:core:3.5.3")

    // AI telefónu (Gemini Nano) – použije sa len na podporovaných telefónoch a len ak ju používateľ zapne
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")

    // Databáza
    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Sieť
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
