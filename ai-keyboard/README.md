# Clavier RP 2.0

Clavier Android (IME) avec assistance IA : sur appui d'un bouton, il lit l'écran,
comprend le dernier message reçu et propose des réponses. Tu choisis, tu
modifies, **tu envoies toi-même**.

## Ce que fait la v2 de plus que la v1

| | v1 | v2 |
|---|---|---|
| Réponses mal formées | du JSON finissait dans le champ | **impossible** : extraction stricte, aucun repli sur le brut |
| Fournisseurs | WaveSpeed en dur | 7 au choix, clé par fournisseur |
| Contexte | la capture seule | journal de conversation persistant + compactage |
| Pilotage | prompt fixe | mode adaptatif ou consigne unique |
| Consigne ponctuelle | — | bouton ⚡ : ce que tu tapes devient la consigne |
| Hors jeu de rôle | — | profil « écris comme moi » |

### Le bug de la v1, corrigé à la racine

La v1 repliait sur le corps HTTP brut quand l'extraction échouait : d'où le JSON
collé dans les messages. La v2 ne replie **jamais**. `Sanitizer` procède en cinq
étapes — extraction stricte par fournisseur, recollage d'un flux SSE, déballage
en boucle jusqu'à point fixe (fences markdown, objets JSON, listes de variantes,
échappements `\uXXXX`, guillemets enveloppants, préfixes de rôle, balises
`<think>`), puis validation finale : si ça ressemble encore à du JSON, on refuse.

En cas d'échec, une erreur typée s'affiche dans la barre du clavier. Rien n'entre
dans le champ de saisie.

Le prompt impose en amont un contrat de sortie en texte brut, séparé par `###` :
pas de JSON demandé, donc rien à déballer dans le cas normal.

## Comment ça marche

1. Tu es dans ta conversation, le clavier est affiché.
2. **✨ Lire l'écran** — ou **⚡** si tu veux d'abord taper une consigne dans le champ.
3. Android affiche sa fenêtre d'autorisation de capture (une fois par session).
4. Une seule image est prise, puis le display virtuel est relâché.
5. Les propositions apparaissent au-dessus des touches.
6. Tu en touches une → elle s'écrit dans le champ. Tu relis, tu modifies, tu envoies.

Rien ne tourne en continu : aucune capture entre deux appuis. Une notification
reste visible tant que la session de lecture est ouverte, avec un bouton
**Arrêter**.

Le message que tu choisis entre dans le journal du fil : le contexte s'enrichit
à mesure, par application.

## Installation

> **Si la v1 est déjà installée, désinstalle-la d'abord** : la clé de signature a
> changé entre les deux, Android refusera la mise à jour sinon. À partir de la
> v2 le keystore est conservé, les prochaines mises à jour s'installeront
> par-dessus.

1. Installer l'APK (autoriser les sources inconnues).
2. Ouvrir **Clavier RP** → *Ouvrir les réglages de clavier* → activer.
3. *Choisir le clavier actif* → Clavier RP.
4. Remplir les réglages, puis **Tester la connexion**.

La clé WaveSpeed est incluse dans le build : laisse le champ Clé API vide pour
l'utiliser. Toute clé saisie prend le dessus.

## Réglages

| Champ | Rôle |
|---|---|
| Fournisseur | WaveSpeed, OpenAI, Anthropic, Mistral, Groq, DeepSeek, compatible OpenAI |
| Clé API | par fournisseur ; vide = clé incluse (WaveSpeed seulement) |
| Modèle | identifiant libre ; **doit supporter la vision** pour lire l'écran |
| URL personnalisée | pour une autre passerelle compatible OpenAI |
| Pilotage | adaptatif (l'IA s'accorde au fil) ou consigne unique |
| Mode jeu de rôle | décoché = conversation ordinaire avec ton style habituel |
| Personnages, univers | le cadre du RP |
| Mon style d'écriture | utilisé hors jeu de rôle |
| Nombre de propositions | 1 à 5 |

## Build

Pas de Gradle, pas de SDK Google, pas d'AndroidX : `aapt2` + `javac` + `dx` +
`apksigner`.

```bash
export WAVESPEED_KEY=...      # ou : echo '...' > local.key
./build.sh
# -> build/clavier-rp.apk
```

La clé est injectée à la compilation dans `Settings.BUILTIN_KEY`. Le dépôt ne
contient que le placeholder `@WAVESPEED_KEY@` ; `local.key`, `build/`, `*.jks` et
les `*.apk` sont ignorés par git.

Dépendances (Debian/Ubuntu) :

```bash
apt-get install -y aapt apksigner dalvik-exchange zipalign
# + un jar framework API 34 en -I/-classpath (variable AJAR)
```

## Tests

29 tests sur la logique, exécutables sur la JVM sans appareil :

```bash
S=/tmp/rp && mkdir -p $S
javac --release 8 -cp $AJAR -d $S $(find src/com/lvx/rpkeyboard/ai -name '*.java') \
      SanitizerTest.java LogTest.java
java -cp "$S:$AJAR" SanitizerTest   # 17 cas
java -cp "$S:$AJAR" LogTest         # 12 cas
```

`SanitizerTest` couvre les formes normales, les formes dégradées (JSON, fences,
SSE, préfixes de rôle) et les pièges : texte légitime contenant `{accolades}`,
apostrophes internes, guillemets internes à conserver.

`LogTest` couvre le recollage par recouvrement : prolongation, remontée du fil,
fenêtre déjà connue, absence de recouvrement — et le piège de deux « ok »
successifs légitimes qui ne doivent pas fusionner.

## Structure

| Fichier | Rôle |
|---|---|
| `ai/Sanitizer` | nettoyage strict des réponses, erreurs typées |
| `ai/Provider` | les 7 fournisseurs, URL, forme, authentification |
| `ai/LlmClient` | requête HTTP, formes OpenAI et Anthropic, vision |
| `ai/PromptBuilder` | prompt système, contrat de sortie, modes |
| `ai/ConversationLog` | journal du fil, recollage par recouvrement |
| `ai/ContextCompactor` | réduction au budget, résumé local gratuit |
| `ai/AiEngine` | orchestration |
| `data/Settings` | réglages, clé incluse, persistance du journal |
| `RpKeyboardService` | clavier AZERTY, barre ✨ / ⚡, chips |
| `CaptureActivity` / `CaptureService` | autorisation puis capture d'une image |

## Limites connues

- L'appel à l'API n'a pas pu être testé depuis l'environnement de build
  (`wavespeed.ai` bloqué par le proxy) : le premier *Tester la connexion* sur le
  téléphone fait foi.
- Les identifiants de modèles proposés viennent de recherches web, pas du
  catalogue du compte : à vérifier.
- Pas de mode automate : il demande un service d'accessibilité, non inclus ici.
- `minSdk 29`, `targetSdk 34`.
