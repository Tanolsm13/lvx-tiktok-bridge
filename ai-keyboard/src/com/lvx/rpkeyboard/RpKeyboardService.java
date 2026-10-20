package com.lvx.rpkeyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import com.lvx.rpkeyboard.ai.ChatMessage;
import com.lvx.rpkeyboard.ai.ConversationLog;
import com.lvx.rpkeyboard.data.Settings;
import com.lvx.rpkeyboard.ime.EmojiData;
import com.lvx.rpkeyboard.ime.Keys;
import com.lvx.rpkeyboard.ime.Theme;
import com.lvx.rpkeyboard.imagegen.ImageClient;
import com.lvx.rpkeyboard.voice.ElevenLabsClient;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/** Clavier AZERTY complet : IA, emoji, presse-papiers, themes. */
public class RpKeyboardService extends InputMethodService implements Bus.Listener {

    private enum Panel {
        KEYS, EMOJI, CLIP, SUGGEST
    }

    private static final long LONG_PRESS_MS = 320;
    private static final long DELETE_FIRST_MS = 380;
    private static final long DELETE_EVERY_MS = 55;

    /** Conservees pour survivre a la recreation de la vue apres la capture. */
    private static List<String> pending = new ArrayList<String>();
    /** Historique local du presse-papiers, le systeme n'en garde pas. */
    private static final LinkedList<String> clips = new LinkedList<String>();

    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private LinearLayout panelHost;
    private TextView status;
    private PopupWindow preview;
    private PopupWindow accentPopup;

