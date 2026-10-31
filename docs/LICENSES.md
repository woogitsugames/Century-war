# Licencje i komponenty zewnętrzne — Century War

**Zakres:** moduł Android `:app` (runtime wydania) i odrębny serwer `:server`. Aktualizacja: 31.10.2026, zlecenie 39.

**Stan weryfikacji:** wstępny inwentarz odtworzony z deklaracji `build.gradle.kts` i metadanych POM zależności, z uwzględnieniem wyboru najwyższych wymaganych wersji. **Nie był jeszcze potwierdzony poleceniami Gradle ani inspekcją finalnego APK/AAB** (środowisko lokalne nie ma Android SDK i dostępu do repozytoriów pobierania). Przed publikacją trzeba porównać każdą pozycję z wynikami poniższych komend i skorygować ewentualne różnice, nie zakładając zgodności bez uruchomienia procesu. Nie są to oświadczenia o uruchomieniu serwera.

## 1. Biblioteki aplikacji Android (APK / AAB)

Zależność zadeklarowana bezpośrednio: `com.google.android.gms:play-services-games-v2:22.1.0`. `:sim` jest kodem tego projektu i nie jest osobną biblioteką zewnętrzną. Gradle i plugin Android nie trafiają do APK jako biblioteki gry. `JUnit` i `Robolectric` są wyłącznie zależnościami testów.

**Uwaga:** brak bezpośredniej deklaracji AndroidX w aplikacji nie oznacza braku kodu AndroidX — moduły są wymagane przechodnio przez Play services. Pozycje tabeli to przewidywane węzły `releaseRuntimeClasspath`, a nie stwierdzenie, że R8 zachowuje w APK każdą klasę danego modułu.

