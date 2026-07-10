[English](./README.md) | [Русский](./README_RU.md) | [简体中文](./README_ZH.md) | [Espanol](./README_ES.md) | [Українська](./README_UK.md) | **Deutsch**

---

![Logo](https://github.com/gree1d/ReAppzuku/blob/main/docs/images/logo.png)
<p align="center">
<img src="https://img.shields.io/github/v/release/gree1d/ReAppzuku?label=Release&" alt="Latest Release">
<img src="https://img.shields.io/github/downloads/gree1d/ReAppzuku/total?label=Downloads&color=a855f7" alt="Downloads">
<img src="https://img.shields.io/badge/License-GPLv3-64748b.svg" alt="License">
<img src="https://img.shields.io/badge/Android-6.0%2B-f97316.svg" alt="Android">
<img src="https://img.shields.io/badge/Root-Supported-brightgreen.svg"/>
<img src="https://img.shields.io/badge/Shizuku-Supported-brightgreen.svg"/>
</p>

ReAppzuku ist eine neue Version von Appzuku (Shappky) und bietet erweiterte Kontrolle über die Hintergrundaktivität von Android-Apps.

Überwache und beende Apps, die im Hintergrund RAM verbrauchen, den Akku leeren und die CPU belasten.\
Manuelles Beenden (Force-Stop) mit nur einem Fingertipp, periodisches Schließen per Timer und flexible Hintergrundbeschränkungen für ausgewählte Apps.\
\
Root- oder Shizuku-Rechte sind erforderlich.

## ⚙️ Funktionen

* **Intelligente Automatisierung:**
  * Periodisches Auto-Kill (10 Sekunden bis 5 Minuten)
  * Auto-Kill beim Sperren des Bildschirms: beendet Hintergrundprozesse sofort nach dem Ausschalten des Bildschirms.
  * RAM-Schwellenwert (75–100 %): Auto-Kill wird erst ausgelöst, wenn die RAM-Auslastung ein festgelegtes Limit erreicht.
  * Auto-Kill bei Hardware-Ereignissen oder App-Start: wird durch ausgewählte Hardware-Events oder beim Starten einer Ziel-App ausgelöst, optional mit zusätzlicher RAM-Bereinigung.
  * Zeitgesteuerte Auto-Kill-Profile: Anpassung und Zeitsteuerung des Auto-Kill-Verhaltens zu bestimmten Uhrzeiten.
* **Manuelle Steuerung:**
  * Hauptbildschirm: Übersicht aktiver Hintergrundprozesse inklusive RAM-Nutzung, Auswahl und Beenden mehrerer Apps gleichzeitig.
  * Quick Tiles: „App stoppen“ beendet die aktuelle Vordergrund-App; „Hintergrund-Apps stoppen“ führt Auto-Kill basierend auf deinen Listen aus.
  * Homescreen-Widget: Zeigt die aktuelle RAM-Auslastung und die Auto-Kill-Statistiken der letzten 12 Stunden an.
  * App-Verknüpfung: Langes Drücken auf das App-Symbol beendet die aktuelle Vordergrund-App sofort.
* **Hintergrundbeschränkungen (Android 11+):**
  * Soft: verhindert automatischen Start auf Betriebssystemebene – die App läuft weiter, wenn du sie öffnest, wacht aber nicht von alleine auf.
  * Medium: schränkt Hintergrundaktivität von Apps teilweise ein.
  * Hard: beendet App sofort nach dem Minimieren und verhindert, dass sie auch nur für eine Sekunde im Speicher bleibt.
  * Manuell: individuelle Einschränkungen manuell auswählen und auf die App anwenden.
* **Restriction Scheduler:** Legt ein Zeitfenster fest, in dem Einschränkungen vorübergehend aufgehoben werden, optional mit dem Start von Komponenten bei der Aktivierung.
* **Sleep Mode:** Vollständiges Einfrieren ausgewählter Apps nach einem festgelegten Inaktivitäts-Timer (5–60 Min.), automatisches Auftauen beim Entsperren des Bildschirms.
* **App Trigger:** Tiefendiagnose-Tool zur Analyse der tatsächlichen Ursachen für Hintergrundaktivität – Vordergrunddienste, Sticky Services, Wakelocks, Alarme, Job Scheduler, Netzwerkverbindungen, Boot-Receiver und 54 weitere Faktoren (abhängig von der Android-Version).
* **Analyse- und Protokollfunktionen:**
  * Auto-Kill-Protokoll der letzten 12 Stunden: Beendete Apps, Neustarts, freigegebener RAM pro App.
  * Ranking der Top-Verbraucher nach RAM-Auslastung und Neustarthäufigkeit (12 Std. / 24 Std. / 7 Tage / Gesamte Zeit).
  * Protokoll der Hintergrundbeschränkungen: Angewendet, Fehler, nicht angewendet – bis zu 200 Einträge.
  * Diagramme zur Ressourcenauslastung (RAM, CPU, Akku) für Zeiträume von 2, 6, 12 und 24 Stunden.
* **Flexible Listen:** Whitelist (Ausnahmen für Auto-Kill), Blacklist (Ziele für Auto-Kill) und versteckte Apps (vollständig aus der Liste und vom Auto-Kill ausgeschlossen).
* **Backup und Wiederherstellung:** Export und Import aller Einstellungen in eine JSON-Datei – Whitelist, Blacklist, versteckte Apps, Einschränkungen, Sleep Mode und Automatisierungsparameter.

## 🛠️ Voraussetzungen

| Komponente | Voraussetzung |
| :--- | :--- |
| Android | 6.0+ (Hintergrundbeschränkungen ab Android 11) |
| Zugriff | Root oder Shizuku |

## 🚀 Schnellstart

1. Shizuku installieren und aktivieren oder Root verwenden.
2. Akkuoptimierung für ReAppzuku deaktivieren und App in der Übersicht (Recents) angeheftet lassen – andernfalls kann das System den Verwaltungsdienst selbst beenden.
3. Whitelist- oder Blacklist-Strategie auswählen: Whitelist + periodisches Schließen für maximale Ersparnis, oder reine Blacklist für die gezielte Kontrolle bestimmter Apps.

## 🛡️ Sicherheit

Kritische Systemprozesse wie Google Play-Dienste, System UI, aktuelle Tastatur, aktueller Launcher, Bluetooth, NFC, Telefonie und Shizuku werden automatisch geschützt. OEM-spezifische System-Apps (Xiaomi Sicherheits-Center, Samsung Device Care, OPPO Telefon-Manager usw.) werden ebenfalls geschützt.

## 🎨 Anpassung

* System-, Hell-, Dunkel- und AMOLED-Design
* Verschiedene Akzentfarben (Indigo, Purpurrot, Waldgrün, Bernstein und mehr)

## 🌐 Übersetzung

Übersetzungen sind herzlich willkommen!\
So hilfst du bei der Lokalisierung der App:
* Sende einen **Pull Request** mit Änderungen an `/values/strings.xml`, `README.md`, `HELP.md`.
* Öffne ein **Issue** und hänge deine `/values/strings.xml`, `README.md`, `HELP.md` an (bitte vorher in eine `.zip`-Datei packen).
Du kannst KI verwenden, um die Dateien zu übersetzen, und danach eventuelle Fehler korrigieren. Claude und Gemini arbeiten (meiner Meinung und Erfahrung nach) sehr gut mit technischen Texten.

## 🖼️ Screenshots

<p align="center">
  <a href="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot1.jpg">
    <img src="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot1.jpg" width="100" alt="Screenshot 1">
  </a>
  <a href="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot2.jpg">
    <img src="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot2.jpg" width="100" alt="Screenshot 2">
  </a>
  <a href="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot3.jpg">
    <img src="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot3.jpg" width="100" alt="Screenshot 3">
  </a>
</p>

## Lizenz

GNU General Public License v3.0 (GPLv3).

## Danksagung

Original von [northmendo/Appzuku](https://github.com/northmendo/Appzuku).
<br><br>
>![Claude](https://img.shields.io/badge/Claude-D97757?logo=claude&logoColor=fff)
![Google Gemini](https://img.shields.io/badge/Google%20Gemini-886FBF?logo=googlegemini&logoColor=fff)
![Grok / xAI](https://img.shields.io/badge/Grok-000000?logo=xai&logoColor=white)
> ReAppzuku wurde mittels „Vibecoding“ entwickelt – ein Ansatz, bei dem ein erheblicher Teil des Codes mithilfe von KI (LLMs) generiert wurde.