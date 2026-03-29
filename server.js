/**
 * LVX TikTok Bridge Service
 * 
 * Connects to TikTok Lives and forwards events to LVX Games Worker.
 * Deploy on Railway/Render for persistent connections.
 */

const express = require('express');
const cors = require('cors');
const { WebcastPushConnection } = require('tiktok-live-connector');

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3000;
const WORKER_URL = 'https://lvxtiktok.tano-lsm-7.workers.dev';

// Active connections: Map<sessionId, { connection, username, startTime }>
const activeConnections = new Map();

// Gift emoji mapping
const GIFT_EMOJIS = {
  "Rose": "🌹", "TikTok": "🎵", "Ice Cream Cone": "🍦", "Finger Heart": "🫰",
  "Perfume": "🧴", "Doughnut": "🍩", "Hand Heart": "💜", "Little Crown": "👑",
  "Love Bang": "💥", "Birthday Cake": "🎂", "Sunglasses": "🕶️", "Corgi": "🐕",
  "Galaxy": "🌌", "Drama Queen": "👸", "Lion": "🦁", "Universe": "✨"
};

// Health check
app.get('/', (req, res) => {
  res.json({ 
    status: 'ok', 
    service: 'LVX TikTok Bridge',
    activeConnections: activeConnections.size,
    uptime: process.uptime()
  });
});

// Get active connections
app.get('/connections', (req, res) => {
  const connections = [];
  for (const [sessionId, data] of activeConnections) {
    connections.push({
      sessionId,
      username: data.username,
      startTime: data.startTime,
      duration: Date.now() - data.startTime
    });
  }
  res.json({ connections });
});

// Start connection to TikTok Live
app.post('/connect', async (req, res) => {
  const { username, sessionId } = req.body;
  
  if (!username || !sessionId) {
    return res.status(400).json({ error: 'username and sessionId required' });
  }

  // Clean username
  const cleanUsername = username.replace('@', '').trim();
  
  // Check if already connected
  if (activeConnections.has(sessionId)) {
    return res.json({ 
      success: true, 
      message: 'Already connected',
      sessionId 
    });
  }

  try {
    console.log(`[${sessionId}] Connecting to @${cleanUsername}...`);
    
    const tiktokConnection = new WebcastPushConnection(cleanUsername, {
      processInitialData: true,
      enableExtendedGiftInfo: true,
      enableWebsocketUpgrade: true,
      requestPollingIntervalMs: 2000,
      sessionId: sessionId
    });

    // Store connection
    activeConnections.set(sessionId, {
      connection: tiktokConnection,
      username: cleanUsername,
      startTime: Date.now()
    });

    // Connect to TikTok
    const state = await tiktokConnection.connect();
    console.log(`[${sessionId}] Connected to @${cleanUsername} - Room ID: ${state.roomId}`);

    // Send initial connected event
    await sendEventToWorker(sessionId, {
      eventType: 'connected',
      roomId: state.roomId,
      viewerCount: state.viewerCount || 0
    });

    // ===== EVENT HANDLERS =====

    // Gifts
    tiktokConnection.on('gift', async (data) => {
      console.log(`[${sessionId}] Gift: ${data.giftName} x${data.repeatCount} from ${data.nickname}`);
      
      await sendEventToWorker(sessionId, {
        eventType: 'gift',
        giftName: data.giftName,
        giftEmoji: GIFT_EMOJIS[data.giftName] || '🎁',
        giftCoins: data.diamondCount || 1,
        repeatCount: data.repeatCount || 1,
        viewer: {
          uniqueId: data.uniqueId,
          nickname: data.nickname,
          profilePictureUrl: data.profilePictureUrl
        }
      });
    });

    // Likes
    tiktokConnection.on('like', async (data) => {
      console.log(`[${sessionId}] Like: ${data.likeCount} from ${data.nickname}`);
      
      await sendEventToWorker(sessionId, {
        eventType: 'like',
        likeCount: data.likeCount || 1,
        totalLikes: data.totalLikeCount || 0,
        viewer: {
          uniqueId: data.uniqueId,
          nickname: data.nickname,
          profilePictureUrl: data.profilePictureUrl
        }
      });
    });

    // Comments
    tiktokConnection.on('chat', async (data) => {
      console.log(`[${sessionId}] Chat: ${data.nickname}: ${data.comment}`);
      
      await sendEventToWorker(sessionId, {
        eventType: 'comment',
        comment: data.comment,
        viewer: {
          uniqueId: data.uniqueId,
          nickname: data.nickname,
          profilePictureUrl: data.profilePictureUrl
        }
      });
    });

    // Follows
    tiktokConnection.on('follow', async (data) => {
      console.log(`[${sessionId}] Follow: ${data.nickname}`);
      
      await sendEventToWorker(sessionId, {
        eventType: 'follow',
        viewer: {
          uniqueId: data.uniqueId,
          nickname: data.nickname,
          profilePictureUrl: data.profilePictureUrl
        }
      });
    });

    // Shares
    tiktokConnection.on('share', async (data) => {
      console.log(`[${sessionId}] Share: ${data.nickname}`);
      
      await sendEventToWorker(sessionId, {
        eventType: 'share',
        viewer: {
          uniqueId: data.uniqueId,
          nickname: data.nickname,
          profilePictureUrl: data.profilePictureUrl
        }
      });
    });

    // Viewer count updates
    tiktokConnection.on('roomUser', async (data) => {
      await sendEventToWorker(sessionId, {
        eventType: 'viewerCount',
        viewerCount: data.viewerCount
      });
    });

    // Disconnection
    tiktokConnection.on('disconnected', () => {
      console.log(`[${sessionId}] Disconnected from @${cleanUsername}`);
      activeConnections.delete(sessionId);
      
      sendEventToWorker(sessionId, {
        eventType: 'disconnected'
      });
    });

    // Stream ended
    tiktokConnection.on('streamEnd', () => {
      console.log(`[${sessionId}] Stream ended for @${cleanUsername}`);
      activeConnections.delete(sessionId);
      
      sendEventToWorker(sessionId, {
        eventType: 'streamEnded'
      });
    });

    // Error handling
    tiktokConnection.on('error', (err) => {
      console.error(`[${sessionId}] Error:`, err.message);
    });

    res.json({ 
      success: true, 
      message: `Connected to @${cleanUsername}`,
      sessionId,
      roomId: state.roomId,
      viewerCount: state.viewerCount
    });

  } catch (err) {
    console.error(`[${sessionId}] Connection failed:`, err.message);
    activeConnections.delete(sessionId);
    
    let errorMessage = 'Failed to connect';
    if (err.message.includes('not found')) {
      errorMessage = 'User not found or not currently live';
    } else if (err.message.includes('LIVE has ended')) {
      errorMessage = 'This user is not currently live';
    }
    
    res.status(400).json({ 
      error: errorMessage,
      details: err.message 
    });
  }
});

