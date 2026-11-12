# ComeBack

Application Android perso de suivi d'habitudes : on définit ses objectifs, on les valide depuis l'app ou le widget, et l'**indice ComeBack** retrace la progression (+1 % pour une journée parfaite).

## Installer et mettre à jour (sans Android Studio)

1. Sur le téléphone, installer **Obtainium** ([github.com/ImranR98/Obtainium](https://github.com/ImranR98/Obtainium), section *Releases*).
2. Dans Obtainium : **Ajouter une appli** → coller `https://github.com/rakotobeMatthieu/Comeback` → **Ajouter** → **Installer**.
3. Android demande d'autoriser Obtainium à installer des applis : accepter.
4. À chaque nouvelle version publiée ici, Obtainium propose la mise à jour. Les données sont conservées.

Ajouter le widget : appui long sur l'écran d'accueil → *Widgets* → **ComeBack**.

## Fonctionnalités de la V1

- Objectifs avec jours au choix (tous les jours, semaine, week-end, à la carte) et rappel optionnel.
- Écran **Aujourd'hui** + **widget** pour valider en un tap ; notification de rappel avec bouton « Fait ».
- **Rattrapage** : la veille reste modifiable jusqu'à midi, puis la journée est figée.
- **Indice** : +1 % pour 100 %, prorata pour une journée partielle (positive dès 1/3), −0,5 % pour 0 %, amplifié si les jours négatifs s'enchaînent (jusqu'à ×3), 0 pour un jour sans objectif.
- **Évolution** (courbe de l'indice, variation par jour) et **Complétion** (calendrier, tableau par objectif).
- **Paliers** : palier N = l'indice qu'on aurait après N journées parfaites depuis une date de départ choisie.
- **Saisons** de 30 jours avec 3 défis et une récompense à définir.
- **Records** : séries, indice max, meilleurs 7 jours, meilleure série par objectif.
- **Sauvegarde** : export / import JSON dans les réglages ; journal d'erreurs copiable.

Tout fonctionne hors connexion : les données restent sur le téléphone.

## Technique

Kotlin · Jetpack Compose (Material 3) · Room · Glance (widget) · WorkManager (rappels).
L'APK est compilé et publié automatiquement par GitHub Actions à chaque push sur `main`
(`.github/workflows/build.yml`). Le journal de compilation est poussé sur la branche `ci-logs`.

La clé de signature n'est **pas** dans le dépôt : elle est fournie par le secret GitHub
`SIGNING_KEY` (keystore encodé en base64). Elle garantit que chaque version s'installe
par-dessus la précédente. Sans ce secret, l'app est compilée mais aucune version n'est publiée.

Logique de score : `app/src/main/java/com/rakotobe/comeback/domain/` (testée dans `app/src/test`).
