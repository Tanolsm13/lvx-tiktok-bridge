package com.lvx.rpkeyboard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.lvx.rpkeyboard.ime.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * Bulle flottante : declenche la lecture d'ecran <b>clavier ferme</b>.
 *
 * Sans clavier ouvert il n'y a pas de connexion au champ de saisie, donc rien
 * a inserer : la proposition choisie part dans le presse-papiers et il reste a
 * la coller. C'est la seule voie honnete sans service d'accessibilite.
 */
public class BubbleService extends Service implements Bus.Listener {

    public static final String ACTION_SHOW = "com.lvx.rpkeyboard.BUBBLE_SHOW";
    public static final String ACTION_HIDE = "com.lvx.rpkeyboard.BUBBLE_HIDE";

    private static final String CHANNEL = "bulle";
    private static final int NOTIF_ID = 43;

    private static boolean running = false;

    public static boolean isRunning() {
        return running;
    }

    /** L'autorisation de superposition se demande a l'utilisateur. */
    public static boolean canOverlay(Context ctx) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx);
    }

    public static Intent overlayPermissionIntent(Context ctx) {
        return new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + ctx.getPackageName()));
    }

    private final Handler ui = new Handler(Looper.getMainLooper());

    private WindowManager wm;
    private View bubble;
    private View card;
    private Theme theme = Theme.NUIT;
    private List<String> pending = new ArrayList<String>();

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Bus.add(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_HIDE.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!canOverlay(this)) {
            Toast.makeText(this, "Autorise l'affichage par-dessus les autres apps",
                    Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        theme = com.lvx.rpkeyboard.data.Settings.get(this).theme();
        startForegroundNotification();
        showBubble();
        running = true;
        return START_STICKY;
    }

    private void startForegroundNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null
                && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Bulle flottante", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, BubbleService.class).setAction(ACTION_HIDE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        Notification n = b
                .setContentTitle("Clavier RP")
                .setContentText("Bulle active")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Fermer", stop).build())
                .build();
        startForeground(NOTIF_ID, n);
    }

    // ---------------------------------------------------------------- bulle

    private WindowManager.LayoutParams overlayParams(int gravity) {
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = gravity;
        return lp;
    }

    private void showBubble() {
        if (bubble != null) {
            return;
        }
        TextView tv = new TextView(this);
        tv.setText("✨");
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Color.WHITE);
        tv.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable round = new GradientDrawable();
        round.setColor(theme.accentColor());
        round.setCornerRadius(dp(28));
        tv.setBackground(round);
        tv.setElevation(dp(8));

        final WindowManager.LayoutParams lp = overlayParams(Gravity.TOP | Gravity.START);
        lp.x = dp(12);
        lp.y = dp(220);

        tv.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - downX);
                        int dy = (int) (e.getRawY() - downY);
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) {
                            moved = true;
                        }
                        lp.x = startX + dx;
                        lp.y = startY + dy;
                        try {
                            wm.updateViewLayout(v, lp);
                        } catch (Exception ignored) {
                            // vue deja retiree
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            trigger();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });

        try {
            wm.addView(tv, lp);
            bubble = tv;
        } catch (Exception e) {
            Toast.makeText(this, "Bulle impossible : " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    /** Un appui lance la meme chaine que le bouton du clavier. */
    private void trigger() {
        com.lvx.rpkeyboard.data.Settings s = com.lvx.rpkeyboard.data.Settings.get(this);
        if (s.apiKey().isEmpty()) {
            toast("Aucune cle API : ouvre les reglages");
            return;
        }
        Intent base = new Intent(this, CaptureService.class)
                .putExtra(CaptureService.EXTRA_ONE_SHOT, "")
                .putExtra(CaptureService.EXTRA_THREAD, "bulle");

        if (CaptureService.isReady()) {
            startForegroundService(base.setAction(CaptureService.ACTION_CAPTURE));
        } else {
            CaptureActivity.pendingOneShot = "";
            CaptureActivity.pendingThread = "bulle";
            Intent i = new Intent(this, CaptureActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        }
    }

    // ----------------------------------------------------------------- carte

    private void showCard(List<String> replies) {
        hideCard();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(theme.bgColor());
        bg.setCornerRadius(dp(14));
        box.setBackground(bg);
        box.setElevation(dp(12));
        box.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView head = new TextView(this);
        head.setText("Touche une proposition : elle part dans le presse-papiers");
        head.setTextColor(theme.mutedColor());
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        head.setPadding(0, 0, 0, dp(8));
        box.addView(head);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                dp(300), dp(260)));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < replies.size(); i++) {
            final String text = replies.get(i);
            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextColor(theme.textColor());
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tv.setLineSpacing(0f, 1.15f);
            GradientDrawable card = new GradientDrawable();
            try {
                card.setColor(Color.parseColor(theme.chip));
            } catch (Exception e) {
                card.setColor(theme.bgColor());
            }
            card.setCornerRadius(dp(10));
            tv.setBackground(card);
            tv.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(7);
            tv.setLayoutParams(lp);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copy(text);
                    hideCard();
                    toast("Copie : colle dans ta conversation");
                }
            });
            list.addView(tv);
        }
        scroll.addView(list);
        box.addView(scroll);

        Button close = new Button(this);
        close.setText("Fermer");
        close.setAllCaps(false);
        close.setTextColor(theme.mutedColor());
        GradientDrawable cb = new GradientDrawable();
        try {
            cb.setColor(Color.parseColor(theme.special));
        } catch (Exception e) {
            cb.setColor(theme.bgColor());
        }
        cb.setCornerRadius(dp(10));
        close.setBackground(cb);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideCard();
            }
        });
        box.addView(close);

        WindowManager.LayoutParams lp = overlayParams(Gravity.CENTER);
        try {
            wm.addView(box, lp);
            card = box;
        } catch (Exception ignored) {
            // superposition refusee
        }
    }

    private void hideCard() {
        if (card != null) {
            try {
                wm.removeView(card);
            } catch (Exception ignored) {
                // deja retiree
            }
            card = null;
        }
    }

    private void copy(String text) {
        try {
            ClipboardManager cm =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Clavier RP", text));
            }
        } catch (Exception ignored) {
            // presse-papiers indisponible
        }
    }

    private void toast(final String message) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(BubbleService.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------------------------------------------------ callbacks

    @Override
    public void onStatus(final String message) {
        if (message != null && !message.isEmpty()) {
            toast(message);
        }
    }

    @Override
    public void onError(final String message) {
        toast(message);
    }

    @Override
    public void onSuggestions(final List<String> replies) {
        pending = new ArrayList<String>(replies);
        ui.post(new Runnable() {
            @Override
            public void run() {
                showCard(pending);
            }
        });
    }

    @Override
    public void onDestroy() {
        running = false;
        Bus.remove(this);
        hideCard();
        if (bubble != null) {
            try {
                wm.removeView(bubble);
            } catch (Exception ignored) {
                // deja retiree
            }
            bubble = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
