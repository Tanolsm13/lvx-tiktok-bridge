package com.lvx.rpkeyboard.ime;

import android.graphics.Color;

/** Palettes du clavier. Sobre par defaut, relief porte par les ombres. */
public enum Theme {

    NUIT("Nuit", "#14121A", "#322C45", "#473E63", "#221E2C",
            "#6C4BD6", "#2C2740", "#FFFFFF", "#9A93AE"),

    ENCRE("Encre", "#0E1116", "#232A33", "#333D4A", "#181D24",
            "#3D7DF2", "#1F2730", "#FFFFFF", "#8C97A6"),

    BRAISE("Braise", "#17100F", "#3A2622", "#52352F", "#241715",
            "#E0632C", "#301F1C", "#FFFFFF", "#AD9188"),

    FORET("Foret", "#0F1613", "#1F302A", "#2C443A", "#16211D",
            "#3FA47A", "#1B2923", "#FFFFFF", "#86A096"),

    PRUNE("Prune", "#17101A", "#342641", "#48355A", "#20172A",
            "#A855C9", "#2A1F35", "#FFFFFF", "#A294B0"),

    CLAIR("Clair", "#ECEAF2", "#FFFFFF", "#DCD7EA", "#DFDBE9",
            "#5B3FD0", "#F4F2F9", "#1B1725", "#6B6480");

    public final String label;
    public final String bg;
    public final String key;
    public final String keyDown;
    public final String special;
    public final String accent;
    public final String chip;
    public final String text;
    public final String muted;

    Theme(String label, String bg, String key, String keyDown, String special,
          String accent, String chip, String text, String muted) {
        this.label = label;
        this.bg = bg;
        this.key = key;
        this.keyDown = keyDown;
        this.special = special;
        this.accent = accent;
        this.chip = chip;
        this.text = text;
        this.muted = muted;
    }

    public int bgColor() {
        return parse(bg, 0xFF14121A);
    }

    public int textColor() {
        return parse(text, Color.WHITE);
    }

    public int mutedColor() {
        return parse(muted, 0xFF9A93AE);
    }

    public int accentColor() {
        return parse(accent, 0xFF6C4BD6);
    }

    /** Le clair demande du texte sombre sur les touches ; les autres du blanc. */
    public boolean isLight() {
        return this == CLAIR;
    }

    public static Theme from(String name) {
        if (name != null) {
            for (Theme t : values()) {
                if (t.name().equals(name)) {
                    return t;
                }
            }
        }
        return NUIT;
    }

    private static int parse(String hex, int fallback) {
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return fallback;
        }
    }
}
