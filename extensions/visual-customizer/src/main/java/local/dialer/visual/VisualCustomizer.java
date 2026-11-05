package local.dialer.visual;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;

/** Compatibility facade for the already validated Google Phone 241 DEX hook. */
public final class VisualCustomizer {
    static final String PREFIX = PreferenceRepository.PREFIX;
    static final String PROFILE = PreferenceRepository.PROFILE;
    static final String STYLE = PreferenceRepository.STYLE;
    static final String ACCENT = PreferenceRepository.ACCENT;
    static final String DARK_ONLY = PreferenceRepository.DARK_ONLY;
    static final String[] KEYS = PreferenceRepository.KEYS;
    private VisualCustomizer() { }
    public static void apply(android.view.View root) { DialpadStyler.apply(root); }
    public static SharedPreferences preferences(Context context) { return PreferenceRepository.prefs(context); }
    public static boolean isNight(Context context) { return ThemeEngine.isNight(context); }
    public static int dp(Context context, float value) { return ThemeEngine.dp(context, value); }
    public static int defaultColor(String profile, int i) { return ThemeEngine.color(profile, i); }
    static int colorFor(SharedPreferences preferences, String profile, int i) {
        return PreferenceRepository.keyColor(preferences, profile, i);
    }
    static int parseOpaqueColor(String value) { return ThemeEngine.opaque(value); }
    static int contrastText(int background) { return ThemeEngine.contrastText(background); }
    static int readableForeground(int color, boolean night) { return ThemeEngine.coloredText(color, night); }
    static Drawable makeBackground(Context c, int color, String style, boolean night) {
        return ThemeEngine.background(c, color, style, night);
    }
    static int accentColor(String color) { return ThemeEngine.accentColor(color); }
    static String hex(int color) { return ThemeEngine.hex(color); }
    public static void reset(Context context) {
        PreferenceRepository.clear(context);
        DialpadStyler.invalidate();
    }
}