    private Theme theme = Theme.NUIT;
    private boolean animate = true;
    private boolean haptics = true;
    private boolean shifted = false;
    private boolean symbols = false;
    private Panel panel = Panel.KEYS;
    private int emojiCategory = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        Bus.add(this);
    }

    @Override
    public void onDestroy() {
        dismissPopups();
        Bus.remove(this);
        super.onDestroy();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        dismissPopups();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public View onCreateInputView() {
        Settings s = Settings.get(this);
        theme = s.theme();
        animate = s.animations();
        haptics = s.haptics();
        panel = Panel.KEYS;

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        applyBackground(root, s);
        int pad = dp(6);
        root.setPadding(pad, pad, pad, pad);

        root.addView(buildToolbar());

        panelHost = new LinearLayout(this);
        panelHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(panelHost);
        if (!pending.isEmpty()) {
            panel = Panel.SUGGEST;
        }
        renderPanel(false);
        return root;
    }

    /** Fond uni du theme, ou image choisie sous un voile. */
    private void applyBackground(View target, Settings s) {
        String path = s.background();
        if (!path.isEmpty()) {
            try {
                File f = new File(path);
                if (f.exists()) {
                    Bitmap bmp = BitmapFactory.decodeFile(path);
                    if (bmp != null) {
                        BitmapDrawable image = new BitmapDrawable(getResources(), bmp);
                        image.setGravity(Gravity.FILL);
                        int alpha = Math.round(255f * s.scrim() / 100f);
                        int base = theme.bgColor();
                        ColorDrawable scrim = new ColorDrawable(Color.argb(alpha,
                                Color.red(base), Color.green(base), Color.blue(base)));
                        target.setBackground(new LayerDrawable(new Drawable[]{image, scrim}));
                        return;
                    }
                }
            } catch (Exception ignored) {
                // image illisible : fond uni
            }
        }
        target.setBackgroundColor(theme.bgColor());
    }

    // -------------------------------------------------------------- toolbar

    private View buildToolbar() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(2), dp(2), dp(2), dp(6));

        bar.addView(tool("✨  Lire l'écran", theme.accent, theme.textColor(),
                new Runnable() {
                    @Override
                    public void run() {
                        requestCapture("");
                    }
                }));
        bar.addView(tool("⚡", theme.special, Color.parseColor("#E8C86A"),
                new Runnable() {
                    @Override
                    public void run() {
                        useFieldAsInstruction();
                    }
                }));
        bar.addView(tool("🎤", theme.special, Color.parseColor("#8FD0E8"),
                new Runnable() {
                    @Override
                    public void run() {
                        makeVoice();
                    }
                }));
        bar.addView(tool("🌄", theme.special, Color.parseColor("#A6E0B0"),
                new Runnable() {
                    @Override
                    public void run() {
                        makeImage();
                    }
                }));
        bar.addView(tool("😀", theme.special, theme.textColor(),
                new Runnable() {
                    @Override
                    public void run() {
                        togglePanel(Panel.EMOJI);
                    }
                }));
        bar.addView(tool("📋", theme.special, theme.textColor(),
                new Runnable() {
                    @Override
                    public void run() {
                        togglePanel(Panel.CLIP);
                    }
                }));

        status = new TextView(this);
        status.setTextColor(theme.mutedColor());
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setPadding(dp(8), 0, dp(8), 0);
        status.setMaxLines(2);
        status.setMinWidth(dp(80));
        status.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(status);

        bar.addView(tool("⚙", theme.special, theme.mutedColor(), new Runnable() {
            @Override
            public void run() {
                Intent i = new Intent(RpKeyboardService.this, SettingsActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        }));

        scroll.addView(bar);
        return scroll;
    }

    private Button tool(String label, String bg, int fg, final Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setBackground(pill(bg));
        b.setElevation(dp(3));
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        tapFeedback(b, bg, action);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(5);
        b.setLayoutParams(lp);
        return b;
    }

    // --------------------------------------------------------------- panneaux

    private void togglePanel(Panel wanted) {
        panel = (panel == wanted) ? Panel.KEYS : wanted;
        renderPanel(true);
    }

    /** Volet anime : glissement et fondu a chaque changement de panneau. */
    private void renderPanel(boolean withAnimation) {
        panelHost.removeAllViews();
        switch (panel) {
            case EMOJI:
                panelHost.addView(buildEmojiPanel());
                break;
            case CLIP:
                panelHost.addView(buildClipPanel());
                break;
            case SUGGEST:
                panelHost.addView(buildSuggestPanel());
                break;
            default:
                panelHost.addView(buildKeysPanel());
        }
        if (withAnimation && animate) {
            panelHost.setAlpha(0f);
            panelHost.setTranslationY(dp(14));
            panelHost.animate().alpha(1f).translationY(0f).setDuration(170).start();
        } else {
            panelHost.setAlpha(1f);
            panelHost.setTranslationY(0f);
        }
    }


    /**
     * Propositions en liste verticale, texte entier.
     *
     * Les chips horizontales coupaient a 140 caracteres sur trois lignes : on
     * ne pouvait pas relire une reponse avant de la choisir, ce qui est tout
     * l'interet. Ici chaque proposition s'affiche en entier, la liste defile.
     */
    private View buildSuggestPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView header = new TextView(this);
        header.setText(pending.size() + (pending.size() > 1
                ? " propositions \u2014 touche celle que tu veux"
                : " proposition \u2014 touche-la pour l'inserer"));
        header.setTextColor(theme.mutedColor());
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        header.setPadding(dp(10), dp(2), dp(10), dp(8));
        box.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(206)));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < pending.size(); i++) {
            final String text = pending.get(i);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(pill(theme.chip));
            card.setElevation(dp(3));
            card.setPadding(dp(13), dp(11), dp(13), dp(11));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = dp(7);
            card.setLayoutParams(clp);

            TextView num = new TextView(this);
            num.setText("Proposition " + (i + 1) + "  \u00B7  " + text.length() + " caracteres");
            num.setTextColor(theme.mutedColor());
            num.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            num.setPadding(0, 0, 0, dp(5));
            card.addView(num);

            TextView body = new TextView(this);
            body.setText(text);
            body.setTextColor(theme.textColor());
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.setLineSpacing(0f, 1.15f);
            card.addView(body);

            tapFeedback(card, theme.chip, new Runnable() {
                @Override
                public void run() {
                    commit(text);
                    rememberSent(text);
                    pending.clear();
                    panel = Panel.KEYS;
                    renderPanel(true);
                    onStatus("Relis, modifie, puis envoie");
                }
            });
            list.addView(card);
        }

        scroll.addView(list);
        box.addView(scroll);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.addView(tool("ABC", theme.special, theme.textColor(), new Runnable() {
            @Override
            public void run() {
                panel = Panel.KEYS;
                renderPanel(true);
            }
        }));
        footer.addView(tool("\u2715 Jeter", theme.special, theme.mutedColor(), new Runnable() {
            @Override
            public void run() {
                pending.clear();
                panel = Panel.KEYS;
                renderPanel(true);
            }
        }));
        footer.addView(tool("\u21BB Relancer", theme.special, theme.textColor(), new Runnable() {
            @Override
            public void run() {
                requestCapture("");
            }
        }));
        box.addView(footer);
        return box;
    }

    private View buildEmojiPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < EmojiData.CATEGORY_LABELS.length; i++) {
            final int index = i;
            boolean active = (i == emojiCategory);
            tabs.addView(tool(EmojiData.CATEGORY_LABELS[i],
                    active ? theme.accent : theme.special, theme.textColor(),
                    new Runnable() {
                        @Override
                        public void run() {
                            emojiCategory = index;
                            renderPanel(false);
                        }
                    }));
        }
        tabScroll.addView(tabs);
        box.addView(tabScroll);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(196)));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(8);
        String[] set = EmojiData.EMOJI[Math.min(emojiCategory, EmojiData.EMOJI.length - 1)];
        for (int i = 0; i < set.length; i++) {
            final String e = set[i];
            TextView tv = new TextView(this);
            tv.setText(e);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(6), dp(9), dp(6), dp(9));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.columnSpec = GridLayout.spec(i % 8, 1f);
            tv.setLayoutParams(lp);
            tapFeedback(tv, theme.bg, new Runnable() {
                @Override
                public void run() {
                    commit(e);
                }
            });
            grid.addView(tv);
        }
        scroll.addView(grid);
        box.addView(scroll);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.addView(tool("ABC", theme.special, theme.textColor(), new Runnable() {
            @Override
            public void run() {
                panel = Panel.KEYS;
                renderPanel(true);
            }
        }));
        footer.addView(tool(Keys.BACKSPACE, theme.special, theme.textColor(), new Runnable() {
            @Override
            public void run() {
                deleteOne();
            }
        }));
        box.addView(footer);
        return box;
    }

    private View buildClipPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        captureCurrentClip();

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(196)));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        if (clips.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Rien dans le presse-papiers pour l'instant.");
            empty.setTextColor(theme.mutedColor());
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            empty.setPadding(dp(12), dp(16), dp(12), dp(16));
            list.addView(empty);
        } else {
            for (int i = 0; i < clips.size(); i++) {
                final String clip = clips.get(i);
                Button b = new Button(this);
                b.setText(clip.length() > 120 ? clip.substring(0, 120) + "…" : clip);
                b.setAllCaps(false);
                b.setTextColor(theme.textColor());
                b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                b.setBackground(pill(theme.chip));
                b.setElevation(dp(2));
                b.setPadding(dp(12), dp(10), dp(12), dp(10));
                b.setMaxLines(3);
                b.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(5);
                b.setLayoutParams(lp);
                tapFeedback(b, theme.chip, new Runnable() {
                    @Override
                    public void run() {
                        commit(clip);
                    }
                });
                list.addView(b);
            }
        }
        scroll.addView(list);
        box.addView(scroll);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.addView(tool("ABC", theme.special, theme.textColor(), new Runnable() {
            @Override
            public void run() {
                panel = Panel.KEYS;
                renderPanel(true);
            }
        }));
        footer.addView(tool("Vider", theme.special, theme.mutedColor(), new Runnable() {
            @Override
            public void run() {
                clips.clear();
                renderPanel(false);
            }
        }));
        box.addView(footer);
        return box;
    }

    /** Un clavier a le droit de lire le presse-papiers tant qu'il a le focus. */
    private void captureCurrentClip() {
        try {
            ClipboardManager cm =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) {
                return;
            }
            ClipData data = cm.getPrimaryClip();
            if (data == null || data.getItemCount() == 0) {
                return;
            }
            CharSequence cs = data.getItemAt(0).coerceToText(this);
            if (cs == null) {
                return;
            }
            String text = cs.toString().trim();
            if (text.isEmpty() || clips.contains(text)) {
                return;
            }
            clips.addFirst(text);
            while (clips.size() > 20) {
                clips.removeLast();
            }
        } catch (Exception ignored) {
            // presse-papiers inaccessible : le panneau reste vide
        }
    }


    // -------------------------------------------------------------- actions

    private String threadKey() {
        EditorInfo info = getCurrentInputEditorInfo();
        return (info == null || info.packageName == null) ? "default" : info.packageName;
    }

    private String fieldText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return "";
        }
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        return (et == null || et.text == null) ? "" : et.text.toString().trim();
    }

    private void commit(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, text.length());
        }
    }

    private void deleteOne() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        if (TextUtils.isEmpty(ic.getSelectedText(0))) {
            ic.deleteSurroundingText(1, 0);
        } else {
            ic.commitText("", 1);
        }
    }

    private void useFieldAsInstruction() {
        String typed = fieldText();
        if (typed.isEmpty()) {
            onError("Tape d'abord ta consigne dans le champ");
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.deleteSurroundingText(typed.length() + 100, typed.length() + 100);
        }
        requestCapture(typed);
    }

    private void requestCapture(String oneShot) {
        pending.clear();

        if (Settings.get(this).apiKey().isEmpty()) {
            onError("Aucune clé API : ouvre les réglages");
            return;
        }

        Intent base = new Intent(this, CaptureService.class)
                .putExtra(CaptureService.EXTRA_ONE_SHOT, oneShot)
                .putExtra(CaptureService.EXTRA_THREAD, threadKey());

        if (CaptureService.isReady()) {
            onStatus(oneShot.isEmpty() ? "Lecture de l'écran..." : "Consigne prise en compte...");
            startForegroundService(base.setAction(CaptureService.ACTION_CAPTURE));
        } else {
            onStatus("Autorisation de capture...");
            CaptureActivity.pendingOneShot = oneShot;
            CaptureActivity.pendingThread = threadKey();
            Intent i = new Intent(this, CaptureActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        }
    }

    private void makeVoice() {
        final Settings s = Settings.get(this);
        if (!s.voiceReady()) {
            onError("Réglages : charge tes voix ElevenLabs et choisis-en une");
            return;
        }
        final String text = fieldText();
        if (text.isEmpty()) {
            onError("Écris ou choisis d'abord le texte à dire");
            return;
        }
        onStatus("Génération du vocal...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File mp3 = new ElevenLabsClient(s.elevenKey(), s.elevenVoice(),
                            s.elevenModel())
                            .synthesize(text, Share.outDir(RpKeyboardService.this));
                    Uri uri = Share.publishAudio(RpKeyboardService.this, mp3);
                    onStatus("Vocal prêt : choisis l'app");
                    Share.send(RpKeyboardService.this, uri, "audio/mpeg", "Envoyer le vocal");
                } catch (Exception e) {
                    onError(msg(e));
                }
            }
        }).start();
    }

    private void makeImage() {
        final Settings s = Settings.get(this);
        if (!s.imageReady()) {
            onError("Clé image à renseigner dans les réglages");
            return;
        }
        final String prompt = fieldText();
        if (prompt.isEmpty()) {
            onError("Décris l'image dans le champ, puis réappuie");
            return;
        }
        onStatus("Génération de l'image...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File png = new ImageClient(s.imageUrl(), s.imageKey(), s.imageModel())
                            .generate(prompt, "1024x1024", Share.outDir(RpKeyboardService.this));
                    Uri uri = Share.publishImage(RpKeyboardService.this, png);
                    onStatus("Image prête : choisis l'app");
                    Share.send(RpKeyboardService.this, uri, "image/png", "Envoyer l'image");
                } catch (Exception e) {
                    onError(msg(e));
                }
            }
        }).start();
    }

    private static String msg(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    // ------------------------------------------------------------ callbacks

    @Override
    public void onStatus(final String message) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (status != null) {
                    status.setTextColor(theme.mutedColor());
                    status.setText(message);
                }
            }
        });
    }

    @Override
    public void onError(final String message) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (status != null) {
                    status.setTextColor(Color.parseColor("#E2777A"));
                    status.setText(message);
                }
            }
        });
    }

    @Override
    public void onSuggestions(final List<String> replies) {
        pending = new ArrayList<String>(replies);
        ui.post(new Runnable() {
            @Override
            public void run() {
                panel = Panel.SUGGEST;
                renderPanel(true);
            }
        });
    }




    private void rememberSent(String text) {
        try {
            Settings s = Settings.get(this);
            String key = threadKey();
            ConversationLog log = s.loadLog(key);
            List<ChatMessage> one = new ArrayList<ChatMessage>();
            one.add(ChatMessage.mine(text));
            log.absorb(one);
            s.saveLog(key, log);
        } catch (Exception ignored) {
            // le journal est un confort, pas une condition de fonctionnement
        }
    }

    // -------------------------------------------------------------- clavier

    private View buildKeysPanel() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        String[][] layer = symbols ? Keys.SYMBOLS : Keys.LETTERS;

        for (int r = 0; r < layer.length; r++) {
            LinearLayout row = newRow();
            for (int c = 0; c < layer[r].length; c++) {
                String label = layer[r][c];
                float weight = Keys.BACKSPACE.equals(label) || Keys.SHIFT.equals(label)
                        ? 1.5f : 1f;
                row.addView(key(label, weight, Keys.isSpecial(label)));
            }
            rows.addView(row);
        }

        LinearLayout bottom = newRow();
        bottom.addView(key(symbols ? Keys.LAYER_BACK : Keys.LAYER, 1.6f, true));
        bottom.addView(key(",", 1f, false));
        bottom.addView(key(Keys.SPACE, 4.4f, false));
        bottom.addView(key(".", 1f, false));
        bottom.addView(key(Keys.ENTER, 1.6f, true));
        rows.addView(bottom);
        return rows;
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(5);
        row.setLayoutParams(lp);
        return row;
    }

    private View key(final String label, float weight, boolean special) {
        final Button b = new Button(this);
        boolean upper = shifted && !symbols && label.length() == 1;
        b.setText(upper ? label.toUpperCase() : label);
        b.setAllCaps(false);
        b.setTextColor(theme.textColor());
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, Keys.SPACE.equals(label) ? 13 : 17);
        b.setPadding(0, dp(13), 0, dp(13));
        b.setElevation(dp(2));

        final String base = (Keys.SHIFT.equals(label) && shifted)
                ? theme.accent : (special ? theme.special : theme.key);
        b.setBackground(pill(base));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        lp.rightMargin = dp(5);
        b.setLayoutParams(lp);

        if (Keys.BACKSPACE.equals(label)) {
            attachBackspace(b, base);
        } else if (Keys.SPACE.equals(label)) {
            attachSpace(b, base);
        } else {
            attachKey(b, label, base);
        }
        return b;
    }

    /** Touche ordinaire : enfoncement, apercu, accents en appui long. */
    private void attachKey(final Button b, final String label, final String base) {
        final String[] variants = Keys.accents(label);
        final Runnable longPress = new Runnable() {
            @Override
            public void run() {
                if (variants.length > 0) {
                    dismissPreview();
                    showAccents(b, variants);
                }
            }
        };

        b.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        press(b);
                        if (!Keys.isSpecial(label)) {
                            showPreview(b, b.getText().toString());
                        }
                        if (variants.length > 0) {
                            b.postDelayed(longPress, LONG_PRESS_MS);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        b.removeCallbacks(longPress);
                        release(b, base);
                        dismissPreview();
                        if (accentPopup == null || !accentPopup.isShowing()) {
                            onKeyPress(label);
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        b.removeCallbacks(longPress);
                        release(b, base);
                        dismissPreview();
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    /** Retour arriere : un appui efface une lettre, un appui long efface en rafale. */
    private void attachBackspace(final Button b, final String base) {
        final Runnable repeat = new Runnable() {
            @Override
            public void run() {
                deleteOne();
                b.postDelayed(this, DELETE_EVERY_MS);
            }
        };
        b.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        press(b);
                        deleteOne();
                        b.postDelayed(repeat, DELETE_FIRST_MS);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        b.removeCallbacks(repeat);
                        release(b, base);
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    /** Barre d'espace : glisser le doigt deplace le curseur, comme sur Gboard. */
    private void attachSpace(final Button b, final String base) {
        final float[] startX = {0f};
        final int[] moved = {0};
        final int step = dp(13);

        b.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        press(b);
                        startX[0] = e.getRawX();
                        moved[0] = 0;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - startX[0];
                        int steps = (int) (dx / step);
                        while (moved[0] < steps) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT);
                            moved[0]++;
                        }
                        while (moved[0] > steps) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT);
                            moved[0]--;
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        release(b, base);
                        if (moved[0] == 0) {
                            commit(" ");
                            tick();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        release(b, base);
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void press(View v) {
        v.setBackground(pill(theme.keyDown));
        if (animate) {
            v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(55).start();
        }
        tick();
    }

    private void release(View v, String base) {
        v.setBackground(pill(base));
        if (animate) {
            v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
        }
    }

    /** Retour tactile commun aux boutons de barre, chips et emoji. */
    private void tapFeedback(final View v, final String base, final Runnable action) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        press(v);
                        return true;
                    case MotionEvent.ACTION_UP:
                        release(v, base);
                        action.run();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        release(v, base);
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    /** Petite vibration seche, si l'utilisateur l'a laissee active. */
    private void tick() {
        if (!haptics) {
            return;
        }
        try {
            Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 29) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
            } else {
                vib.vibrate(12);
            }
        } catch (Exception ignored) {
            // pas de vibreur : tant pis
        }
    }

    private void showPreview(View anchor, String text) {
        dismissPreview();
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(theme.textColor());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(16), dp(8), dp(16), dp(8));
        tv.setBackground(pill(theme.accent));
        tv.setElevation(dp(8));

        preview = new PopupWindow(tv,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        preview.setTouchable(false);
        preview.setOutsideTouchable(true);
        showAbove(preview, anchor, dp(6));
    }

    private void dismissPreview() {
        if (preview != null) {
            try {
                preview.dismiss();
            } catch (Exception ignored) {
                // deja fermee
            }
            preview = null;
        }
    }

    private void showAccents(View anchor, String[] variants) {
        dismissAccents();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setBackground(pill(theme.special));
        box.setElevation(dp(10));
        box.setPadding(dp(6), dp(6), dp(6), dp(6));

        for (int i = 0; i < variants.length; i++) {
            final String v = shifted ? variants[i].toUpperCase() : variants[i];
            Button ab = new Button(this);
            ab.setText(v);
            ab.setAllCaps(false);
            ab.setTextColor(theme.textColor());
            ab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            ab.setBackground(pill(theme.key));
            ab.setPadding(dp(14), dp(10), dp(14), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(4);
            ab.setLayoutParams(lp);
            ab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    commit(v);
                    tick();
                    dismissAccents();
                }
            });
            box.addView(ab);
        }

        accentPopup = new PopupWindow(box,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        accentPopup.setTouchable(true);
        accentPopup.setOutsideTouchable(true);
        accentPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        showAbove(accentPopup, anchor, dp(6));
    }

    private void dismissAccents() {
        if (accentPopup != null) {
            try {
                accentPopup.dismiss();
            } catch (Exception ignored) {
                // deja fermee
            }
            accentPopup = null;
        }
    }

    private void dismissPopups() {
        dismissPreview();
        dismissAccents();
    }

    private void showAbove(PopupWindow window, View anchor, int gap) {
        try {
            int[] pos = new int[2];
            anchor.getLocationInWindow(pos);
            window.showAtLocation(anchor, Gravity.NO_GRAVITY, 0, 0);
            window.update(pos[0], pos[1] - anchor.getHeight() - gap, -1, -1);
        } catch (Exception ignored) {
            // si l'ancrage echoue, on se passe de l'apercu
        }
    }

    private void onKeyPress(String label) {
        if (Keys.SHIFT.equals(label)) {
            shifted = !shifted;
            renderPanel(false);
            return;
        }
        if (Keys.LAYER.equals(label) || Keys.LAYER_BACK.equals(label)) {
            symbols = !symbols;
            renderPanel(false);
            return;
        }
        if (Keys.ENTER.equals(label)) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
            return;
        }
        String out = (shifted && !symbols) ? label.toUpperCase() : label;
        commit(out);
        if (shifted) {
            shifted = false;
            renderPanel(false);
        }
    }

    // ---------------------------------------------------------------- utils

    private GradientDrawable pill(String color) {
        GradientDrawable d = new GradientDrawable();
        try {
            d.setColor(Color.parseColor(color));
        } catch (Exception e) {
            d.setColor(theme.bgColor());
        }
        d.setCornerRadius(dp(11));
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
