# AssistiPunkt (Assistive Tap)

> **Ihr Tipp-Helfer zur Navigation** – Eine barrierefreie Android-App für intuitive Navigation mit einem schwebendem Punkt

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org/)
[![API Level](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 📱 Über die App

**AssistiPunkt** (international: *Assistive Tap*) ist eine barrierefreie Android-App, die Menschen mit eingeschränkter Mobilität oder motorischen Schwierigkeiten die Smartphone-Navigation erleichtert. Ein schwebender, anpassbarer Punkt ermöglicht mit einfachen Gesten die Steuerung aller wichtigen Navigationsfunktionen.

### ✨ Hauptfunktionen

- **🎯 Zwei Modi**: Normaler Navigationspunkt oder Rettungsring für Notfälle
- **🛟 Rettungsring-Modus**: Großer Rettungsring (🛟) schließt aktuelle App und geht zur Startseite
- **🎯 Schwebender Punkt**: Frei positionierbarer, anpassbarer Navigationspunkt über allen Apps
- **👆 Intuitive Gesten** (Normal-Modus):
  - 1x tippen → Zurück
  - 2x tippen → Letzte App
  - 3x tippen → Alle offenen Apps
  - 4x tippen → App öffnen
  - Lang drücken → Startseite
- **⌨️ Tastatur-Vermeidung**: Punkt weicht automatisch der Bildschirmtastatur aus
- **🎨 Anpassbar**: Farbe, Durchsichtigkeit und Position frei wählbar
- **⚡ Konfigurierbar**: App-Wechsel-Geschwindigkeit einstellbar (50-300ms)
- **♿ Barrierefrei**: Nach WCAG 2.1 Level AA optimiert

## 🖼️ Screenshots

<p align="center">
  <img src="screenshots/Screenshot_20251102_173223_Assistive Tap.jpg" width="30%" alt="Hauptbildschirm" />
  <img src="screenshots/Screenshot_20251102_173243_Assistive Tap.jpg" width="30%" alt="Hauptbildschirm mit Retungsring" />
  <img src="screenshots/Screenshot_20251102_173305_Assistive Tap.jpg" width="30%" alt="Einstellungen" />
  <img src="screenshots/floating_dot.jpg" width="30%" alt="AssistiPunkt in Aktion" />
</p>

<p align="center">
  <em>Hauptbildschirm • Rettungsring • Einstellungen • AssistiPunkt in Aktion</em>
</p>

## 🚀 Installation

### Voraussetzungen
- Android 8.0 (API Level 26) oder höher
- Zwei Berechtigungen erforderlich:
  - **Overlay-Berechtigung**: Für den schwebenden Punkt
  - **Bedienungshilfe-Zugriff**: Für Navigationsaktionen

### Download & Installation

1. APK von [Releases](../../releases) herunterladen
2. APK auf dem Gerät installieren
3. App öffnen und den Anweisungen folgen
4. Berechtigungen erteilen:
   - Overlay-Berechtigung aktivieren
   - "AssistiPunkt" in den Bedienungshilfen einschalten

## 🎮 Verwendung

### Modi auswählen

**AssistiPunkt** bietet zwei Modi für verschiedene Situationen:

#### 🟢 Normal-Modus (Navigation)
- Gefärbter Punkt für alltägliche Navigation
- Alle Gesten-Steuerungen verfügbar
- Ideal für präzise Steuerung

#### 🔴 Rettungsring-Modus (Notfall)
- Großer weißer Rettungsring mit roter Emoji (🛟)
- Einfache Notfall-Funktion: Tippen schließt aktuelle App und geht zur Startseite
- Perfekt für schnelle Flucht aus schwierigen Situationen

### Grundlegende Nutzung

1. **Modus wählen**: Rettungsring-Schalter auf dem Hauptbildschirm aktivieren/deaktivieren
2. **Punkt aktivieren**: Ein/Aus-Schalter in der App
3. **Punkt positionieren**: Punkt gedrückt halten und verschieben
4. **Navigation**: Mit verschiedenen Tipp-Gesten navigieren (im Normal-Modus)
5. **Anpassen**: Über "⚙ Einstellungen" Farbe und Verhalten ändern

### Automatische Funktionen

- **Tastatur-Vermeidung**: Der Punkt bewegt sich automatisch nach oben, wenn die Bildschirmtastatur erscheint
- **Bildschirm-Rotation**: Der Punkt bleibt an der gleichen physischen Position

### Erweiterte Einstellungen (für Experten)

Im Bereich "Für Experten" können folgende Parameter angepasst werden:
- **Durchsichtigkeit**: Transparenz des Punktes (0-100%)
- **Wechsel-Geschwindigkeit**: Verzögerung beim App-Wechsel (50-300ms)

## 🛠️ Technische Details

### 🏗️ Architektur

**AssistiPunkt** folgt den **Clean Architecture** Prinzipien mit klarer Trennung der Verantwortlichkeiten:

```
AssistiPunkt/
├── domain/                    # Geschäftslogik & Modelle
│   ├── model/
│   │   ├── DotPosition.kt     # Position-Modell
│   │   ├── Gesture.kt         # Gesten-Enumeration
│   │   └── OverlaySettings.kt # Einstellungen-Modell
│   └── repository/
│       └── SettingsRepository.kt # Daten-Zugriffs-Interface
├── data/                      # Daten-Zugriffsschicht
│   ├── local/
│   │   └── SharedPreferencesDataSource.kt # SharedPreferences-Implementierung
│   └── repository/
│       └── SettingsRepositoryImpl.kt # Repository-Implementierung
├── service/                   # Service-Komponenten
│   └── overlay/
│       ├── OverlayService.kt  # Hauptservice (Lifecycle-Management)
│       ├── KeyboardDetector.kt # Tastatur-Erkennung
│       ├── GestureDetector.kt  # Gesten-Erkennung
│       └── OverlayViewManager.kt # Overlay-Verwaltung
├── ui/                        # Benutzeroberfläche
│   ├── MainActivity.kt        # Hauptansicht & Berechtigungen
│   ├── SettingsActivity.kt    # Einstellungen
│   └── ImpressumActivity.kt   # Impressum
├── util/                      # Hilfsfunktionen
│   └── AppConstants.kt        # Zentralisierte Konstanten
├── di/                        # Dependency Injection
│   ├── ServiceLocator.kt      # Manuelle DI (ServiceLocator-Pattern)
│   └── AppModule.kt           # Hilt-Modul (für zukünftige Migration)
└── BackHomeAccessibilityService.kt # Accessibility Service
```

### 🧩 Architektur-Prinzipien

- **🧹 Clean Architecture**: Strenge Trennung zwischen Domain, Data und Presentation Layer
- **🔄 Dependency Inversion**: Abhängigkeiten zeigen nur nach innen (Domain)
- **📦 Single Responsibility**: Jede Klasse hat genau eine Verantwortlichkeit
- **🧪 Testability**: Komponenten sind isoliert testbar
- **🔧 Dependency Injection**: Lose Kopplung durch ServiceLocator (bereit für Hilt-Migration)

### 📱 Service-Komponenten

#### OverlayService (Hauptservice)
- **Verantwortlichkeit**: Lifecycle-Management und Komponenten-Orchestrierung
- **Reduziert**: Von 507 auf ~194 Zeilen (62% Reduktion)
- **Pattern**: Composition über Vererbung

#### KeyboardDetector
- **Funktion**: Tastatur-Sichtbarkeit und Höhen-Erkennung
- **APIs**: WindowInsets (Android R+), InputMethodManager
- **Fallback**: Heuristische Schätzung bei API-Limitierungen

#### GestureDetector
- **Funktion**: Touch-Gesten-Erkennung und -Verarbeitung
- **Gesten**: Tap, Double-Tap, Triple-Tap, Quadruple-Tap, Long-Press, Drag
- **Timeouts**: System-konforme Timeouts für natürliches Feeling

#### OverlayViewManager
- **Funktion**: Overlay-Erstellung, Positionierung und Darstellung
- **Features**: Automatische Tastatur-Vermeidung, Bildschirm-Rotation-Handling
- **Rendering**: WindowManager mit TYPE_APPLICATION_OVERLAY

### 💾 Daten-Management

#### Reactive Data Flow
- **Kotlin Flows**: Reaktive Datenströme für Echtzeit-Updates
- **SharedPreferences**: Persistente Datenspeicherung
- **Repository Pattern**: Abstraktion der Daten-Zugriffsschicht

#### Einstellungen-Struktur
```kotlin
data class OverlaySettings(
    val isEnabled: Boolean,
    val color: Int,
    val alpha: Int,
    val position: DotPosition,
    val positionPercent: DotPositionPercent,
    val recentsTimeout: Long,
    val keyboardAvoidanceEnabled: Boolean,
    val rescueRingEnabled: Boolean,
    val screenWidth: Int,
    val screenHeight: Int,
    val rotation: Int
)
```

### 🔧 Technologie-Stack

- **Sprache**: Kotlin 1.9+
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 36
- **UI Framework**: Material Design 3
- **Architecture**: Clean Architecture mit ServiceLocator DI
- **Async**: Kotlin Coroutines + Flows
- **Build**: Gradle Kotlin DSL
- **Testing**: JUnit 4 + Mockito (bereit für Erweiterung)

### 📡 Verwendete Android-APIs

- **Overlay API**: `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`
- **Accessibility API**: `AccessibilityService` für System-Navigation
- **WindowInsets API**: Tastatur-Höhen-Erkennung (Android R+)
- **SharedPreferences**: Persistente Konfiguration
- **Gesture Detection**: Custom Touch-Handler mit System-Timeouts
- **LocalBroadcastManager**: Interne Kommunikation (deprecated, geplant: LiveData/Flow)

## ♿ Barrierefreiheit

Die App wurde nach den **WCAG 2.1 Level AA** Richtlinien entwickelt:

- ✅ **Touch-Targets**: Mindestens 48dp, empfohlen 64dp
- ✅ **Kontrast**: Hoher Kontrast für alle UI-Elemente
- ✅ **TalkBack**: Vollständige Screen-Reader-Unterstützung
- ✅ **Große Schrift**: Texte in 16-28sp für bessere Lesbarkeit
- ✅ **Einfache Sprache**: A1-Level Deutsch für maximale Verständlichkeit
- ✅ **Dark Mode**: Automatische Anpassung an System-Theme

##  Entwicklung

### 🚀 Build-Anleitung

```bash
# Repository klonen
git clone https://github.com/Stephan-Heuscher/Back_Home_Dot.git
cd Back_Home_Dot

# Mit Android Studio öffnen
# File → Open → Projektordner auswählen

# Abhängigkeiten synchronisieren
./gradlew build

# Debug-Build erstellen
./gradlew assembleDebug

# Unit-Tests ausführen
./gradlew testDebugUnitTest

# Release-Build erstellen (signiert)
./gradlew assembleRelease
```

### 🏗️ Projekt-Struktur & Clean Architecture

Das Projekt folgt **Clean Architecture** Prinzipien:

- **`domain/`**: Reine Geschäftslogik ohne Android-Abhängigkeiten
- **`data/`**: Daten-Zugriffsschicht (Repository-Pattern)
- **`service/`**: Android-Service-Komponenten
- **`ui/`**: Benutzeroberfläche (Activities, Fragments)
- **`util/`**: Hilfsfunktionen und Konstanten
- **`di/`**: Dependency Injection (ServiceLocator + Hilt-Module)

### 🔄 Aktuelle Verbesserungen (v2.0.0)

**Major Refactoring 2025**: Umstellung auf Clean Architecture

- ✅ **Architektur-Überarbeitung**: Von monolithischem Service zu komponentenbasierter Architektur
- ✅ **Code-Reduktion**: OverlayService von 507 auf ~194 Zeilen reduziert (62%)
- ✅ **Testbarkeit**: Komponenten sind isoliert testbar
- ✅ **Wartbarkeit**: Klare Trennung der Verantwortlichkeiten
- ✅ **Tastatur-Vermeidung**: Verbessert mit 1.5x Punkt-Durchmesser Margin
- ✅ **Reaktive Daten**: Kotlin Flows für Echtzeit-Updates
- ✅ **Dependency Injection**: ServiceLocator-Pattern (Hilt-ready)

### 🧪 Testing

```bash
# Unit-Tests für alle Module
./gradlew test

# Coverage-Report generieren
./gradlew jacocoTestReport

# Integration-Tests (zukünftig)
./gradlew connectedAndroidTest
```

### 📦 ProGuard & R8

Release-Builds verwenden ProGuard für Code-Optimierung:
- Konfiguration in `app/proguard-rules.pro`
- Clean Architecture sorgt für optimale Obfuscation

### Beitragen

Beiträge sind willkommen! Bitte:
1. Fork das Repository
2. Erstelle einen Feature-Branch (`git checkout -b feature/AmazingFeature`)
3. Committe deine Änderungen (`git commit -m 'Add: AmazingFeature'`)
4. Push zum Branch (`git push origin feature/AmazingFeature`)
5. Öffne einen Pull Request

## 📋 Roadmap

### 🚀 **Abgeschlossen (v2.0.0)**
- ✅ **Clean Architecture**: Vollständige Umstellung auf moderne Architektur
- ✅ **Komponenten-basierte Services**: OverlayService in fokussierte Komponenten zerlegt
- ✅ **Verbesserte Tastatur-Vermeidung**: 1.5x Punkt-Durchmesser Margin
- ✅ **Reaktive Datenströme**: Kotlin Flows für Echtzeit-Updates
- ✅ **Dependency Injection**: ServiceLocator-Pattern (Hilt-ready)
- ✅ **Deprecation Fixes**: Alle veralteten APIs aktualisiert (LocalBroadcastManager, versionCode, etc.)

### 🚀 **Geplante Features**

Die Weiterentwicklung wird durch **Ihr Feedback** gesteuert!

#### 🎯 **Hoch priorisiert**
- **Hilt Migration**: Von ServiceLocator zu Hilt DI
- **Erweiterte Tests**: Unit-Tests für alle Komponenten
- **Performance Monitoring**: Battery- und Memory-Optimierung
- **Accessibility Audit**: Vollständige WCAG 2.2 AA Konformität

#### 💡 **Mögliche Features**
- **Custom Gesten**: Benutzerdefinierte Gesten-Konfiguration
- **Themes**: Dunkles/Licht-Theme für Overlay
- **Animations**: Sanfte Übergänge und Feedback
- **Multi-Device**: Wear OS Companion App
- **Statistics**: Nutzungsstatistiken und Insights
- **Backup/Restore**: Einstellungen sichern/wiederherstellen

### 💬 **Feedback geben**

- **GitHub Issues**: [Neue Anfrage erstellen](../../issues/new)
- **Feature Requests**: Mit "enhancement" Label versehen
- **Bug Reports**: Mit "bug" Label und Reproduktionsschritten

## 🐛 Bekannte Einschränkungen

- **Overlay über System-UI**: Ab Android 8.0 erlaubt Google aus Sicherheitsgründen keine Overlays über System-Einstellungen
- **Akku-Optimierung**: Bei aggressiver Akku-Optimierung kann der Service beendet werden

## 📄 Lizenz

Dieses Projekt steht unter der MIT-Lizenz - siehe [LICENSE](LICENSE) Datei für Details.

## 👤 Autor

**Stephan Heuscher**

- GitHub: [@Stephan-Heuscher](https://github.com/Stephan-Heuscher)

## 🙏 Danksagungen

- Entwickelt mit Unterstützung von Claude (Anthropic)
- Icons von Material Design
- Inspiriert von iOS AssistiveTouch

## 📞 Support

Bei Fragen oder Problemen:
- Öffne ein [Issue](../../issues)
- Kontaktiere den Entwickler über GitHub

---

**Hinweis**: Diese App ist ein Hilfsmittel und ersetzt keine professionelle Beratung oder Therapie bei motorischen Einschränkungen. Konsultiere bei gesundheitlichen Fragen immer einen Arzt oder Therapeuten.

Made with ❤️ for accessibility
