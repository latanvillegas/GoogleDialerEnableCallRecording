package local.dialer.visual;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.TextView;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Runtime part of Dialer Visual Customizer for the inspected Google Phone 241 dialpad.
 * All resource IDs below were resolved against that exact APK's public.xml.
 * No call state, phone permissions, contacts, or dial events are read or changed.
 */
public final class VisualCustomizer {
    static final String PREFIX = "local_dialer_visual_";
    static final String PROFILE = PREFIX + "profile";
    static final String STYLE = PREFIX + "style";
    static final String ACCENT = PREFIX + "accent";
    static final String DARK_ONLY = PREFIX + "dark_only";

    static final String[] KEYS = {
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"
    };
    private static final int[] KEY_IDS = {
        0x7f0b050f, 0x7f0b07cf, 0x7f0b078d,
        0x7f0b03ca, 0x7f0b03b7, 0x7f0b065f,
        0x7f0b0647, 0x7f0b02f3, 0x7f0b04e8,
        0x7f0b0697, 0x7f0b08ab, 0x7f0b056a
    };
    private static final int NUMBER_ID = 0x7f0b0290;
    private static final int LETTERS_ID = 0x7f0b028f;
    private static final int SECONDARY_LETTERS_ID = 0x7f0b0291;
    private static final int[] CALL_BUTTON_IDS = {
        0x7f0b029d,  // dialpad_voice_call_button
        0x7f0b0293,  // dialpad_rtt_call_button
        0x7f0b0294,  // dialpad_rtt_visible_voice_call_button
        0x7f0b028d   // dialpad_extended_voice_call_button
    };

    private static final int PEACH = 0xffffb385;
    private static final int GOLD = 0xffffe18b;
    private static final int LIME = 0xffb9ee83;
    private static final int MINT = 0xff77e5c1;
    private static final int SLATE = 0xff8995ad;
    private static final int NEON_PINK = 0xffff71b7;
    private static final int NEON_CYAN = 0xff57e9f0;
    private static final int NEON_GREEN = 0xffa8f36d;

    private static final Map<View, Snapshot> ORIGINAL =
        Collections.synchronizedMap(new WeakHashMap<View, Snapshot>());
    private static final Map<View, ColorStateList> CALL_ORIGINAL =
        Collections.synchronizedMap(new WeakHashMap<View, ColorStateList>());
    private static final Map<View, String> LAST =
        Collections.synchronizedMap(new WeakHashMap<View, String>());

    private VisualCustomizer() { }

    public static SharedPreferences preferences(Context context) {
        // AndroidX PreferenceManager's default SharedPreferences filename.
        return context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }

    public static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static int defaultColor(String profile, int i) {
        switch (profile) {
            case "amoled": return Color.BLACK;
            case "neon":
                if (i == 9 || i == 11) return SLATE;
                if (i == 10) return NEON_CYAN;
                return i % 3 == 0 ? NEON_PINK : (i % 3 == 1 ? NEON_CYAN : NEON_GREEN);
            case "custom":
            case "pastel":
            default:
                if (i == 9 || i == 11) return SLATE;
                if (i == 10) return MINT;
                return i % 3 == 0 ? PEACH : (i % 3 == 1 ? GOLD : LIME);
        }
    }

    static int colorFor(SharedPreferences preferences, String profile, int i) {
        int fallback = defaultColor(profile, i);
        if (!"custom".equals(profile)) return fallback;
        try {
            String hex = preferences.getString(PREFIX + "key_" + KEYS[i], null);
            return hex == null ? fallback : parseOpaqueColor(hex);
        } catch (RuntimeException invalid) {
            return fallback;
        }
    }

    static int parseOpaqueColor(String value) {
        if (value == null || !value.matches("#[A-Fa-f0-9]{6}")) {
            throw new IllegalArgumentException("Expected #RRGGBB");
        }
        return Color.parseColor(value) | 0xff000000;
    }

    static int contrastText(int color) {
        return Color.luminance(color) > 0.179f ? Color.BLACK : Color.WHITE;
    }

    static int readableForeground(int color, boolean night) {
        // Palette lettering on a plain background: use the hue in dark mode.
        // On a white light-theme background, darken the hue to keep text legible.
        if (night) return color;
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(hsv[1], 0.55f);
        hsv[2] = Math.min(hsv[2], 0.42f);
        return Color.HSVToColor(hsv);
    }

