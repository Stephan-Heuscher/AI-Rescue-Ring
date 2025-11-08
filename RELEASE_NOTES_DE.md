# Release Notes - AssistiPunkt

## Version 2.1.0 (2025-11-08)

### Safe-Home-Modus & UX-Verbesserungen

Wir freuen uns, **AssistiPunkt v2.1.0** mit dem neuen **Safe-Home-Modus** vorzustellen - perfekt für Nutzer, die maximale Sicherheit benötigen!

#### 🏠 Safe-Home-Modus (Neu!)
- **Immer nach Hause**: Alle Taps führen zur Startseite - keine Verwirrung mehr
- **Viereck-Design**: Button wird zum abgerundeten Viereck (8dp Radius) - wie Android-Navigationsbuttons
- **Geschütztes Verschieben**: Button nur nach 500ms langem Drücken verschiebbar
- **Visuelles Feedback**: Pulsierender weißer Halo (128dp) zeigt an, wann der Button verschoben werden kann
- **Überall verschiebbar**: Im Drag-Modus kann der Button überall platziert werden

#### 🎨 Design-Verbesserungen
- **Modus-basiertes Design**:
  - **Standard/Navi**: Klassisches Kreis-Design
  - **Safe-Home**: Modernes Viereck-Design (wie Android-Navigation)
- **Halo-Effekt**: Doppelt so groß (128dp statt 64dp) für bessere Sichtbarkeit
- **Sanfte Animation**: Pulsierender Halo (300ms-700ms Alpha) während Drag-Modus

#### 🔧 Technische Verbesserungen
- **Automatischer Neustart**: App startet automatisch nach Updates neu (PackageUpdateReceiver)
- **Tablet-Fix**: Tastatur-Erkennung korrigiert - Button kann jetzt über gesamten Bildschirm verschoben werden
- **Layout-Optimierung**: Feste 128dp Layoutgröße verhindert Button-Verschiebung beim Halo-Erscheinen
- **Intelligente Gesten**: Modus-bewusste Drag-Logik (sofort vs. long-press)

#### 🎯 Verhalten pro Modus
**Standard-Modus:**
- Kreis-Design
- Sofortiges Verschieben per Drag
- 1x Tap = Home, 2x Tap = Zurück
- Long-Press = Home

**Navi-Modus:**
- Kreis-Design
- Sofortiges Verschieben per Drag
- 1x Tap = Zurück, 2x Tap = Letzte App
- Long-Press = Home

**Safe-Home-Modus:**
- Viereck-Design (8dp Radius)
- Verschieben nur nach Long-Press (500ms) mit Halo-Feedback
- Alle Taps = Home (maximale Sicherheit)
- Long-Press = Drag-Modus aktivieren

### Fehlerbehebungen
- **Tablet-Problem behoben**: Button war auf Tablets auf 62% des Bildschirms beschränkt (Tastatur-Erkennung gab immer 38% zurück)
- **Halo verschiebt Button nicht mehr**: Layout-Größe fix auf 128dp, verhindert Verschiebung beim Halo-Erscheinen
- **Keyboard-Erkennung**: getKeyboardHeight() gibt jetzt 0 zurück wenn Tastatur nicht sichtbar

### Technische Details
- **Neue Konstante**: `OVERLAY_LAYOUT_SIZE_DP = 128` für Layout mit Halo
- **GestureDetector**: Mode-aware drag logic mit `requiresLongPressToDrag` Flag
- **OverlayViewManager**: Dynamisches Shape-Rendering basierend auf tapBehavior
- **PackageUpdateReceiver**: Lauscht auf `ACTION_MY_PACKAGE_REPLACED` Intent

---

## Version 2.0.0 (2025-11-05)

### Großes Refactoring - Clean Architecture & Spezialisierte Komponenten

Wir freuen uns, **AssistiPunkt v2.0.0** vorzustellen, eine vollständige Architektur-Überarbeitung die Codequalität, Wartbarkeit und Benutzererfahrung signifikant verbessert!

