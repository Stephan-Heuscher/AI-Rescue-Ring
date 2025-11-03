# Release Notes - AssistiPunkt

## Version 1.1.1 (2025-11-03)

### Neue Funktionen

Wir freuen uns, **AssistiPunkt v1.1.1** mit bedeutenden Verbesserungen der Tipp-Verhaltens-Anpassung und einem optimierten Benutzererlebnis vorzustellen!

#### 🎯 Tipp-Verhaltens-Modi
- **Zwei Verhaltensoptionen**: Wählen Sie zwischen STANDARD und ZURÜCK Tipp-Verhaltens-Modi
- **STANDARD-Modus** (empfohlen): 1x tippen = Home, 2x tippen = Zurück (intuitive Android-Navigation)
- **ZURÜCK-Modus**: 1x tippen = Zurück, 2x tippen = Zu vorheriger App wechseln (traditionelles Verhalten)
- **Immer verfügbar**: 3x tippen = Alle offenen Apps, 4x tippen = App öffnen, langes Drücken = Startseite

#### 🎨 Dynamische Benutzeroberfläche
- **Kontextabhängige Anweisungen**: Hauptbildschirm-Anweisungen aktualisieren sich automatisch basierend auf gewähltem Tipp-Verhalten
- **Einstellungs-Optimierung**: Tipp-Verhaltens-Auswahl an oberste Stelle der Einstellungen verschoben für bessere Auffindbarkeit
- **Optimierte Erfahrung**: Rettungsring-Funktion entfernt für saubere, fokussierte Benutzeroberfläche

#### 🔧 Technische Verbesserungen
- **Automatische Versionierung**: Release-Builds erhöhen nun automatisch Versionsnummern
- **Build-Prozess-Verbesserung**: Versionscode und Patch-Version werden automatisch bei jedem Release erhöht
- **Verbesserte Build-Scripts**: Gradle Build-Prozess mit dynamischer Versionierung optimiert

### Änderungen

#### ✅ Entfernte Funktionen
- **Rettungsring-Modus**: Notfall-Rettungsring-Funktion vollständig entfernt für vereinfachtes Benutzererlebnis
- **Rettungsring-Schalter**: Hauptbildschirm-Schalter entfernt, Benutzeroberfläche fokussiert sich nun auf Kern-Navigation

#### ✅ Erweiterte Funktionen
- **Tipp-Verhaltens-Auswahl**: Radio-Buttons in Einstellungen für einfache Modus-Umschaltung
- **Echtzeit-Anweisungen**: Hauptbildschirm-Text aktualisiert sich sofort bei Verhaltensänderung
- **Einstellungs-Neustrukturierung**: Wichtigste Einstellung (Tipp-Verhalten) an oberste Stelle verschoben

#### ✅ Technische Aktualisierungen
- **Versions-Eigenschaften**: Neue `version.properties` Datei für automatische Versionsverwaltung
- **Build-Automatisierung**: Release-Builds erhöhen automatisch Versionsnummern
- **Dokumentations-Aktualisierungen**: README und technische Dokumentation aktualisiert

---

## Version 1.1.0 (2025-10-30)

### Neue Funktionen

Wir freuen uns, **AssistiPunkt v1.1.0** mit zwei wichtigen neuen Funktionen vorzustellen, die die Barrierefreiheit und Notfall-Reaktionsfähigkeiten verbessern!

#### 🛟 Rettungsring-Modus
- **Notfall-Navigation**: Neuer Rettungsring-Modus mit großem weißen Ring und roter Rettungsring-Emoji (🛟)
- **Schnelle Flucht**: Einfacher Tipp schließt aktuelle App und kehrt zur Startseite zurück
- **Hauptbildschirm-Schalter**: Einfacher Zugriff auf den Rettungsmodus vom Hauptbildschirm
- **Visuelle Unterscheidung**: Klare visuelle Unterscheidung zwischen normalem Navigationspunkt und Rettungsring

#### ⌨️ Tastatur-Vermeidung
- **Automatische Positionierung**: Schwebender Punkt bewegt sich automatisch nach oben, wenn Tastatur erscheint
- **Intelligente Erkennung**: Erkennt Tastatur-Sichtbarkeit und passt Position entsprechend an
- **Nahtloses Erlebnis**: Kein manuelles Neupositionieren beim Tippen nötig

#### 🎨 Verbesserungen der Benutzerfreundlichkeit
- **Modus-spezifische Anweisungen**: Dynamische Anweisungen, die sich je nach gewähltem Modus ändern
- **Verbesserte Touch-Verarbeitung**: Bessere Gesten-Erkennung im Rettungsring-Modus
- **Erweiterte Barrierefreiheit**: Aktualisierte Texte und Beschreibungen für beide Modi

---

## Version 1.0.0 (2025-10-27)

### Erstveröffentlichung

Wir freuen uns, die erste öffentliche Version von **AssistiPunkt** (international: **Assistive Tap**) vorzustellen - Ihre barrierefreie Android-App für intuitive Navigation!

---

## Hauptfunktionen

