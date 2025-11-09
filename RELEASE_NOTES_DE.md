# Release Notes - AI Rescue Ring

## Version 3.0.0 (2025-11-09)

### Komplettes Rebranding & KI-Integration

Wir freuen uns, **AI Rescue Ring v3.0.0** vorzustellen - eine vollständige Transformation von einem Navigationsassistent zu einem intelligenten KI-gestützten Helfer!

#### 🤖 KI-gestützte Hilfe (NEU!)
- **Gemini-Integration**: Tippen Sie auf den Rettungsring, um mit Googles Gemini 2.5 Flash KI zu chatten
- **Spracheingabe**: Stellen Sie Ihre Fragen natürlich per Sprache
- **Texteingabe**: Tippen Sie Ihre Anfragen
- **Direkte API-Verbindung**: Ihre Anfragen gehen direkt an Google - wir speichern nichts
- **Sichere Speicherung**: API-Schlüssel verschlüsselt mit Android KeyStore
- **Sofortige Hilfe**: KI-Unterstützung überall auf Ihrem Gerät

#### 🎨 Komplettes visuelles Rebranding
- **Neuer Name**: Assistive Tap → AI Rescue Ring
- **Package-Änderung**: `ch.heuscher.back_home_dot` → `ch.heuscher.airescuering`
- **Aktualisierte UI**: Alle Texte fokussieren sich auf KI-Unterstützung
- **Rettungsring-Thema**: Neues Branding mit Fokus auf Hilfe und Unterstützung
- **Ring-Icon**: 🛟 Rettungsring-Emoji symbolisiert Hilfe

#### 🎮 Einfache Bedienung
- **Tippen** = KI-Chat öffnen
- **Sprach- oder Texteingabe** = Frage stellen
- **Lang drücken + ziehen** = Ring neu positionieren

#### 🔒 Privatsphäre & Sicherheit
- **Keine Datensammlung**: Wir sammeln oder speichern Ihre Gespräche nicht
- **Verschlüsselte API-Schlüssel**: Ihr Gemini API-Schlüssel mit Android KeyStore gespeichert
- **Direkte Kommunikation**: Alle KI-Anfragen gehen direkt an Google
- **Open Source**: Volle Transparenz - überprüfen Sie den Code selbst

#### 📱 Neue Funktionen
- **AI Helper Activity**: Dedizierte Chat-Oberfläche für KI-Gespräche
- **Settings-Integration**: Einfache API-Schlüssel-Verwaltung
- **Spracherkennung**: Integrierte Sprache-zu-Text-Funktion
- **Sichere Datenspeicherung**: Alle sensiblen Daten verschlüsselt

### Technische Änderungen
- **Package umbenannt**: `ch.heuscher.back_home_dot` → `ch.heuscher.airescuering`
- **Neue Dependencies**: OkHttp, Kotlinx Serialization, Security Crypto
- **Erweiterte Architektur**: KI-Repository-Layer hinzugefügt
- **Internet-Berechtigung**: Für Gemini API-Kommunikation hinzugefügt
- **Audio-Berechtigung**: Für Spracheingabe hinzugefügt

### Breaking Changes
- **Neuer Package-Name**: Nutzer müssen App neu installieren (kein Update von alter Version)
- **API-Schlüssel erforderlich**: KI-Funktionen benötigen kostenlosen Google Gemini API-Schlüssel
- **Neue Berechtigungen**: Internet- und Mikrofon-Zugriff für KI-Funktionen erforderlich

---

## Version 2.1.0 (2025-11-08)

### Safe-Home-Modus & UX-Verbesserungen

#### 🏠 Safe-Home-Modus
- **Immer nach Hause**: Alle Taps führen zur Startseite
- **Viereck-Design**: Button wird zum abgerundeten Viereck (8dp Radius)
- **Geschütztes Verschieben**: Button nur nach 500ms langem Drücken verschiebbar
- **Visuelles Feedback**: Pulsierender weißer Halo (128dp) zeigt Verschiebbarkeit
- **Überall verschiebbar**: Im Drag-Modus überall platzierbar

#### 🎨 Design-Verbesserungen
- **Modus-basiertes Design**: Kreis (Standard/Navi) vs. Viereck (Safe-Home)
- **Halo-Effekt**: Doppelt so groß (128dp) für bessere Sichtbarkeit
- **Sanfte Animation**: Pulsierender Halo während Drag-Modus

