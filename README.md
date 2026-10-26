# OpenFbx

Client Android non officiel pour piloter une **Freebox** via l'API **Freebox OS**
(l'API locale documentée par Free sur `dev.freebox.fr/sdk/os`).

Interface 100 % Jetpack Compose / **Material 3** : thème violet profond, modes
Clair / Sombre / OLED, palette d'accent au choix ou couleurs dynamiques,
navigation adaptative (barre inférieure sur téléphone, rail latéral dès 600 dp),
états de chargement, d'erreur et vides systématiques.

## État actuel

Jalons **M0 → M3** implémentés et compilés :

| Écran | Contenu |
|---|---|
| Découverte | `http://mafreebox.freebox.fr/api_version` + saisie manuelle d'une adresse |
| Appairage | demande `login/authorize/`, suivi du statut, empreinte du certificat, droits accordés |
| Accueil | état de la connexion, débits temps réel, Wi-Fi, appareils actifs, redémarrage, raccourcis Wi-Fi / Courbes / Système |
| Téléchargements | file complète, avancement, débits, ETA, pause / reprise / réessai, ajout par lien, magnet ou fichier `.torrent`, choix du dossier de destination, total partagé et ratio |
| Fichiers | explorateur des disques : navigation, fil d'Ariane, création de dossier et de fichier, envoi de fichiers, renommage, suppression, fiche détaillée |
| Système | firmware, modèle, série, MAC, uptime, températures, ventilateur, disques |
| Réseau | onglets Connexion (WAN, FTTH, xDSL) / Appareils (Wake-on-LAN) / DHCP (baux dynamiques, baux statiques) |
| Wi-Fi | radio globale, points d'accès, activation par AP, appareils associés et signal |
| Graphiques | séries RRD lues via `POST /rrd/` (base + champ + période), tracées en Canvas |
| Réglages | apparence (thèmes, palettes), infos de connexion, droits, empreinte, journaux, oubli de la box |
| Journaux | 800 dernières entrées (requêtes HTTP, erreurs, événements), copiables ou exportables en un geste |

Non encore couvert (jalons suivants) : AirMedia, appels et contacts, maison
connectée et alarme, télévision et PVR, machines virtuelles, VPN, NAT, contrôle
parental, Freeplug, switch, notifications, LCD.

## Corrections de la 0.2.0

- **Débit maximal** : `bandwidth_down`/`bandwidth_up` sont en bit/s ; une ligne
  à 5 000 000 bit/s s'affichait « 5000 Gbit/s ».
- **Explorateur** : les chemins doivent être encodés avec leur `/` initial
  (`/Disque dur`), sinon la box répond 404 et aucun dossier ne s'ouvre.
- **Wi-Fi** : la configuration globale est sur `wifi/config/` (et non `wifi/`).
- **Courbes** : RRD s'interroge en `POST /rrd/` avec `{db, fields, date_start,
  date_end}` ; la réponse est une liste d'objets `{time, <champ>}`.
- **Température CPU** : lecture des capteurs `sensors` des firmwares récents,
  puis `temp_cpub`, puis `temp_cpum`.
- **Thème** : choisir une palette désactive les couleurs dynamiques (sinon
  Android continuait d'imposer la sienne) ; le mode OLED et les palettes
  s'appliquent réellement. Empreinte du certificat affichée par groupes
  sélectionnables et copiables.
- **Téléchargements** : `downloads/add` attend un corps `x-www-form-urlencoded`
  avec `download_url` et `download_dir` (base64), et non du JSON ; l'effacement
  passe par `DELETE downloads/{id}/erase`.
- **Appareils** : rafraîchissement toutes les 5 s, débits par appareil quand la
  box les expose (et via les stations Wi-Fi), fiche détaillée au toucher.

## Architecture

```
core/
  AppGraph          conteneur de dépendances (pas de kapt/KSP)
  model/            BoxConfig, AppSettings, ThemeMode, AccentPalette
  storage/          DataStore + chiffrement Keystore de l'app_token
  net/              FreeboxClient (session, erreurs, sérialisation), TOFU, HMAC-SHA1
  discovery/        /api_version
  data/             BoxRepository + DTO par groupe d'API
ui/
  theme/            schémas Material 3 (clair / sombre / OLED), palettes, motif
  components/       scaffolding, cartes, pills, graphique Canvas
  setup/ home/ downloads/ files/ system/ network/ wifi/ graphs/ settings/