| Artefakt Maven (grupa:moduł:wersja) | Właściciel | Licencja / odnośnik |
|---|---|---|
| `com.google.android.gms:play-services-games-v2:22.1.0` | Google LLC | [Android SDK License](https://developer.android.com/studio/terms) |
| `com.google.android.gms:play-services-base:18.5.0` | Google LLC | [Android SDK License](https://developer.android.com/studio/terms) |
| `com.google.android.gms:play-services-basement:18.9.0` | Google LLC | [Android SDK License](https://developer.android.com/studio/terms) |
| `com.google.android.gms:play-services-tasks:18.2.0` | Google LLC | [Android SDK License](https://developer.android.com/studio/terms) |
| `androidx.activity:activity:1.0.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.annotation:annotation:1.1.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.arch.core:core-common:2.1.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.arch.core:core-runtime:2.0.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.collection:collection:1.1.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.core:core:1.2.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.customview:customview:1.0.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.fragment:fragment:1.1.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.lifecycle:lifecycle-common:2.1.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.lifecycle:lifecycle-livedata:2.0.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.lifecycle:lifecycle-livedata-core:2.0.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.lifecycle:lifecycle-runtime:2.1.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.lifecycle:lifecycle-viewmodel:2.1.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.loader:loader:1.0.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.savedstate:savedstate:1.0.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.versionedparcelable:versionedparcelable:1.1.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.viewpager:viewpager:1.0.0` | Android Open Source Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `org.jetbrains.kotlin:kotlin-stdlib:2.2.20` | JetBrains | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `org.jetbrains:annotations:13.0` | JetBrains | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |

**Wykaz ekranowy:** `LicenseCatalog.app` w `app/src/main/java/com/centurywar/screens/AboutScreen.kt` odzwierciedla pozycje tej tabeli (23 rekordy), prezentując nazwę, wersję, właściciela, typ licencji oraz zewnętrzny odnośnik HTTPS. Google Play services jest objęte własnymi warunkami **Android SDK License**, nie wolno oznaczyć tych modułów jako Apache-2.0. Pozostałe moduły z tej tabeli są udostępniane na Apache-2.0.

**Tekst Apache-2.0:** `app/src/main/res/raw/apache_2_0.txt` (pełny tekst), dostępny z ekranu „O grze”; odczytywany w tle po naciśnięciu, nigdy w renderowaniu. W aplikacji nie ma zewnętrznych fontów, gotowych grafik ani nagrań — treści audiowizualne są tworzone w kodzie projektu.

## 2. Biblioteki backendu (tylko serwer, **nie** APK)

Bezpośrednie deklaracje w `server/build.gradle.kts`: `ktor-server-core-jvm` i `ktor-server-netty-jvm` 3.2.3, `kotlinx-serialization-json-jvm` 1.9.0 i `sqlite-jdbc` 3.50.3.0. `:sim` jest własnym kodem projektu. Poniżej rozpisano rozpoznane zależności bezpośrednie i typowe węzły przechodnie (w tym rodziny Netty / Ktor). To **nie jest jeszcze zatwierdzony wynik** `runtimeClasspath`; ostateczną listę i warianty platformowe (np. artefakty JNI Netty) trzeba wyprowadzić z raportu CI.

| Artefakt Maven | Właściciel | Licencja | Pochodzenie |
|---|---|---|---|
| `io.ktor:ktor-server-core-jvm:3.2.3` | JetBrains (Ktor) | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | bezpośrednia |
| `io.ktor:ktor-server-netty-jvm:3.2.3` | JetBrains (Ktor) | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | bezpośrednia |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0` | JetBrains | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | bezpośrednia |
| `org.xerial:sqlite-jdbc:3.50.3.0` | Xerial | [Apache-2.0; SQLite w domenie publicznej](https://www.apache.org/licenses/LICENSE-2.0) | bezpośrednia |
| `io.ktor:ktor-utils-jvm:3.2.3` | JetBrains (Ktor) | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.ktor:ktor-http-jvm:3.2.3` | JetBrains (Ktor) | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.ktor:ktor-serialization-jvm:3.2.3` | JetBrains (Ktor) | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.ktor:ktor-events-jvm:3.2.3` | JetBrains (Ktor) | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.ktor:ktor-http-cio-jvm:3.2.3` | JetBrains (Ktor) | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.ktor:ktor-websockets-jvm:3.2.3` | JetBrains (Ktor) | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.ktor:ktor-io-jvm:3.2.3` | JetBrains (Ktor) | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia (moduł I/O) |
| `io.ktor:ktor-network-jvm:3.2.3` | JetBrains (Ktor) | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia (sieć) |
| `io.ktor:ktor-network-tls-jvm:3.2.3` | JetBrains (Ktor) | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia (TLS) |
| `io.netty:netty-buffer:4.2.2.Final` | Netty Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.netty:netty-common:4.2.2.Final` | Netty Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.netty:netty-codec:4.2.2.Final` | Netty Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.netty:netty-codec-http:4.2.2.Final` | Netty Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.netty:netty-codec-http2:4.2.2.Final` | Netty Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.netty:netty-handler:4.2.2.Final` | Netty Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.netty:netty-resolver:4.2.2.Final` | Netty Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.netty:netty-transport:4.2.2.Final` | Netty Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.netty:netty-transport-classes-epoll:4.2.2.Final` | Netty Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.netty:netty-transport-classes-kqueue:4.2.2.Final` | Netty Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.netty:netty-transport-native-unix-common:4.2.2.Final` | Netty Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.netty:netty-transport-native-epoll:4.2.2.Final` | Netty Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `io.netty:netty-transport-native-kqueue:4.2.2.Final` | Netty Project | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `org.eclipse.jetty.alpn:alpn-api:1.1.3.v20160715` | Eclipse Jetty | [Apache-2.0 / EPL-1.0 (dual)](https://github.com/jetty/jetty-alpn-api) | przechodnia |
| `com.typesafe:config:1.4.3` | Lightbend / Typesafe | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `org.slf4j:slf4j-api:2.0.17` | QOS.ch | [MIT](https://www.slf4j.org/license.html) | przechodnia |
| `org.fusesource.jansi:jansi:2.4.2` | Jansi contributors | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `org.jetbrains.kotlin:kotlin-stdlib:2.2.20` | JetBrains | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `org.jetbrains.kotlin:kotlin-reflect:2.2.20` | JetBrains | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `org.jetbrains:annotations:13.0` | JetBrains | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2` | JetBrains | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.9.0` | JetBrains | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | przechodnia |

Dla `org.eclipse.jetty.alpn:alpn-api` obowiązują warunki **Apache-2.0 albo EPL-1.0** (dual license); licencji nie należy sprowadzać do jednostronnego MIT. `SLF4J` jest **MIT**, nie Apache-2.0. Driver JDBC Xerial zawiera natywne SQLite (SQLite jest w domenie publicznej). Zależność `slf4j-api` zgłoszona przez Xerial jako opcjonalna może być mimo to dostarczona przez Ktor.

## 3. Jak odtworzyć i zatwierdzić wykaz

W katalogu repozytorium, z JDK 17 i Android SDK 36:

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath --console=plain --no-configuration-cache > release-runtime-dependencies.txt
./gradlew :server:dependencies --configuration runtimeClasspath -PserverOnly=true --console=plain --no-configuration-cache > server-runtime-dependencies.txt
./gradlew :app:assembleRelease --console=plain --no-configuration-cache
```

Przy każdej zmianie wersji zależności:
1. Porównać **unikalne rozstrzygnięte** `group:artifact:version` z tabelą Android i ekranem `LicenseCatalog.app`; wersje z oznaczeniem `->` w drzewie oznaczają wybór innej wersji przez Gradle.
2. Porównać analogicznie tabelę serwera i sprawdzić licencje **wszystkich** nowych bibliotek, także natywnych; `serverOnly` wyklucza moduł `:app`.
3. Przejrzeć rzeczywiście zbudowany APK/AAB (`apkanalyzer` albo analiza pakietu), aby wyłapać ewentualne dołączane pliki lub zasoby.
4. W razie nowych licencji dołączyć wymagane notice/copyright. Sam link nie zawsze zastępuje obowiązek dołączenia pełnego notice.
5. Uruchomić testy `AboutScreenTest`, `LTest`, `ScreenshotTest.menus` i `:app:assembleDebug`.

Skrypt `scripts/check_license_inventory.py` porównuje rozstrzygnięte moduły aplikacji z `LicenseCatalog.app` i `docs/LICENSES.md`, a serwera z `docs/LICENSES.md`. Brak dowolnego modułu przerywa CI. Lista nie jest uważana za sprawdzoną, dopóki kontrola rzeczywiście się nie wykona.

Workflow `.github/workflows/android.yml` zapisuje oba drzewa Gradle jako artefakt **`release-runtime-dependencies`**, również gdy podstawowe testy kończą się niepowodzeniem. **Do czasu uzyskania i audytu artefaktów nie należy oznaczać inwentarza jako w pełni zweryfikowanego prawnie.**

## 4. Źródła licencji i deklaracji modułów

- Google Android SDK License: https://developer.android.com/studio/terms
- Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0
- AndroidX: https://source.android.com/docs/setup/about/licenses oraz https://developer.android.com/jetpack/androidx
- Kotlin / KotlinX / Ktor: https://github.com/JetBrains/kotlin, https://github.com/Kotlin/kotlinx.serialization, https://github.com/Kotlin/kotlinx.coroutines, https://github.com/ktorio/ktor
- Netty: https://github.com/netty/netty
- Xerial SQLite JDBC: https://github.com/xerial/sqlite-jdbc
- SLF4J (MIT): https://www.slf4j.org/license.html
- Jetty ALPN API: https://github.com/jetty/jetty-alpn-api
- Jansi: https://github.com/fusesource/jansi
- HOCON/Config: https://github.com/lightbend/config
