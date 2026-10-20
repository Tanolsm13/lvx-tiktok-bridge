# Reprise Clavier RP 2.0 — état réel et corrections à appliquer

Rédigé après revue des sources du zip `clavier-rp-source.zip`.
Ce document corrige la passation `HANDOFF.md`, qui se trompe sur plusieurs points.

## 1. Le code n'est nulle part

`HANDOFF.md` dit « Tout le code est écrit, commité ». C'était un commit **local**,
dans un conteneur éphémère aujourd'hui détruit. Vérifié sur GitHub : le dépôt
`Tanolsm13/lvx-tiktok-bridge` ne contient que `master` et `ccr-53bf7e8b-d72102`.

**Le zip est la seule copie des 6284 lignes.** À pousser en priorité absolue.

## 2. Diagnostic réseau — la passation se trompe

Elle désigne quatre hôtes bloqués. En réalité, un seul l'est :

| Hôte | Passation | Réel |
|---|---|---|
| `dl.google.com` / `maven.google.com` | bloqué | **bloqué (403)** — AGP, AndroidX, Compose |
| `repo1.maven.org` | bloqué | joignable (mirror GCS ; 429 en direct) |
| `services.gradle.org` | bloqué | joignable (200) |

Conséquence : Google Maven est le seul blocage, mais c'est le fatal. Aucun build
local n'est possible sans AndroidX ni Compose. **La CI GitHub est le seul chemin.**

## 3. Chemin de build qui fonctionne

Le workflow `.github/workflows/build.yml` du zip est correct et se déclenche déjà
sur une branche nommée `clavier-rp`. Marche à suivre :

1. Créer la branche `clavier-rp` dont la **racine** est le contenu du dossier
   `clavier-rp/` du zip (`gradlew` et `app/` directement à la racine).
2. Pousser. La CI se déclenche seule.
3. L'APK sort en artefact de run, et en release `latest` si la branche est `main`.

Pour avoir la release téléchargeable depuis un téléphone sans compte GitHub,
changer dans le workflow :

```yaml
- name: Mettre à jour la release "latest"
  if: github.ref == 'refs/heads/clavier-rp'   # au lieu de refs/heads/main
```

## 4. Risques de la passation : un est déjà réglé

- **`menuAnchor(MenuAnchorType.PrimaryNotEditable)`** — signalé comme risque
  principal. En fait couvert : `SettingsActivity.kt` porte bien
  `@OptIn(ExperimentalMaterial3Api::class)` aux lignes 53 et 152, et l'API existe
  depuis Material3 1.3.0, que fournit compose-bom 2024.10.01. Rien à faire.
- **Versions** — AGP 8.7.3 / Kotlin 2.0.21 / Gradle 8.11.1 / compileSdk 35 :
  cohérentes (AGP 8.7 exige Gradle ≥ 8.9, compileSdk 35 exige AGP ≥ 8.6).
- **Dépendances Maven Central** — toutes vérifiées présentes : kotlin-stdlib
  2.0.21, kotlinx-coroutines 1.9.0, org.json 20240303, junit 4.13.2.
- **`org.json` en test** — le vrai risque restant. `android.jar` embarque un stub
  d'`org.json` qui peut primer sur la vraie implémentation selon l'ordre de
  classpath. Si `ResponseSanitizerTest` échoue avec des `null` inexplicables,
  c'est ça. Repli : `testImplementation` en premier, ou Robolectric.

## 5. Incohérences réelles à corriger

Trouvées en revue, non mentionnées dans la passation.

### a) Aucun fournisseur WaveSpeed

`ai/Providers.kt` propose Anthropic, OpenAI, Mistral, Google, Groq, DeepSeek et
un mode compatible-OpenAI. La clé disponible est une clé **WaveSpeed**. Elle ne
peut passer que par le mode compatible-OpenAI, avec :

```
base URL : https://llm.wavespeed.ai/v1
```

Ajouter une entrée `WAVESPEED` dans `ProviderId` avec cette URL par défaut évite
la configuration manuelle.

### b) Le défaut ne marchera pas

`data/Prefs.kt` :

```kotlin
ProviderId.valueOf(sp.getString("provider", null) ?: "ANTHROPIC")
get() = sp.getString("model_id", "claude-sonnet-4-5")!!
```

Premier lancement avec une clé WaveSpeed sur le fournisseur Anthropic : échec
d'authentification garanti. Mettre WaveSpeed et un modèle de son catalogue par
défaut.

### c) La clé n'est plus en dur

La v1 (APK de 29 Ko) avait la clé compilée dans `Prefs.API_KEY`. La v2 la stocke
chiffrée (`EncryptedSharedPreferences`) et attend une saisie manuelle dans les
réglages. C'est plus propre, mais c'est un changement de comportement non
documenté : il faudra saisir la clé une fois après installation.

Le repli Keystore de `Prefs.build()` est correct (`try/catch` vers des
préférences normales) — rien à corriger là.

## 6. Garde-fous du mode auto : vérifiés, à conserver

`screen/AutoReplyController.kt` est conforme à ce que décrit la passation, et le
câblage tient :

- `State.ACTIF` n'est atteignable que par `confirmDisclosureSent()` ;
- `ScreenReaderService.kt:163` ne l'appelle que sur `MessageSender.Outcome.Sent` ;
- `:173` appelle `failDisclosure()` sur `Outcome.Failed`, donc rien ne part si
  l'annonce n'est pas passée ;
- arrêts : frappe manuelle, secousse, plafond, expiration, changement de fil.

L'annonce préalable est la condition à laquelle ce mode a été construit. À garder.

## 7. Reste à faire après compilation

- Faire passer les 45 tests unitaires, jamais exécutés.
- Panneau emoji complet (la bande actuelle est minimale).
- Vérifier `MessageSender` sur WhatsApp, Instagram, Messenger, Signal : la
  détection du bouton d'envoi repose sur des heuristiques de `viewId` et de
  `contentDescription`, qui cassent à chaque refonte de ces apps.
- Polissage visuel : socle posé dans `Palette.kt` et `AiBarView`, à appliquer
  partout et à vérifier sur appareil réel.
