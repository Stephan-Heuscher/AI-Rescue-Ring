# Google Play Store Launch Checklist

## ⚠️ WICHTIGE WARNUNG: Accessibility Service

**Google hat sehr strenge Richtlinien für Apps mit AccessibilityService!**

### Problem
Ihre App nutzt AccessibilityService, ist aber **kein primäres Accessibility-Tool** (Bedienungshilfe für Menschen mit Behinderungen). Google lehnt solche Apps häufig ab.

### Lösung-Optionen
1. **App umpositionieren**: Als "Bedienungshilfe für motorisch eingeschränkte Nutzer" vermarkten
2. **Zusätzliche Features**: Echte Accessibility-Features einbauen (z.B. Sprachsteuerung, größere Touch-Bereiche)
3. **Detaillierte Begründung**: Im Play Store genau erklären, warum AccessibilityService benötigt wird

**Referenz**: [Google Play Accessibility Service Policy](https://support.google.com/googleplay/android-developer/answer/10964491)

---

## ✅ Technische Anforderungen (ERLEDIGT)

- [x] ProGuard/R8 aktiviert in `build.gradle.kts`
- [x] Resource Shrinking aktiviert
- [x] ProGuard Rules konfiguriert für AccessibilityService
- [x] Version Name auf "1.0.0" gesetzt

---

## 📋 TODO: Vor dem Upload

### 1. App-Signierung (KRITISCH)
**Sie müssen einen Keystore erstellen!**

```bash
# In Android Studio Terminal:
keytool -genkey -v -keystore back_home_dot.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias back_home_dot_key
```

**Wichtig**:
- ⚠️ Passwort sicher aufbewahren! Wenn verloren, können Sie die App NIE updaten!
- Keystore-Datei sichern (z.B. in einem Passwort-Manager oder Cloud-Safe)

Dann in `app/build.gradle.kts` die signingConfigs auskommentieren und ausfüllen.

### 2. App-Icons & Assets

#### App-Icon
- [ ] **App-Icon** in allen Größen (wird automatisch aus `ic_launcher.xml` generiert)
- [ ] Prüfen ob Icon den [Play Store Guidelines](https://developer.android.com/distribute/google-play/resources/icon-design-specifications) entspricht

#### Play Store Grafiken (PFLICHT)
- [ ] **App-Icon**: 512x512px PNG (mit Alpha-Kanal)
- [ ] **Feature Graphic**: 1024x500px JPG/PNG
- [ ] **Screenshots**: Mindestens 2, maximal 8 (Phone, Tablet falls unterstützt)
  - Empfohlen: 1080x1920px oder höher
  - Zeigen Sie: Settings-Screen, den Punkt im Overlay, verschiedene Farben

#### Optional aber empfohlen
- [ ] Promo-Video auf YouTube (max 2 Minuten)

### 3. App-Beschreibungen

#### Deutscher Text (Hauptsprache)

**Titel** (max 50 Zeichen):
```
Back Home Dot - Schnelle Navigation
```

**Kurzbeschreibung** (max 80 Zeichen):
```
Navigation mit Punkt - Zurück, Home & App-Wechsel
```

**Ausführliche Beschreibung** (max 4000 Zeichen):
```
Back Home Dot ist ein praktisches Tool für schnelle Navigation auf Android.

🎯 FUNKTIONEN

• Verschiebbarer Punkt, immer sichtbar
• Einfacher Klick = Zurück (Normal-Modus)
• Doppelklick = Zur vorherigen App wechseln (Normal-Modus)
• Dreifachklick = App-Übersicht (Multitasking) (Normal-Modus)
• Vierfachklick = Einstellungen öffnen (Normal-Modus)
• Langes Drücken = Home-Bildschirm (Normal-Modus)
• Tastatur-Vermeidung: Punkt weicht automatisch der Tastatur aus

⚙️ ANPASSBAR

• Farbe frei wählbar (Farbwähler + Theme-Farben)
• Deckkraft einstellbar (0-100%)
• Position frei verschiebbar
• Ein/Aus-Schalter
• Service-Stop Button für einfaches Beenden

⚙️ ANPASSBAR

• Farbe frei wählbar (Farbwähler + Theme-Farben)
• Deckkraft einstellbar (0-100%)
• Position frei verschiebbar
• Ein/Aus-Schalter
• Service-Stop Button für einfaches Beenden

♿ ACCESSIBILITY / BEDIENUNGSHILFE

Diese App nutzt Android Accessibility Services, um Navigationsgesten
systemweit bereitzustellen. Besonders hilfreich für:
- Nutzer mit motorischen Einschränkungen
- Ein-Hand-Bedienung
- Schnellere Navigation

🔒 DATENSCHUTZ

• Keine Datensammlung
• Keine Internet-Verbindung
• Keine Werbung
• Open Source

📱 BERECHTIGUNGEN

• Overlay-Berechtigung: Für den sichtbaren Punkt
• Accessibility Service: Für Zurück/Home/App-Wechsel Aktionen

Entwickelt in der Schweiz 🇨🇭
```

#### Englischer Text (International)

**Title**:
```
Back Home Dot - Quick Navigation
```

**Short Description**:
```
**Short Description**:
```
Navigation with dot - Back, Home & App switching
```

**Full Description**: (Übersetzung der deutschen Beschreibung)
```
Back Home Dot is a practical tool for quick navigation on Android.

🎯 FEATURES

• Movable dot, always visible
• Single tap = Back (Normal mode)
• Double tap = Switch to previous app (Normal mode)
• Triple tap = App overview (Multitasking) (Normal mode)
• Quadruple tap = Open settings (Normal mode)
• Long press = Home screen (Normal mode)
• Keyboard avoidance: Dot automatically moves away from keyboard

⚙️ CUSTOMIZABLE

• Color freely selectable (Color picker + theme colors)
• Transparency adjustable (0-100%)
• Position freely movable
• On/Off switch
• Service stop button for easy termination
```

**Full Description**: (Übersetzung der deutschen Beschreibung)

### 4. Privacy Policy (PFLICHT!)

Google verlangt bei Apps mit Permissions eine **Datenschutzerklärung**!

- [ ] Privacy Policy erstellen (siehe `PRIVACY_POLICY_TEMPLATE.md`)
- [ ] Auf eigener Website oder GitHub Pages hosten
- [ ] URL im Play Store Console eingeben

**Tipp**: Nutzen Sie GitHub Pages (kostenlos):
1. Erstellen Sie `docs/privacy_policy.html` in Ihrem Repository
2. Aktivieren Sie GitHub Pages in Repository Settings
3. URL wird: `https://yourusername.github.io/Back_Home_Dot/privacy_policy.html`

### 5. Accessibility Service Deklaration (KRITISCH!)

Im Play Store Formular werden Sie gefragt:

**"Why does your app use Accessibility Service?"**

**Empfohlene Antwort (Englisch)**:
```
This app uses the Accessibility Service to provide system-wide navigation
assistance for users with motor impairments or those who prefer one-handed
operation. The service enables:

1. Back navigation via single tap
2. Home screen access via long press
3. App switching via double tap
4. Recent apps overview via triple tap

The Accessibility Service is strictly used for these navigation gestures only.
No user data is collected, stored, or transmitted. The app does not track
user behavior or access sensitive information.

This functionality cannot be implemented without the Accessibility Service,
as standard Android APIs do not provide system-wide gesture controls.
```

### 6. Content Rating

- [ ] Content Rating Fragebogen ausfüllen
- [ ] Kategorie: "Tools" oder "Productivity"
- [ ] Altersfreigabe: Voraussichtlich USK 0 / PEGI 3 (alle Altersgruppen)

### 7. Store Listing

- [ ] **Kategorie**: Tools oder Productivity
- [ ] **Tags/Keywords**: navigation, accessibility, floating button, gestures
- [ ] **App-Kontakt**: E-Mail-Adresse angeben
- [ ] **Website** (optional aber empfohlen): GitHub Repository URL

### 8. Testing vor Release

#### Internal Testing
- [ ] APK in Internal Testing Track hochladen
- [ ] Auf verschiedenen Geräten testen
- [ ] Release-Build auf Fehler prüfen

#### Closed/Open Beta (empfohlen)
- [ ] Beta-Tester einladen (Freunde/Familie)
- [ ] Feedback sammeln
- [ ] Bugs fixen

---

## 🚀 Release Build erstellen

### Schritt 1: Keystore konfigurieren
1. Keystore erstellen (siehe oben)
2. In `app/build.gradle.kts` signingConfigs auskommentieren
3. Passwords NICHT in Git committen! Nutzen Sie:
   - Umgebungsvariablen ODER
   - `local.properties` (wird nicht committed)

### Schritt 2: Build erstellen
```bash
# In Android Studio:
Build -> Generate Signed Bundle / APK
-> Android App Bundle (empfohlen) oder APK
-> Release
-> Keystore auswählen
```

**Oder via Command Line:**
```bash
./gradlew bundleRelease
# Findet sich in: app/build/outputs/bundle/release/app-release.aab
```

### Schritt 3: Testen
```bash
# APK installieren und testen
./gradlew installRelease
```

---

## 📤 Play Store Console Upload

1. **Google Play Console**: https://play.google.com/console
2. **App erstellen**: Neue App anlegen
3. **App Bundle hochladen**: In "Release" -> "Production"
4. **Store Listing ausfüllen**: Alle Texte, Bilder, etc.
5. **Content Rating**: Fragebogen ausfüllen
6. **Pricing**: Kostenlos oder Bezahlt wählen
7. **In Review senden**: Kann 1-7 Tage dauern

---

## ⚠️ Mögliche Ablehnungsgründe

1. **Accessibility Service Missbrauch**
   - Lösung: Detaillierte Begründung + Accessibility-Features hervorheben

2. **Overlay Permission Missbrauch**
   - Lösung: Klar erklären, dass es nur für Navigation-Button ist

3. **Fehlende Privacy Policy**
   - Lösung: Privacy Policy erstellen und hosten

4. **Unvollständige App-Informationen**
   - Lösung: Alle Felder im Play Store ausfüllen

5. **Schlechte Screenshots/Icons**
   - Lösung: Professionelle Screenshots mit Beschreibungen

---

## 📞 Hilfreiche Links

- [Play Console](https://play.google.com/console)
- [Launch Checklist](https://developer.android.com/distribute/best-practices/launch/launch-checklist)
- [Accessibility Service Policy](https://support.google.com/googleplay/android-developer/answer/10964491)
- [App Signing](https://developer.android.com/studio/publish/app-signing)
- [Store Listing](https://support.google.com/googleplay/android-developer/answer/9866151)

---

## 💡 Tipps für erfolgreichen Launch

1. **Beta-Testing**: Nutzen Sie Internal/Closed Beta vor Production Release
2. **Screenshots**: Professionelle Screenshots mit Kontext (zeigen Sie Use-Cases)
3. **Beschreibung**: Klar kommunizieren, WAS die App macht und WARUM
4. **Keywords**: Gute Keywords für ASO (App Store Optimization)
5. **Updates**: Planen Sie regelmäßige Updates (zeigt aktive Entwicklung)

---

## 🔄 Nach dem Launch

- [ ] App auf verschiedenen Geräten testen
- [ ] User-Feedback beobachten
- [ ] Crash-Reports in Play Console überwachen
- [ ] Updates planen (neue Features, Bug-Fixes)
- [ ] Version Code bei jedem Update erhöhen!

---

**Viel Erfolg mit Ihrer App! 🚀**