### Navigation per Gesten
- **Einfachklick**: Zurück-Navigation
- **Doppelklick**: Zur letzten App wechseln
- **Dreifachklick**: Alle offenen Apps anzeigen
- **Vierfachklick**: AssistiPunkt-App öffnen
- **Langes Drücken**: Zur Startseite

### Anpassungsmöglichkeiten
- **Farbauswahl**: Wählen Sie Ihre bevorzugte Farbe für den Punkt
- **Durchsichtigkeit**: Stellen Sie die Transparenz ein (0-100%)
- **Freie Positionierung**: Verschieben Sie den Punkt an beliebige Stellen
- **Wechsel-Geschwindigkeit**: Konfigurierbare Verzögerung beim App-Wechsel (50-300ms)

### Barrierefreiheit
- **WCAG 2.1 Level AA konform**: Entwickelt nach internationalen Barrierefreiheits-Standards
- **TalkBack-Unterstützung**: Vollständige Screen-Reader-Kompatibilität
- **Einfache Sprache**: Texte in A1-Level Deutsch für maximale Verständlichkeit
- **Große Schrift**: Optimierte Textgrößen (16-28sp) für bessere Lesbarkeit
- **Dark Mode**: Automatische Anpassung an System-Theme
- **Touch-Targets**: Mindestens 48dp große Berührungsflächen

### Design & Benutzerfreundlichkeit
- **Design-Galerie**: Inspirationen für Farben und Designs in den Einstellungen
- **Neues App-Icon**: Modernes, wiedererkennbares Icon
- **Material Design 3**: Zeitgemäße, intuitive Benutzeroberfläche
- **Intelligente Berechtigungsanzeige**: Klare Anleitung bei fehlenden Berechtigungen

### Internationalisierung
- **Mehrsprachig**: Vollständige Unterstützung für Deutsch und Englisch
- **Automatische Spracherkennung**: App passt sich an System-Sprache an

### Unterstützung der Entwicklung
- **Rewarded Ads**: Freiwillige Werbung zur Unterstützung der App-Entwicklung
- **Keine In-App-Käufe**: Alle Funktionen sind kostenlos verfügbar
- **Open Source**: Vollständiger Quellcode auf GitHub verfügbar

---

## Technische Details

### Systemanforderungen
- **Android-Version**: 8.0 (API Level 26) oder höher
- **Berechtigungen**:
  - Overlay-Berechtigung (für schwebenden Punkt)
  - Bedienungshilfe-Zugriff (für Navigationsaktionen)
  - Nutzungszugriff (für direkten App-Wechsel)

### Architektur
- **Sprache**: Kotlin
- **Target SDK**: 36
- **UI-Framework**: Material Design 3
- **Services**: OverlayService + AccessibilityService
- **Code-Optimierung**: ProGuard für Release-Builds

---

## Behobene Probleme

### Rotation & Positionierung
- Punkt bleibt beim Bildschirm-Drehen an gleicher physischer Position
- Korrekte Rotations-Transformation (Gegen den Uhrzeigersinn)
- Punkt verschwindet nicht mehr am Bildschirmrand
- Center-Point Transformation behebt Durchmesser-Verschiebung

### Stabilität
- App-Beenden stoppt Services zuverlässig ohne Status-Änderung
- 4-fach-Klick öffnet App zuverlässig
- Overlay-Sichtbarkeit garantiert
- Robustere Fehlerbehandlung

### Benutzeroberfläche
- Dark Mode funktioniert korrekt
- Switches schalten zuverlässig
- Dialoge verwenden konsistente Bezeichnung "Assistive Tap"
- Optimierte Button-Texte und Beschreibungen

---

## Bekannte Einschränkungen

- **Overlay über System-UI**: Ab Android 8.0 erlaubt Google aus Sicherheitsgründen keine Overlays über System-Einstellungen
- **Akku-Optimierung**: Bei aggressiver Akku-Optimierung kann der Service beendet werden

---

## Installation

1. APK von [GitHub Releases](https://github.com/Stephan-Heuscher/Back_Home_Dot/releases) herunterladen
2. APK auf dem Gerät installieren
3. App öffnen und Anweisungen folgen
4. Erforderliche Berechtigungen erteilen

---

## Feedback & Unterstützung

Wir freuen uns über Ihr Feedback!

- **GitHub Issues**: [Problem melden](https://github.com/Stephan-Heuscher/Back_Home_Dot/issues)
- **Feature-Wünsche**: [Enhancement vorschlagen](https://github.com/Stephan-Heuscher/Back_Home_Dot/issues/new)
- **Diskussionen**: [An Diskussion teilnehmen](https://github.com/Stephan-Heuscher/Back_Home_Dot/discussions)

---

## Credits

- **Entwickelt von**: Stephan Heuscher
- **Mit Unterstützung von**: Claude (Anthropic)
- **Icons**: Material Design
- **Inspiriert von**: iOS AssistiveTouch

---

**Hinweis**: Diese App ist ein Hilfsmittel und ersetzt keine professionelle Beratung oder Therapie bei motorischen Einschränkungen.

Made with ❤️ for accessibility
