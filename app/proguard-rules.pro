# Väčšina knižníc (Room, WorkManager, ML Kit, OkHttp, Compose) dodáva vlastné pravidlá.
# Doplnkové pravidlá pre istotu:

# WorkManager vytvára worker reflexiou podľa názvu triedy
-keep class sk.miro.blocky.data.FetchWorker { <init>(...); }

# OkHttp – voliteľné TLS knižnice, ktoré nepoužívame
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Čitateľnejšie stack trace v prípade chyby
-keepattributes SourceFile,LineNumberTable
-keep class sk.miro.blocky.sync.SyncWorker { <init>(...); }
# ML Kit GenAI (AI telefónu) – voliteľné triedy, ktoré na niektorých zariadeniach chýbajú
-dontwarn com.google.mlkit.genai.**
