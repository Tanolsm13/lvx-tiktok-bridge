package com.lvx.rpkeyboard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;

import com.lvx.rpkeyboard.ai.AiEngine;
import com.lvx.rpkeyboard.ai.ConversationLog;
import com.lvx.rpkeyboard.ai.LlmClient;
import com.lvx.rpkeyboard.ai.PromptBuilder;
import com.lvx.rpkeyboard.data.Settings;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Detient le jeton de capture pour la session et prend une image unique
 * a chaque demande explicite du clavier.
 */
public class CaptureService extends Service {

    public static final String ACTION_INIT = "com.lvx.rpkeyboard.INIT";
    public static final String ACTION_CAPTURE = "com.lvx.rpkeyboard.CAPTURE";
    public static final String ACTION_STOP = "com.lvx.rpkeyboard.STOP";

    public static final String EXTRA_RESULT_CODE = "code";
    public static final String EXTRA_RESULT_DATA = "data";
    public static final String EXTRA_ONE_SHOT = "oneShot";
    public static final String EXTRA_THREAD = "thread";

    private static final String CHANNEL = "capture";
    private static final int NOTIF_ID = 42;

    /** Vrai quand le jeton est encore valable : evite de redemander l'autorisation. */
    private static boolean ready = false;

    public static boolean isReady() {
        return ready;
    }

    private MediaProjection projection;
    private HandlerThread thread;
    private Handler handler;
    private boolean busy = false;

    @Override
    public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("capture");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            release();
            stopSelf();
            return START_NOT_STICKY;
        }

        startForegroundNotification();

        String oneShot = intent == null ? "" : intent.getStringExtra(EXTRA_ONE_SHOT);
        String threadKey = intent == null ? null : intent.getStringExtra(EXTRA_THREAD);

        if (ACTION_INIT.equals(action)) {
            int code = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            if (!openProjection(code, data)) {
                return START_NOT_STICKY;
            }
            capture(oneShot, threadKey);
        } else if (ACTION_CAPTURE.equals(action)) {
            if (projection == null) {
                Bus.error("Session de capture expiree, reappuie sur le bouton");
            } else {
                capture(oneShot, threadKey);
            }
        }
        return START_NOT_STICKY;
    }

    private void startForegroundNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null
                && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Lecture d'ecran", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }

        PendingIntent stop = PendingIntent.getService(
                this, 1,
                new Intent(this, CaptureService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);

        Notification n = b
                .setContentTitle("Clavier RP")
                .setContentText("Lecture d'ecran active pour cette session")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Arreter", stop).build())
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private boolean openProjection(int code, Intent data) {
        if (projection != null) {
            return true;
        }
        if (data == null) {
            Bus.error("Autorisation de capture manquante");
            return false;
        }
        MediaProjectionManager mgr =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mgr == null) {
            Bus.error("Capture d'ecran indisponible");
            return false;
        }
        projection = mgr.getMediaProjection(code, data);
        if (projection == null) {
            Bus.error("Impossible d'ouvrir la capture");
            return false;
        }
        // Obligatoire depuis Android 14.
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                release();
            }
        }, handler);
        ready = true;
        return true;
    }

    private void capture(final String oneShot, final String threadKey) {
        if (busy) {
            return;
        }
        busy = true;
        Bus.status("Lecture de l'ecran...");
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] jpeg = grabFrame();
                    Bus.status("L'IA ecrit...");

                    Settings s = Settings.get(CaptureService.this);
                    PromptBuilder.Config cfg = s.config(oneShot);
                    LlmClient client = new LlmClient(
                            s.provider(), s.baseUrl(), s.apiKey(), s.model())
                            .withMaxTokens(s.maxTokens());
                    ConversationLog log = s.loadLog(threadKey);

                    AiEngine engine = new AiEngine(client, cfg)
                            .withBudget(s.contextBudgetChars())
                            .withProgress(new AiEngine.Progress() {
                                @Override
                                public void onRetry(int attempt, int total, String why) {
                                    Bus.status("Nouvelle tentative " + attempt + "/" + total
                                            + " (" + why + ")");
                                }
                            });
                    AiEngine.Result result = log.isEmpty()
                            ? engine.fromScreenshot(jpeg)
                            : engine.fromBoth(log.items(), jpeg);

                    if (result.isOk()) {
                        Bus.suggestions(result.variants);
                        int asked = cfg.count;
                        if (result.variants.size() < asked) {
                            // On le dit plutot que de laisser croire a une seule idee.
                            Bus.status(result.variants.size() + " proposition"
                                    + (result.variants.size() > 1 ? "s" : "")
                                    + " sur " + asked + " renvoyees par le modele");
                        }
                    } else {
                        Bus.error(result.error);
                    }
                } catch (Exception e) {
                    Bus.error(e.getMessage() == null ? e.toString() : e.getMessage());
                } finally {
                    busy = false;
                }
            }
        });
    }

    /** Une seule image, puis on relache le display virtuel. */
    private byte[] grabFrame() throws Exception {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int density = dm.densityDpi;

        ImageReader reader = ImageReader.newInstance(
                width, height, android.graphics.PixelFormat.RGBA_8888, 2);
        VirtualDisplay display = projection.createVirtualDisplay(
                "rp-capture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, handler);

        try {
            // On laisse l'ecran se stabiliser (retour de la fenetre d'autorisation,
            // fin d'animation) et on garde la derniere image disponible.
            Image image = null;
            long deadline = System.currentTimeMillis() + 3000;
            long settleUntil = System.currentTimeMillis() + 700;
            while (System.currentTimeMillis() < deadline) {
                Image next = reader.acquireLatestImage();
                if (next != null) {
                    if (image != null) {
                        image.close();
                    }
                    image = next;
                    if (System.currentTimeMillis() >= settleUntil) {
                        break;
                    }
                }
                Thread.sleep(50);
            }
            if (image == null) {
                throw new Exception("Capture d'ecran vide");
            }

            try {
                Bitmap bitmap = toBitmap(image, width, height);
                Bitmap scaled = downscale(bitmap, 1100);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, bos);
                if (scaled != bitmap) {
                    scaled.recycle();
                }
                bitmap.recycle();
                return bos.toByteArray();
            } finally {
                image.close();
            }
        } finally {
            display.release();
            reader.close();
        }
    }

    private static Bitmap toBitmap(Image image, int width, int height) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int padding = rowStride - pixelStride * width;

        Bitmap full = Bitmap.createBitmap(
                width + padding / pixelStride, height, Bitmap.Config.ARGB_8888);
        full.copyPixelsFromBuffer(buffer);

        if (padding == 0) {
            return full;
        }
        Bitmap cropped = Bitmap.createBitmap(full, 0, 0, width, height);
        full.recycle();
        return cropped;
    }

    private static Bitmap downscale(Bitmap src, int maxWidth) {
        if (src.getWidth() <= maxWidth) {
            return src;
        }
        float ratio = (float) maxWidth / (float) src.getWidth();
        Matrix m = new Matrix();
        m.postScale(ratio, ratio);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    private void release() {
        ready = false;
        if (projection != null) {
            try {
                projection.stop();
            } catch (Exception ignored) {
                // deja arretee
            }
            projection = null;
        }
    }

    @Override
    public void onDestroy() {
        release();
        if (thread != null) {
            thread.quitSafely();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
