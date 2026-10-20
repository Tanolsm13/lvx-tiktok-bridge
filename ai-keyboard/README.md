# Clavier RP

Clavier Android (IME) avec un bouton de lecture d'écran à la demande, pour écrire
plus vite dans un jeu de rôle par messages.

## Comment ça marche

1. Tu es dans ta conversation, le clavier est affiché.
2. Tu appuies sur **✨ Lire l'écran**.
3. Android affiche sa fenêtre d'autorisation de capture (une fois par session).
4. Une seule image est prise, envoyée au modèle, puis le display virtuel est relâché.
5. 3 propositions apparaissent au-dessus des touches.
6. Tu en touches une → elle s'écrit dans le champ. Tu modifies si tu veux, **et c'est toi qui envoies**.

Rien ne tourne en continu : aucune capture entre deux appuis sur le bouton. Une
notification reste visible tant que la session de lecture est ouverte, avec un
bouton **Arrêter** pour la fermer immédiatement.

## Installation

1. Copier l'APK sur le téléphone, l'installer (autoriser les sources inconnues).
2. Ouvrir **Clavier RP** → *Ouvrir les réglages de clavier* → activer Clavier RP.
3. *Choisir le clavier actif* → sélectionner Clavier RP.
4. Remplir les réglages : ton personnage, le sien, l'univers du RP.
5. *Tester la connexion* pour vérifier la clé et le modèle.

## Réglages

| Champ | Rôle |
|---|---|
| Mon personnage | qui tu joues, ton ton, ta façon de parler |
| Son personnage | qui elle joue |
| Univers et ton | cadre, époque, ambiance, rythme |
| Modèle | identifiant WaveSpeed, **doit supporter la vision** |
| Nombre de propositions | 1 à 5 |

Le champ Modèle est libre : si un identifiant ne passe pas, colle-en un autre
depuis le catalogue WaveSpeed. Les presets affichés sous le champ sont des pistes,
pas des garanties — vérifie-les sur ton compte.

## Build

Pas de Gradle, pas de SDK Google : `aapt2` + `javac` + `dx` + `apksigner`.

```bash
export WAVESPEED_KEY=...      # ou bien : echo '...' > ai-keyboard/local.key
./build.sh
# -> build/clavier-rp.apk
```

La clé est injectée à la compilation dans `Prefs.API_KEY`. Le dépôt ne contient
que le placeholder `@WAVESPEED_KEY@` ; `local.key`, `build/` et les `*.apk` sont
ignorés par git.

Dépendances de build (Debian/Ubuntu) :

```bash
apt-get install -y aapt apksigner dalvik-exchange zipalign
# + le jar framework API 34 utilisé comme -I/-classpath (variable AJAR)
```

## Structure

| Fichier | Rôle |
|---|---|
| `RpKeyboardService` | clavier AZERTY, barre ✨, chips de suggestions |
| `CaptureActivity` | demande l'autorisation système de capture |
| `CaptureService` | foreground service `mediaProjection`, capture une image |
| `WaveSpeedClient` | POST `/chat/completions`, parsing tolérant des réponses |
| `Prefs` | réglages + prompt système |
| `SettingsActivity` | écran de configuration |

## Limites connues

- L'appel à l'API n'a pas pu être testé depuis l'environnement de build (host
  bloqué) : le premier *Tester la connexion* sur le téléphone fait foi.
- `minSdk 29`, `targetSdk 34`.
- Signé avec une clé de debug générée localement : désinstalle l'ancienne version
  avant de réinstaller si tu régénères le keystore.