```

### Authentification

1. `POST /login/authorize/` avec `app_id`, `app_name`, `app_version`, `device_name`
   → `app_token` + `track_id`.
2. `GET /login/authorize/{track_id}` jusqu'à `granted` (validation sur l'écran de la box).
3. `GET /login/` → `challenge`, puis
   `password = hex(HMAC-SHA1(clé = app_token, message = challenge))`.
4. `POST /login/session/` avec `app_id` + `password` → `session_token`, envoyé
   ensuite dans `X-Fbx-App-Auth`.

L'`app_token` est chiffré en AES-GCM avec une clé du Keystore Android avant
d'être stocké ; le jeton de session reste en mémoire. Toute réponse
`auth_required` / `invalid_session` déclenche une seule reprise de session.

### Confiance TLS

Le certificat d'une Freebox est auto-signé : la chaîne ne peut pas être validée
par les autorités système. OpenFbx applique un **TOFU** : l'empreinte SHA-256 du
certificat est relevée à la première connexion, affichée pendant l'appairage,
puis exigée à chaque connexion. Une empreinte différente est refusée (message
explicite, réinitialisable dans Réglages). Aucune vérification n'est désactivée
silencieusement.

### Thème

- **Trois modes** : Clair, Sombre (violet très profond, cartes anthracite) et
  OLED (fond noir absolu, cartes à peine relevées), plus « Système ».
- **Huit palettes d'accent** (Violet, Indigo, Bleu, Turquoise, Vert, Ambre,
  Rose, Rouge). Les rôles Material 3 sont dérivés par tons, donc les contrastes
  restent lisibles dans les trois modes.
- **Couleurs dynamiques** (Android 12+) : quand elles sont actives, la palette
  manuelle est masquée, comme le veut Material You.
- Motif de pétales translucides en haut des écrans, coins très arrondis,
  titres de section dans la couleur d'accent.

### Téléchargements et fichiers

| Action | Requête |
|---|---|
| Lister les téléchargements | `GET downloads/` |
| Ajouter un lien / magnet | `POST downloads/add` avec `download_url` |
| Pause / reprise / réessai | `PUT downloads/{id}` avec `status` = `stopped` / `downloading` / `retry` |
| Retirer la tâche | `DELETE downloads/{id}` |
| Retirer et effacer les fichiers | `POST downloads/{id}/erase` |
| Lister un dossier | `GET fs/ls/{chemin}` (chemin base64 avec `/` initial, `%2F`/`%2B`/`%3D` échappés) |
| Envoyer un fichier | `ws/upload` : `upload_start` (dirname base64, filename, size), trames binaires de 512 Kio, puis `upload_finalize` |
| Ajouter un `.torrent` | `POST downloads/add` en multipart, champ `download_file` |
| Créer un dossier | `POST fs/mkdir/` avec `parent` (base64) et `dirname` |
| Renommer | `POST fs/rename/` avec `src` (base64) et `dst` |
| Supprimer | `POST fs/rm/` avec `files` (liste de chemins base64) |

L'avancement d'un téléchargement est calculé depuis `rx_bytes` / `size` (avec
repli sur `rx_pct`), et les caractères spéciaux des chemins base64 sont
percent-encodés pour rester dans un seul segment d'URL.

### Débits

`bandwidth_down` / `bandwidth_up` sont exprimés en **bits par seconde** par
Freebox OS. `formatLinkSpeed` convertit explicitement (kbit/s, Mbit/s ou
Gbit/s) : une ligne à `5 000 000` bit/s s'affiche « 5 Mbit/s ». Auparavant ces
bits étaient pris pour des mégabits, ce qui affichait « 5000 Gbit/s ».

### Versions

Kotlin 2.4.21 · Compose BOM 2026.06.00 · AGP 8.13.2 · `compileSdk` 36 ·
`minSdk` 26 · OkHttp 5.4.0 · kotlinx.serialization 1.10.0.

Chaque push sur `main` compile l'APK et lance les tests via GitHub Actions ;
un tag `v*` attache automatiquement l'APK à la release (`.github/workflows/android.yml`).

## Compiler

```bash
bash tools/setup-android-sdk.sh          # JDK 21 + Gradle 8.14.3 + SDK 36
echo "sdk.dir=$HOME/android-sdk" > local.properties
./gradlew assembleDebug                  # APK dans app/build/outputs/apk/debug/
./gradlew test                           # tests unitaires
```

Ou ouvrir le dossier dans Android Studio (Gradle 8.14.3, JDK 17+).

## Limites connues

- **Non testé contre une vraie Freebox** : l'appairage exige de valider sur
  l'écran du boîtier et un accès au réseau local. Les contrats d'API suivent la
  documentation Freebox OS et les clients libres existants ; certains champs
  varient selon le firmware.
- Les états de téléchargement et la valeur `status` acceptée en écriture
  (`stopped` / `downloading` / `retry`) varient selon les firmwares : une erreur
  est affichée en français plutôt que masquée, et tracée dans le journal.
- Selon le firmware, `fs/ls` renvoie ou non le champ `path` des entrées ; quand
  il manque, le chemin est reconstruit puis encodé localement.
- Les débits par appareil ne sont pas exposés par tous les firmwares : la fiche
  détaillée l'indique explicitement quand la donnée manque.
- L'envoi de fichiers utilise le WebSocket `ws/upload` documenté ; il n'a pas pu
  être validé sur une box réelle.
- L'unité du débit de synchronisation xDSL (`connection/xdsl`) est supposée être
  le bit/s, comme le reste du module connexion.
- Les modules absents d'un modèle sont détectés par échec d'appel et simplement
  masqués (`optional { }` dans `BoxRepository`).
- Les noms de séries **RRD** ne sont pas standardisés : l'écran Graphiques
  propose des suggestions et accepte un champ saisi à la main.
- Aucune synchronisation en arrière-plan, aucun widget, pas de mode hors ligne.

## Licence

À définir.
