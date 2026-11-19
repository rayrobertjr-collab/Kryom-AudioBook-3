# Livres audio — lecteur Android inspiré de Voice

Interface Material 3 (Material You : les couleurs suivent ton fond d'écran), grosses pochettes, gros boutons.

## Fonctions
- **Import par dossier** : ⊕ en haut → choisir le dossier. Deux modes (comme Voice) :
  - *Plusieurs livres* : chaque sous-dossier = un livre (ses fichiers = chapitres, tri 1, 2, … 10), et chaque fichier seul (ex. `.m4b`) = un livre.
  - *Un seul livre* : tout le dossier = un livre.
- **Reprise exacte** : livre, chapitre, position et vitesse sont mémorisés (toutes les 5 s, à chaque pause, changement de chapitre, fermeture). Au retour, on reprend là où on était.
- Lecture en arrière-plan, notification, écran verrouillé, écouteurs Bluetooth (bouton lecture = reprend le dernier livre), pause automatique si on débranche les écouteurs.
- **Couleurs du livre** : le fond du lecteur (dégradé) et tous ses menus prennent les couleurs de la pochette.
- **Barre pilule** : minuterie, vitesse, marque-pages + historique, sauter les silences, amplification du volume (graphique animé).
- **Bibliothèque en sections** : En cours / Pas commencés / Terminés, avec filtre.
- **Menu ⋮ d'un livre** (ou appui long) : modifier titre/auteur, couverture depuis Internet, couverture depuis les fichiers, recommencer, terminé, supprimer.
- Reculer 10 s / avancer 30 s, chapitre précédent/suivant, liste des chapitres.
- Vitesse 0,5x → 3x (par livre).
- Minuterie de sommeil (5 à 90 min).
- Pochettes : `cover.jpg` / `folder.jpg` du dossier, sinon l'image intégrée au fichier.
- Formats : MP3, M4B, M4A, AAC, OGG, OPUS, FLAC, WAV.

## Compiler l'APK

### Option A — Android Studio (le plus simple)
1. Installer Android Studio (Windows, Linux ou Mac).
2. *File → Open* → choisir le dossier `LivresAudio`. Laisser Gradle synchroniser (télécharge les dépendances la première fois).
3. *Build → Build App Bundle(s) / APK(s) → Build APK(s)*.
4. L'APK est dans `app/build/outputs/apk/debug/app-debug.apk`. Le copier sur le téléphone et l'ouvrir (autoriser « installer des applis inconnues »).
   Ou brancher le téléphone en USB (débogage USB activé) et cliquer ▶ Run.

### Option B — sans rien installer (GitHub)
1. Créer un dépôt GitHub et y téléverser le contenu du dossier.
2. Onglet *Actions* : la compilation part toute seule (~5 min).
3. Télécharger l'artefact `LivresAudio-apk` et installer l'APK sur le téléphone.

### Option C — ligne de commande (Linux, SDK Android installé)
```
export ANDROID_HOME=~/Android/Sdk
./gradlew assembleDebug
```

## Limites connues
- Un `.m4b` d'un seul fichier s'affiche comme un seul chapitre (les marqueurs de chapitres internes ne sont pas lus).
- Fichiers Audible `.aax`/`.aaxc` : protégés par DRM, non pris en charge.