// Disconnect from TikTok Live
app.post('/disconnect', (req, res) => {
  const { sessionId } = req.body;
  
  if (!sessionId) {
    return res.status(400).json({ error: 'sessionId required' });
  }

  const connData = activeConnections.get(sessionId);
  if (connData) {
    try {
      connData.connection.disconnect();
    } catch (e) {
      // Ignore disconnect errors
    }
    activeConnections.delete(sessionId);
    console.log(`[${sessionId}] Manually disconnected`);
    res.json({ success: true, message: 'Disconnected' });
  } else {
    res.json({ success: true, message: 'No active connection found' });
  }
});

// Check connection status
app.get('/status/:sessionId', (req, res) => {
  const { sessionId } = req.params;
  const connData = activeConnections.get(sessionId);
  
  if (connData) {
    res.json({
      connected: true,
      username: connData.username,
      duration: Date.now() - connData.startTime
    });
  } else {
    res.json({ connected: false });
  }
});

// Helper: Send event to Worker
async function sendEventToWorker(sessionId, eventData) {
  try {
    const response = await fetch(`${WORKER_URL}/api/session/${sessionId}/event`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ...eventData,
        timestamp: Date.now()
      })
    });
    
    if (!response.ok) {
      console.error(`[${sessionId}] Worker error:`, response.status);
    }
  } catch (err) {
    console.error(`[${sessionId}] Failed to send to worker:`, err.message);
  }
}

// Cleanup on shutdown
process.on('SIGTERM', () => {
  console.log('Shutting down...');
  for (const [sessionId, data] of activeConnections) {
    try {
      data.connection.disconnect();
    } catch (e) {}
  }
  process.exit(0);
});

app.listen(PORT, () => {
  console.log(`🚀 LVX TikTok Bridge running on port ${PORT}`);
  console.log(`📡 Forwarding events to ${WORKER_URL}`);
});
