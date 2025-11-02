# AssistiPunkt (Assistive Tap)

> **Ihr Tipp-Helfer zur Navigation** – Eine barrierefreie Android-App für intuitive Navigation mit einem schwebendem Punkt

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org/)
[![API Level](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 📱 Über die App

**AssistiPunkt** (international: *Assistive Tap*) ist eine barrierefreie Android-App, die Menschen mit eingeschränkter Mobilität oder motorischen Schwierigkeiten die Smartphone-Navigation erleichtert. Ein schwebender, anpassbarer Punkt ermöglicht mit einfachen Gesten die Steuerung aller wichtigen Navigationsfunktionen.

### ✨ Hauptfunktionen

- **🎯 Schwebender Punkt**: Frei positionierbarer, anpassbarer Navigationspunkt über allen Apps
- **👆 Intuitive Gesten**:
  - 1x tippen → Zurück
  - 2x tippen → Letzte App
  - 3x tippen → Alle offenen Apps
  - 4x tippen → App öffnen
  - Lang drücken → Startseite
- **🎨 Anpassbar**: Farbe, Durchsichtigkeit und Position frei wählbar
- **⚡ Konfigurierbar**: App-Wechsel-Geschwindigkeit einstellbar (50-300ms)
- **♿ Barrierefrei**: Nach WCAG 2.1 Level AA optimiert

## 🖼️ Screenshots

<p align="center">
  <img src="screenshots/Screenshot_20251030_172336_Assistive_Tap.jpg" width="30%" alt="Hauptbildschirm" />
  <img src="screenshots/Screenshot_20251030_172401_Assistive_Tap.jpg" width="30%" alt="Einstellungen" />
  <img src="screenshots/floating_dot.jpg" width="30%" alt="AssistiPunkt in Aktion" />
</p>

<p align="center">
  <em>Hauptbildschirm • Einstellungen • AssistiPunkt in Aktion</em>
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

1. **Punkt aktivieren**: Ein/Aus-Schalter in der App
2. **Punkt positionieren**: Punkt gedrückt halten und verschieben
3. **Navigation**: Mit verschiedenen Tipp-Gesten navigieren
4. **Anpassen**: Über "⚙ Einstellungen" Farbe und Verhalten ändern

### Erweiterte Einstellungen (für Experten)

Im Bereich "Für Experten" können folgende Parameter angepasst werden:
- **Durchsichtigkeit**: Transparenz des Punktes (0-100%)
- **Wechsel-Geschwindigkeit**: Verzögerung beim App-Wechsel (50-300ms)

## 🛠️ Technische Details

### Architektur

```
AssistiPunkt/
├── MainActivity           # Hauptansicht & Berechtigungen
├── SettingsActivity      # Einstellungen (Farbe, Alpha, Timeout)
├── OverlayService        # Schwebender Punkt & Gesten-Erkennung
├── BackHomeAccessibilityService  # Accessibility Service für Navigation
├── OverlaySettings       # SharedPreferences Verwaltung
└── PermissionManager     # Berechtigungsverwaltung
```

### Technologie-Stack

- **Sprache**: Kotlin
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 36
- **UI Framework**: Material Design 3
- **Architecture**: Service-basiert mit Overlay & Accessibility

### Verwendete Android-APIs

- **Overlay API**: `WindowManager` für schwebenden Punkt
- **Accessibility API**: `AccessibilityService` für Navigationsaktionen
- **Gesture Detection**: Custom Touch-Handler mit System-Timeouts

## ♿ Barrierefreiheit

Die App wurde nach den **WCAG 2.1 Level AA** Richtlinien entwickelt:

- ✅ **Touch-Targets**: Mindestens 48dp, empfohlen 64dp
- ✅ **Kontrast**: Hoher Kontrast für alle UI-Elemente
- ✅ **TalkBack**: Vollständige Screen-Reader-Unterstützung
- ✅ **Große Schrift**: Texte in 16-28sp für bessere Lesbarkeit
- ✅ **Einfache Sprache**: A1-Level Deutsch für maximale Verständlichkeit
- ✅ **Dark Mode**: Automatische Anpassung an System-Theme

##  Entwicklung

### Build-Anleitung

```bash
# Repository klonen
git clone https://github.com/Stephan-Heuscher/Back_Home_Dot.git
cd Back_Home_Dot

# Mit Android Studio öffnen
# File → Open → Projektordner auswählen

# Debug-Build erstellen
./gradlew assembleDebug

# Release-Build erstellen (signiert)
./gradlew assembleRelease
```

### ProGuard

Release-Builds verwenden ProGuard für Code-Optimierung und -Verschleierung:
- Konfiguration in `app/proguard-rules.pro`

### Beitragen

Beiträge sind willkommen! Bitte:
1. Fork das Repository
2. Erstelle einen Feature-Branch (`git checkout -b feature/AmazingFeature`)
3. Committe deine Änderungen (`git commit -m 'Add: AmazingFeature'`)
4. Push zum Branch (`git push origin feature/AmazingFeature`)
5. Öffne einen Pull Request

## 📋 Roadmap

Die Weiterentwicklung von AssistiPunkt wird durch **Ihr Feedback** gesteuert!

### 💡 Wie Sie mitgestalten können

Haben Sie Ideen oder Wünsche für neue Features? Wir hören zu!

1. **Feature-Wünsche**: Öffnen Sie ein [Issue](../../issues) mit dem Label "enhancement"
2. **Probleme melden**: Teilen Sie uns Bugs oder Schwierigkeiten mit


### 🎯 Mögliche Features (basierend auf Feedback)

Die Priorisierung erfolgt nach:
- ✨ Anzahl der Anfragen
- ♿ Verbesserung der Barrierefreiheit
- 🚀 Technischer Machbarkeit
- 👥 Nutzen für die Community

**Ihre Stimme zählt!** Je mehr Nutzer ein Feature wünschen, desto höher die Priorität.

### 💬 Feedback geben

- GitHub Issues: [Neue Anfrage erstellen](../../issues/new)
- Email: Direkt über GitHub-Profil kontaktieren

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