#### 🔧 Technische Verbesserungen
- **Auto-Neustart**: App startet automatisch nach Updates neu
- **Tablet-Fix**: Button kann über gesamten Bildschirm verschoben werden
- **Layout-Optimierung**: Feste 128dp Layout-Größe verhindert Verschiebung

### Fehlerbehebungen
- Tablet-Einschränkung behoben (war auf 62% des Bildschirms beschränkt)
- Halo verschiebt Button-Position nicht mehr
- Tastatur-Erkennung gibt 0 zurück wenn Tastatur nicht sichtbar

---

## Version 2.0.0 (2025-11-05)

### Großes Refactoring - Clean Architecture

#### 🏗️ Architektur-Verbesserungen
- **Komponenten-Extraktion**: Spezialisierte Komponenten aus monolithischem Service
  - KeyboardManager (273 Zeilen): Komplette Tastatur-Vermeidung
  - PositionAnimator (86 Zeilen): Sanfte Animationen
  - OrientationHandler (97 Zeilen): Rotations-Transformationen
- **Code-Reduktion**: OverlayService um 31% reduziert (670→459 Zeilen)
- **Clean Architecture**: Strikte Layer-Trennung
- **Testbarkeit**: Alle Komponenten unabhängig testbar

#### 🔄 Rotations-Handling - Kein Springen
- **Versteckt während Rotation**: Punkt versteckt um Springen zu eliminieren
- **Intelligente Erkennung**: 16ms-Polling erkennt Änderungen sofort
- **Perfekte Positionierung**: Erscheint an korrekter Position wieder

#### ⌨️ Tastatur-Vermeidung
- **Vollständig extrahiert**: Dedizierte KeyboardManager-Klasse
- **Intelligenter Abstand**: 1.5x Punkt-Durchmesser von Tastatur
- **Debouncing**: Verhindert Positions-Flackern

---

## Version 1.1.1 (2025-11-03)

### Neue Funktionen

#### 🎯 Tipp-Verhaltens-Modi
- **STANDARD-Modus**: 1x tippen = Home, 2x tippen = Zurück
- **ZURÜCK-Modus**: 1x tippen = Zurück, 2x tippen = Zu vorheriger App wechseln
- **Immer verfügbar**: 3x tippen = Alle Apps, 4x tippen = App öffnen, lang drücken = Home

#### ⌨️ Tastatur-Vermeidung
- Automatische Positionierung wenn Tastatur erscheint
- Intelligente Erkennung und Anpassung
- Nahtloses Tipp-Erlebnis

#### 🎨 Dynamische UI
- Kontextabhängige Anweisungen
- Einstellungs-Optimierung
- Verbesserte Barrierefreiheit

---

## Version 1.0.0 (2025-10-27)

### Erstveröffentlichung

Erste öffentliche Version des Navigationsassistenten (vor KI-Integration).

---

## Installation

1. APK von [GitHub Releases](https://github.com/Stephan-Heuscher/AI-Rescue-Ring/releases) herunterladen
2. Kostenlosen Gemini API-Schlüssel von [ai.google.dev](https://ai.google.dev) holen
3. APK auf Gerät installieren
4. App öffnen und Setup-Anweisungen folgen
5. Erforderliche Berechtigungen erteilen
6. Gemini API-Schlüssel in Einstellungen hinzufügen

---

## Feedback & Unterstützung

- **GitHub Issues**: [Problem melden](https://github.com/Stephan-Heuscher/AI-Rescue-Ring/issues)
- **Feature-Wünsche**: [Enhancement vorschlagen](https://github.com/Stephan-Heuscher/AI-Rescue-Ring/issues/new)
- **Email**: s.heuscher@gmail.com

---

## Credits

- **Entwickelt von**: Stephan Heuscher
- **KI powered by**: Google Gemini
- **Mit Unterstützung von**: Claude (Anthropic)
- **Icons**: Material Design

---

**Hinweis**: Diese App nutzt KI zur Unterstützung und liefert möglicherweise nicht immer genaue Informationen. Überprüfen Sie wichtige Informationen immer unabhängig.

Made with ❤️ für alle, die eine helfende Hand brauchen
