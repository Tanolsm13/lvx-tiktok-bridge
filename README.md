# LVX TikTok Bridge Service

Service Node.js qui connecte les Lives TikTok aux jeux interactifs LVX.

## Déploiement 1-Clic sur Render (Recommandé)

### Option A: Via GitHub (5 min)

1. **Créer un repo GitHub** avec ce dossier `tiktok-bridge`
2. Va sur https://render.com
3. Connecte-toi avec GitHub
4. Clique **"New" → "Web Service"**
5. Sélectionne ton repo
6. Settings:
   - Name: `lvx-tiktok-bridge`
   - Runtime: `Node`
   - Build Command: `npm install`
   - Start Command: `node server.js`
   - Plan: **Free**
7. Clique **"Create Web Service"**

### Option B: Via interface Render directe

1. Va sur https://dashboard.render.com/create?type=web
2. Choisis "Public Git repository"
3. Entre l'URL de ton repo GitHub
4. Configure comme ci-dessus

## Récupérer l'URL

Une fois déployé, Render te donne une URL comme:
```
https://lvx-tiktok-bridge.onrender.com
```

**Important:** Mets cette URL dans LVXbank.html à la ligne `TIKTOK_BRIDGE`

## Coût
- **Gratuit** (750h/mois - largement suffisant)
- Le service "dort" après 15min d'inactivité (se réveille en ~30s)

## Endpoints

| Endpoint | Méthode | Description |
|----------|---------|-------------|
| `/` | GET | Health check |
| `/connect` | POST | Connecter à un live TikTok |
| `/disconnect` | POST | Déconnecter |
| `/status/:sessionId` | GET | Vérifier le statut |
