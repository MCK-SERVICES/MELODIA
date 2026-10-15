# Mélodia — lecteur de musique Android personnel

Lecteur pour les musiques déjà téléchargées sur ton téléphone, avec paroles synchronisées.
100 % gratuit : aucun compte, aucune clé, aucune pub.

## Fonctions
- Bibliothèque automatique : **Titres**, **Albums**, **Artistes**, recherche
- Lecture aléatoire, répétition (tout / un titre), titre suivant / précédent
- Lecture en arrière-plan + contrôles dans la notification et sur l'écran verrouillé
- Pause automatique quand on débranche les écouteurs
- **Paroles** : bouton « Paroles » dans le lecteur
  - Synchronisées (la ligne en cours s'allume, on peut toucher une ligne pour y sauter)
  - Trouvées automatiquement en ligne via **LRCLIB** (service gratuit et ouvert)
  - Si un fichier `.lrc` du même nom est à côté de la musique, il est utilisé
  - Mises en cache : une fois trouvées, elles marchent hors connexion
  - Bouton ✏️ pour corriger titre/artiste si la recherche échoue

## Obtenir l'APK gratuitement (sans ordinateur) — GitHub
1. Crée un compte gratuit sur github.com
2. Crée un dépôt (« New repository »), par ex. `melodia`, **privé** si tu veux
3. Envoie tous les fichiers de ce dossier dans le dépôt (en gardant les dossiers)
4. L'onglet **Actions** compile l'appli automatiquement (≈ 5 min)
5. Onglet **Releases** → télécharge `Melodia.apk` sur ton téléphone
6. Ouvre-le et autorise « Installer des applis inconnues » pour ton navigateur

## Avec un ordinateur (Android Studio, gratuit)
Ouvre ce dossier dans Android Studio → bouton ▶ Run, téléphone branché en mode débogage USB.

## Technique
Kotlin · Jetpack Compose · Media3 (ExoPlayer + MediaSession) · Android 8.0 minimum