#### 🏗️ Architektur-Verbesserungen
- **Komponenten-Extraktion**: Spezialisierte Komponenten aus monolithischem OverlayService extrahiert
  - **KeyboardManager** (273 Zeilen): Vollständiges Tastatur-Vermeidungs-Management mit Debouncing
  - **PositionAnimator** (86 Zeilen): Sanfte Positions-Animationen mit ValueAnimator
  - **OrientationHandler** (97 Zeilen): Mathematische Rotations-Transformationen für alle Ausrichtungen
- **Code-Reduktion**: OverlayService von 670 auf ~459 Zeilen reduziert (31% Reduktion)
- **Clean Architecture**: Strikte Trennung zwischen Domain, Data und Presentation Layern
- **Testbarkeit**: Alle Komponenten sind nun unabhängig testbar
- **Dependency Injection**: ServiceLocator-Pattern bereit für Hilt-Migration

#### 🔄 Rotations-Handling - Kein Springen
- **Versteckt während Rotation**: Punkt wird während Bildschirm-Rotation versteckt um sichtbares Springen zu eliminieren
- **Intelligente Erkennung**: Dynamisches 16ms-Polling erkennt Dimensionsänderungen sofort
- **Perfekte Positionierung**: Punkt erscheint an mathematisch korrekter Position nach Rotation
- **Mathematische Transformation**: Präzise Mittelpunkt-Transformation für alle Rotationswinkel (0°, 90°, 180°, 270°)

#### ⌨️ Tastatur-Vermeidungs-Verbesserungen
- **Vollständig extrahiert**: Komplettes Tastatur-Management in dedizierter KeyboardManager-Klasse
- **Intelligenter Abstand**: 1.5x Punkt-Durchmesser Abstand zur Tastatur
- **Debouncing**: Verhindert Positions-Flackern während Tastatur-Übergängen
- **Snapshot/Restore**: Positions-Speicher für Tastatur-Erscheinen/Verschwinden-Zyklen

#### 🎨 Benutzererfahrung
- **Kein Springen**: Punkt springt nicht mehr während Rotation
- **Schnellere Reaktion**: Intelligente Erkennung bietet optimales Timing (~16ms auf den meisten Geräten)
- **Nahtlose Übergänge**: Alle Animationen und Positions-Updates sind flüssig

#### 🔧 Technische Details
- **Repository-Pattern**: BackHomeAccessibilityService zu Repository-Pattern migriert
- **Reaktive Daten**: Kotlin Flows für Echtzeit-Einstellungs-Updates
- **Komponenten-Komposition**: Composition over Inheritance Pattern durchgehend
- **ServiceLocator**: Factory-Methoden für alle spezialisierten Komponenten

### Breaking Changes
- Keine - alle Änderungen sind interne Architektur-Verbesserungen

### Fehlerbehebungen
- Punkt-Springen während Bildschirm-Rotation behoben
- Verbesserte Tastatur-Vermeidungs-Zuverlässigkeit
- Bessere Positions-Berechnungs-Genauigkeit

---

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

#### 🔧 Technische Verbesserungen
- **Automatische Versionierung**: Release-Builds erhöhen nun automatisch Versionsnummern
- **Build-Prozess-Verbesserung**: Versionscode und Patch-Version werden automatisch bei jedem Release erhöht
- **Verbesserte Build-Scripts**: Gradle Build-Prozess mit dynamischer Versionierung optimiert

### Änderungen

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

Wir freuen uns, **AssistiPunkt v1.1.0** mit Tastatur-Vermeidung vorzustellen!

#### ⌨️ Tastatur-Vermeidung
- **Automatische Positionierung**: Schwebender Punkt bewegt sich automatisch nach oben, wenn Tastatur erscheint
- **Intelligente Erkennung**: Erkennt Tastatur-Sichtbarkeit und passt Position entsprechend an
- **Nahtloses Erlebnis**: Kein manuelles Neupositionieren beim Tippen nötig

#### 🎨 Verbesserungen der Benutzerfreundlichkeit
- **Verbesserte Touch-Verarbeitung**: Bessere Gesten-Erkennung
- **Erweiterte Barrierefreiheit**: Aktualisierte Texte und Beschreibungen

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
