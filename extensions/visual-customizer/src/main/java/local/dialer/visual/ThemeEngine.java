package local.dialer.visual;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.content.res.Configuration;

/** Immutable, side-effect-free palette and contrast rules for visual surfaces. */
public final class ThemeEngine {
    public static final String[] PROFILES = {
        "system", "amoled", "pastel", "neon", "ocean", "sunset", "emerald", "monochrome", "custom"
    };
    public static final String[] PROFILE_LABELS = {
        "Colores originales", "AMOLED clásico", "Pastel Rainbow", "Neon AMOLED",
        "Ocean AMOLED", "Sunset Gradient", "Emerald Glass", "Monochrome Pro", "Personalizado"
    };
    // Indexing follows 1,2,3 / 4,5,6 / 7,8,9 / *,0,#.
    private static final int[][] PALETTES = {
        {0xff000000,0xff000000,0xff000000,0xff000000,0xff000000,0xff000000,
         0xff000000,0xff000000,0xff000000,0xff000000,0xff000000,0xff000000},
        {0xff000000,0xff000000,0xff000000,0xff000000,0xff000000,0xff000000,
         0xff000000,0xff000000,0xff000000,0xff000000,0xff000000,0xff000000},
        {0xffffb385,0xffffe18b,0xffb9ee83,0xffffb385,0xffffe18b,0xffb9ee83,
         0xffffb385,0xffffe18b,0xffb9ee83,0xff8995ad,0xff77e5c1,0xff8995ad},
        {0xffff71b7,0xff57e9f0,0xffa8f36d,0xffff71b7,0xff57e9f0,0xffa8f36d,
         0xffff71b7,0xff57e9f0,0xffa8f36d,0xff8995ad,0xff57e9f0,0xff8995ad},
        {0xff075e80,0xff18a3bd,0xff46c6ce,0xff075e80,0xff18a3bd,0xff46c6ce,
         0xff075e80,0xff18a3bd,0xff46c6ce,0xff254b60,0xffa5efe6,0xff254b60},
        {0xffe56b75,0xfffa9971,0xfff8bd75,0xffe56b75,0xfffa9971,0xfff8bd75,
         0xffe56b75,0xfffa9971,0xfff8bd75,0xff8f3180,0xffffe19c,0xff8f3180},
        {0xff22967a,0xff39b892,0xff6ad3ac,0xff22967a,0xff39b892,0xff6ad3ac,
         0xff22967a,0xff39b892,0xff6ad3ac,0xff176a58,0xffa7eac8,0xff176a58},
        {0xff505050,0xff787878,0xffa5a5a5,0xff505050,0xff787878,0xffa5a5a5,
         0xff505050,0xff787878,0xffa5a5a5,0xff303030,0xffececec,0xff303030}
    };
    private ThemeEngine() { }
    public static boolean isProfile(String value) {
        for (String profile : PROFILES) if (profile.equals(value)) return true;
        return false;
    }
    public static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
    }
    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
    public static int color(String profile, int index) {
        if (index < 0 || index >= 12) throw new IllegalArgumentException("Key index");
        int row = 2;
        for (int i = 0; i < PROFILES.length - 1; i++) {
            if (PROFILES[i].equals(profile)) { row = i; break; }
        }
        if (row == 8) row = 2;
        return PALETTES[row][index];
    }
    public static int opaque(String value) {
        if (value == null || !value.matches("#[a-fA-F0-9]{6}")) {
            throw new IllegalArgumentException("Expected #RRGGBB");
        }
        return Color.parseColor(value) | 0xff000000;
    }
    public static String hex(int color) {
        return String.format(java.util.Locale.ROOT, "#%06X", color & 0x00ffffff);
    }
    public static int contrastText(int bg) {
        return Color.luminance(bg) > 0.179f ? Color.BLACK : Color.WHITE;
    }
    public static int coloredText(int base, boolean night) {
        if (night) return base;
        float[] hsv = new float[3];
        Color.colorToHSV(base, hsv);
        hsv[1] = Math.max(hsv[1], 0.55f);
        hsv[2] = Math.min(hsv[2], 0.42f);
        return Color.HSVToColor(hsv);
    }
    public static Drawable background(Context context, int color, String style, boolean night) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(context, 19));
        if ("outline".equals(style)) {
            shape.setColor(Color.TRANSPARENT);
            shape.setStroke(dp(context, 2), color == Color.BLACK
                ? (night ? Color.WHITE : Color.BLACK) : coloredText(color, night));
        } else {
            shape.setColor(color);
        }
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.RECTANGLE);
        mask.setCornerRadius(dp(context, 19));
        mask.setColor(Color.WHITE);
        int ripple = Color.luminance(color) > 0.179f ? 0x33000000 : 0x44ffffff;
        return new RippleDrawable(ColorStateList.valueOf(ripple), shape, mask);
    }
    public static int accentColor(String accent) {
        switch (accent) {
            case "peach": return 0xffffb385;
            case "cyan": return 0xff57e9f0;
            case "violet": return 0xffb8a3ff;
            case "pink": return 0xffff71b7;
            case "green": return 0xff58b877;
            default: return 0xff58b877;
        }
    }
}
