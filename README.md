# Mon Budget DZ — étape 3 (fonctionnalités avancées)

## Nouveautés par rapport à l'étape 2
- **5 onglets** : Accueil, Dépenses, Courses, Budget, Profil (+ bouton rapide « Dépense »). Revenus, Épargne, Factures et Statistiques s'ouvrent depuis l'Accueil et le Profil.
- **Budget** : budget global et par catégorie, par mois ; dépensé / restant / % consommé ; alertes aux seuils (70 % / 90 % par défaut, modifiables dans Profil) et au dépassement ; report du mois précédent sans écraser ni perdre l'historique.
- **Épargne** : objectifs, versements, retraits (refusés au-delà du montant épargné), historique, date cible, montant mensuel à épargner, objectif mensuel. Un versement est un transfert : le solde baisse une fois, sans dépense.
- **Factures** : échéances ; « Payer » crée une seule dépense (transaction).
- **Statistiques** : courbe du solde sur 6 mois (aussi sur l'Accueil), comparaison de deux mois par catégorie, dépenses les plus importantes.
- **Notifications locales** (WorkManager, aucun serveur), activables une par une.
- **Exports** : CSV (séparateur « ; », ouvre dans Excel), rapport mensuel PDF (revenus, dépenses, par catégorie, courses, épargne nette, solde de fin de mois, comparaison, graphiques).
- **Sauvegarde / restauration** JSON : le fichier est validé en entier avant tout changement, puis les données sont remplacées en une transaction (tout ou rien).
- Réglages mémorisés (DataStore) : seuils, jour du salaire, objectif mensuel, masquage des montants, rappels.
- Base Room **v2** avec migration 1→2 (données de l'étape 2 conservées).

## Ce qui reste (étape 4)
Verrouillage PIN / biométrie, chiffrement éventuel de la sauvegarde, tests Room sur appareil (migration, persistance, restauration), arabe/RTL, relecture complète des calculs et de la navigation après compilation réelle.

## Limites connues
- **Non compilé, non exécuté.** La migration 1→2 est écrite à la main : si Room signale « Migration didn't properly handle … », envoyez le message complet.
- Rappels : une vérification par jour vers 19 h (Android peut la décaler) ; rappel « courses » fixé au jeudi, « épargne » au 1er du mois.
- Notification : Android 13+ demande une autorisation à l'activation d'un rappel ; refusée, aucun rappel n'apparaît.
- Revenus d'un mois = par « mois de référence » ; solde de fin de mois (courbe, PDF) = par date d'opération.
- La sauvegarde n'est pas chiffrée. Pas encore de verrouillage de l'application.
- Le bouton « modifier » d'un budget rouvre le formulaire vierge (choisir la catégorie et saisir le nouveau montant).

## Prérequis, compilation, APK
Inchangés : Android Studio récent, JDK 17, AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.12.01, Room 2.6.1, Navigation 2.8.5, DataStore 1.1.1, WorkManager 2.9.1. minSdk 26, compileSdk/targetSdk 35.
1. Ouvrir le dossier dans Android Studio, attendre la synchronisation Gradle.
2. Tests : clic droit sur app/src/test > Run Tests.
3. APK : Build > Build APK(s) → app/build/outputs/apk/debug/app-debug.apk, à copier sur le téléphone (autoriser les sources inconnues).

## GitHub
- Intégration continue : `.github/workflows/android.yml` lance les tests et construit l'APK debug à chaque push (onglet Actions > dernier run > Artifacts > `mon-budget-dz-debug-apk`).
- Si le build échoue, le journal du run contient l'erreur exacte de compilation : copiez-la pour correction.
- Le dossier `app/schemas` (généré par Room) doit être commité.
- APK debug : signé avec la clé de débogage, utilisable pour des tests personnels, pas pour une publication sur le Play Store.
- Pas de `gradlew` dans le dépôt : Android Studio peut le générer (Gradle > wrapper) ou lancer `gradle wrapper --gradle-version 8.9` ; ensuite commitez `gradlew`, `gradlew.bat` et `gradle/wrapper/gradle-wrapper.jar`.