    static Drawable makeBackground(Context context, int color, String style, boolean night) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 19));
        if ("outline".equals(style)) {
            drawable.setColor(Color.TRANSPARENT);
            drawable.setStroke(dp(context, 2), color == Color.BLACK
                ? (night ? Color.WHITE : Color.BLACK) : readableForeground(color, night));
        } else {
            drawable.setColor(color);
        }
        // RippleDrawable keeps the press feedback in all three visual modes.
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.RECTANGLE);
        mask.setCornerRadius(dp(context, 19));
        mask.setColor(Color.WHITE);
        int ripple = Color.luminance(color) > 0.179f ? 0x33000000 : 0x44ffffff;
        return new RippleDrawable(ColorStateList.valueOf(ripple), drawable, mask);
    }

    private static class Snapshot {
        final Drawable background;
        final ColorStateList numberColor;
        final ColorStateList lettersColor;
        final ColorStateList secondLettersColor;
        Snapshot(View key) {
            background = key.getBackground();
            numberColor = capture(key, NUMBER_ID);
            lettersColor = capture(key, LETTERS_ID);
            secondLettersColor = capture(key, SECONDARY_LETTERS_ID);
        }
        private static ColorStateList capture(View key, int id) {
            View child = key.findViewById(id);
            return child instanceof TextView ? ((TextView) child).getTextColors() : null;
        }
        void restore(View key) {
            key.setBackground(background);
            restoreText(key, NUMBER_ID, numberColor);
            restoreText(key, LETTERS_ID, lettersColor);
            restoreText(key, SECONDARY_LETTERS_ID, secondLettersColor);
        }
        private static void restoreText(View key, int id, ColorStateList colors) {
            View child = key.findViewById(id);
            if (colors != null && child instanceof TextView) {
                ((TextView) child).setTextColor(colors);
            }
        }
    }

    private static void setText(View key, int id, int color) {
        View child = key.findViewById(id);
        if (child instanceof TextView) ((TextView) child).setTextColor(color);
    }

    public static void apply(View dialpad) {
        if (dialpad == null) return;
        Context context = dialpad.getContext();
        SharedPreferences preferences = preferences(context);
        String profile = preferences.getString(PROFILE, "system");
        String style = preferences.getString(STYLE, "filled");
        boolean night = isNight(context);
        boolean enabled = !"system".equals(profile) &&
            (!preferences.getBoolean(DARK_ONLY, true) || night);
        StringBuilder signature = new StringBuilder(100)
            .append(enabled).append('|').append(profile).append('|').append(style).append('|')
            .append(night).append('|').append(preferences.getString(ACCENT, "system"));
        for (String key : KEYS) signature.append('|')
            .append(preferences.getString(PREFIX + "key_" + key, ""));
        String newSignature = signature.toString();
        String last = LAST.get(dialpad);
        if (newSignature.equals(last)) return;

        for (int i = 0; i < KEY_IDS.length; i++) {
            View key = dialpad.findViewById(KEY_IDS[i]);
            if (key == null) continue;
            Snapshot original = ORIGINAL.get(key);
            if (original == null) {
                original = new Snapshot(key);
                ORIGINAL.put(key, original);
            }
            if (!enabled) {
                original.restore(key);
                continue;
            }
            int color = colorFor(preferences, profile, i);
            if ("numbers".equals(style)) {
                key.setBackground(original.background);
                int fontColor = "amoled".equals(profile) ? (night ? Color.WHITE : Color.BLACK)
                    : readableForeground(color, night);
                setText(key, NUMBER_ID, fontColor);
                setText(key, LETTERS_ID, fontColor);
                setText(key, SECONDARY_LETTERS_ID, fontColor);
            } else {
                key.setBackground(makeBackground(context, color, style, night));
                int textColor = "outline".equals(style)
                    ? ("amoled".equals(profile) ? (night ? Color.WHITE : Color.BLACK)
                        : readableForeground(color, night))
                    : contrastText(color);
                setText(key, NUMBER_ID, textColor);
                setText(key, LETTERS_ID, textColor);
                setText(key, SECONDARY_LETTERS_ID, textColor);
            }
        }
        applyCallAccent(dialpad, preferences,
            !preferences.getBoolean(DARK_ONLY, true) || night);
        LAST.put(dialpad, newSignature);
    }

    private static void applyCallAccent(View dialpad, SharedPreferences preferences, boolean enabled) {
        String accent = preferences.getString(ACCENT, "system");
        View root = dialpad.getRootView();
        if (root == null) return;
        for (int buttonId : CALL_BUTTON_IDS) {
            View button = root.findViewById(buttonId);
            if (button == null) continue;
            if (!CALL_ORIGINAL.containsKey(button)) {
                CALL_ORIGINAL.put(button, button.getBackgroundTintList());
            }
            if (!enabled || "system".equals(accent)) {
                button.setBackgroundTintList(CALL_ORIGINAL.get(button));
            } else {
                button.setBackgroundTintList(ColorStateList.valueOf(accentColor(accent)));
            }
        }
    }

    static int accentColor(String accent) {
        switch (accent) {
            case "peach": return PEACH;
            case "cyan": return NEON_CYAN;
            case "violet": return 0xffb8a3ff;
            case "pink": return NEON_PINK;
            case "green": return 0xff58b877;
            default: return 0xff58b877;
        }
    }

    public static void reset(Context context) {
        SharedPreferences.Editor editor = preferences(context).edit();
        editor.remove(PROFILE).remove(STYLE).remove(ACCENT).remove(DARK_ONLY);
        for (String key : KEYS) editor.remove(PREFIX + "key_" + key);
        editor.apply();
        LAST.clear();
    }

    static String hex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0x00ffffff);
    }
}
