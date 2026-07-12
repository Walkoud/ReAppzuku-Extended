[English](./README.md) | [Русский](./README_RU.md) | [简体中文](./README_ZH.md) | [Espanol](./README_ES.md) | [Українська](./README_UK.md) | [Deutsch](./README_DE.md) | **Français**

---

![Logo](https://github.com/Walkoud/ReAppzuku-Extended/blob/main/docs/images/logo.png)
<p align="center">
<img src="https://img.shields.io/github/v/release/Walkoud/ReAppzuku-Extended?label=Release&" alt="Dernière version">
<img src="https://img.shields.io/github/downloads/Walkoud/ReAppzuku-Extended/total?label=T%C3%A9l%C3%A9chargements&color=a855f7" alt="Téléchargements">
<img src="https://img.shields.io/badge/Licence-GPLv3-64748b.svg" alt="Licence">
<img src="https://img.shields.io/badge/Android-6.0%2B-f97316.svg" alt="Android">
<img src="https://img.shields.io/badge/Root-Pris%20en%20charge-brightgreen.svg"/>
<img src="https://img.shields.io/badge/Shizuku-Pris%20en%20charge-brightgreen.svg"/>
</p>

ReAppzuku Extended est un fork de [ReAppzuku](https://github.com/gree1d/ReAppzuku) avec des fonctionnalités supplémentaires pour un contrôle renforcé de l'activité en arrière-plan des applications Android.

Surveillez et stoppez les applications qui consomment de la RAM, vident la batterie et sollicitent le CPU en arrière-plan.\
Arrêt forcé manuel en un clic, Kill périodique via une minuterie, et restrictions d'arrière-plan flexibles pour les applications sélectionnées.\
\
Les privilèges Root ou Shizuku sont requis.

## ✨ Fonctionnalités Étendues

* **Sélectionner tout filtré :** Sélectionnez ou désélectionnez toutes les applications visibles dans n'importe quelle boîte de dialogue en un seul clic — Liste blanche, Liste noire, Restrictions d'arrière-plan, Mode Sommeil, Applications masquées, etc.
* **Tri et filtres améliorés :**
  * Filtrage par état de sélection : afficher uniquement les applications sélectionnées ou non sélectionnées.
  * Filtrage par type de restriction (Soft / Medium / Hard / Manuel) dans les boîtes de dialogue Restrictions d'arrière-plan.
  * Appliquer un type de restriction en lot à plusieurs applications sélectionnées à la fois.
* **Modèle d'installation automatique :** Applique automatiquement une restriction d'arrière-plan, le mode sommeil, la liste blanche ou la liste noire aux applications nouvellement installées. Configurez une fois dans Paramètres → Outils avancés → Modèle d'installation d'application.
* **Support Android 14+ et HyperOS :** Détection des paquets via à la fois un récepteur de diffusion et une sonde active de secours pour un fonctionnement fiable sur les OEM qui bloquent les événements standards.
* **Sauvegarde étendue :** Les compartiments standby personnalisés, les indicateurs de suppression de liste blanche et la méthode de suspension du mode sommeil sont désormais préservés lors de la sauvegarde et de la restauration.

## ⚙️ Fonctionnalités

* **Automatisation intelligente :**
  * Auto-Kill périodique : intervalles de 10 secondes à 5 minutes.
  * Kill au verrouillage de l'écran : arrête forcé des processus en arrière-plan immédiatement après l'extinction de l'écran.
  * Seuil de RAM : le Kill se déclenche uniquement lorsque l'utilisation de la RAM atteint une limite définie (75 % à 100 %).
  * Kill sur événements matériels / lancement d'application : le Kill est déclenché par des événements matériels sélectionnés ou lors du lancement de l'application cible, avec la possibilité de vider également la RAM.
  * Préréglages Auto-Kill : personnalisez et planifiez le comportement de l'Auto-Kill à des moments spécifiques.
* **Contrôles manuels :**
  * Écran principal : visualisez tous les processus actifs en arrière-plan avec leur utilisation de la RAM, sélectionnez et tuez en masse.
  * Tuiles rapides : « Arrêter l'application » tue l'application au premier plan ; « Arrêter les applications en arrière-plan » exécute l'Auto-Kill avec vos listes.
  * Widget d'écran d'accueil : affiche l'utilisation actuelle de la RAM et les statistiques d'Auto-Kill des 12 dernières heures.
  * Raccourci d'application : appui long sur l'icône de l'application pour tuer instantanément l'application au premier plan.
* **Restrictions d'arrière-plan** (Android 11+) :
  * Mode Soft : bloque le démarrage automatique au niveau OS — l'application continue de fonctionner si vous l'avez ouverte, mais ne se réveille pas d'elle-même.
  * Mode Medium : restriction partielle de l'activité en arrière-plan de l'application.
  * Mode Hard : termine immédiatement le processus lorsqu'il est réduit, l'empêche de rester en mémoire ne serait-ce qu'une seconde.
  * Mode Manuel : sélectionnez et appliquez manuellement les restrictions requises à l'application.
* **Planificateur de restrictions :** définissez une fenêtre de temps pour lever temporairement les restrictions, avec lancement facultatif d'un composant à l'activation.
* **Mode Sommeil :** gel complet des applications sélectionnées après une période d'inactivité définie (5 à 60 min), dégel automatique au déverrouillage de l'écran.
* **Déclencheurs d'application :** outil de diagnostic approfondi analysant les causes réelles de l'activité en arrière-plan — services au premier plan, services persistants, wakelocks, alarmes, planificateur de tâches, connexions réseau, récepteurs de démarrage, et 54 autres facteurs (dépend de la version d'Android).
* **Analytiques et journaux :**
  * Journal Auto-Kill des 12 dernières heures : kills, redémarrages, RAM libérée par application.
  * Classement des pires applications par consommation de RAM et fréquence de redémarrage (12 h / 24 h / 7 j / tout le temps).
  * Journal des restrictions d'arrière-plan : appliqué, erreur, non appliqué — jusqu'à 200 entrées.
  * Graphiques d'utilisation des ressources (RAM, CPU, batterie) pour des périodes de 2, 6, 12 et 24 heures.
* **Listes flexibles :** Liste blanche (exclusions Auto-Kill), Liste noire (cibles Auto-Kill), Applications masquées (exclues de la liste et de l'Auto-Kill entièrement).
* **Sauvegarde et restauration :** exportez et importez tous les paramètres dans un fichier JSON — liste blanche, liste noire, applications masquées, restrictions, mode Sommeil et paramètres d'automatisation.

## 🛠 Prérequis

| Composant | Prérequis |
|---|---|
| Android | 6.0+ (les restrictions d'arrière-plan nécessitent 11+) |
| Accès | Root ou Shizuku |

## 🚀 Démarrage rapide

* **Configurer l'accès :** installez et activez [Shizuku](https://github.com/thedjchi/Shizuku), ou accordez les droits root.
* **Fonctionnement en arrière-plan :** désactivez l'optimisation de la batterie pour ReAppzuku et épinglez-le dans Récents — sinon le système pourrait tuer le service de gestion lui-même.
* **Choisissez votre stratégie :** Liste blanche + Kill périodique pour des économies maximales, ou Liste noire uniquement pour un contrôle ciblé d'applications spécifiques.

## ⭐ Meilleure utilisation (ma préférence)

Voici ma configuration personnelle recommandée pour des économies de batterie maximales avec un minimum de compromis.

### Étapes de configuration

1. **Activer le service d'arrière-plan** — Allez dans Paramètres → Automatisation → activez « Service d'arrière-plan ». Cela garantit que ReAppzuku peut appliquer et maintenir les restrictions même après avoir quitté les paramètres.

2. **Activer le modèle d'installation d'application** — Paramètres → Outils avancés → Modèle d'installation d'application → activez, puis :
   - Cochez **Restriction d'arrière-plan** → réglez sur **HARD**
   - Cochez **Notifier lors de l'application** — cela envoie une notification chaque fois qu'une nouvelle application est installée et que le modèle lui est appliqué, afin que vous sachiez exactement ce qui s'est passé et puissiez ajuster si nécessaire.
   - Laissez le reste décoché (Mode sommeil, Liste blanche, Liste noire).

3. **Appliquer le mode HARD en masse à toutes les applications utilisateur :**
   - Allez dans **Restrictions d'arrière-plan** → appuyez sur **Trier** → sélectionnez **Utilisateur** pour afficher uniquement vos applications installées.
   - Appuyez sur **Tout sélectionner** (en haut à droite) pour sélectionner chaque application visible.
   - Appuyez sur **Type** (en haut à droite, apparaît lorsque des applications sont sélectionnées) → choisissez **Hard** → Confirmez.
   - Toutes vos applications utilisateur sont maintenant en mode HARD.

4. **Exceptions — applications nécessitant des notifications ou ayant des widgets :**
   - Dans le même écran Restrictions d'arrière-plan, **décochez** ou passez en mode **Soft** pour :
     - Les applications de messagerie (WhatsApp, Signal, Telegram, Discord) — Soft maintient les notifications FCM instantanées.
     - Les applications avec des widgets sur votre écran d'accueil (les widgets peuvent ne pas se mettre à jour en mode HARD).
     - Toute application pour laquelle vous souhaitez continuer à recevoir des notifications push.

### Tableau de référence des modes de restriction

| Mode | Effet technique (via Android/Shizuku) | Pourquoi l'utiliser ? | Impact sur les notifications | Exemples d'applications cibles |
|---|---|---|---|---|
| 🟢 SOFT | • Désactive uniquement RUN_ANY_IN_BACKGROUND.<br>• Laisse l'application passivement en RAM. | • Empêche l'application de se lancer automatiquement.<br>• Préserve une fluidité maximale (pas de rechargement/logo lors du retour). | Instantanée (100 % fonctionnelle via les serveurs Google FCM). | WhatsApp, Signal, Discord, Gmail, Uber. |
| 🟡 MEDIUM | • Désactive 6 AppOps clés (ACCESS_NOTIFICATIONS, GET_USAGE_STATS, etc.).<br>• Place l'application en compartiment standby RARE. | • Bloque le pistage et l'espionnage (l'application ne peut plus voir vos autres activités).<br>• Permet toujours la lecture musicale ou le GPS en arrière-plan pendant l'utilisation. | Bloquée ou fortement retardée (uniquement livrée par lots). | Instagram, X (Twitter), Spotify, YouTube, Chrome/Firefox. |
| 🔴 HARD | • Désactive les 11 AppOps disponibles (y compris WAKE_LOCK, START_FOREGROUND, SCHEDULE_EXACT_ALARM).<br>• Compartiment standby RESTRICTED.<br>• Un arrêt forcé initial lors de l'activation. | • Protection maximale de la batterie.<br>• Handicape complètement l'application dès qu'elle passe en arrière-plan.<br>• Plus de processus parasites pendant la nuit ou lorsque l'écran est éteint. | Complètement bloquée (l'application reste silencieuse jusqu'à ce que vous l'ouvriez). | TikTok, AliExpress, Temu, Shein, Jeux mobiles, Applications bancaires. |

## 🛡 Sécurité

ReAppzuku protège automatiquement les processus système critiques — Google Play Services, l'interface système, le clavier actuel, le lanceur actuel, la téléphonie, le Bluetooth, le NFC et Shizuku lui-même. Les applications système spécifiques aux OEM (Xiaomi Security Center, Samsung Device Care, OPPO Phone Manager, etc.) sont également protégées.

## 🎨 Personnalisation

* Thèmes système, clair, sombre et AMOLED.
* Accents de couleur configurables : Indigo, Carmin, Vert forêt, Ambre, et plus encore.

## 🌐 Traduction

Les traductions sont les bienvenues !\
Pour aider à localiser l'application :
* Soumettez une **Pull Request** avec les modifications de `/values/strings.xml`, `README.md`, `HELP.md`.
* Ouvrez une **Issue** et joignez vos fichiers `/values/strings.xml`, `README.md`, `HELP.md` (emballez-les d'abord en `.zip`).\

Vous pouvez utiliser l'IA pour traduire les fichiers, puis vérifier et corriger les éventuelles erreurs. Claude et Gemini fonctionnent bien avec les textes techniques (à mon avis et d'après mon expérience).

## 🖼️ Captures d'écran

<p align="center">
  <a href="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot1.jpg">
    <img src="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot1.jpg" width="100" alt="Capture d'écran 1">
  </a>
  <a href="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot2.jpg">
    <img src="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot2.jpg" width="100" alt="Capture d'écran 2">
  </a>
  <a href="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot3.jpg">
    <img src="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot3.jpg" width="100" alt="Capture d'écran 3">
  </a>
</p>

## Licence

ReAppzuku est distribué sous licence [GNU General Public License v3.0](LICENSE).

## Crédits

Forké depuis [gree1d/ReAppzuku](https://github.com/gree1d/ReAppzuku), originalement forké depuis [northmendo/Appzuku](https://github.com/northmendo/Appzuku).
<br><br>
>![Claude](https://img.shields.io/badge/Claude-D97757?logo=claude&logoColor=fff)
![Google Gemini](https://img.shields.io/badge/Google%20Gemini-886FBF?logo=googlegemini&logoColor=fff)
![Grok / xAI](https://img.shields.io/badge/Grok-000000?logo=xai&logoColor=white)
> ReAppzuku a été développé en utilisant le vibecoding — une approche où une partie significative du code a été générée avec l'aide de l'IA (LLM).
