package local.dialer.visual;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.Map;

/** Versioned, app-private theme settings. Never exports Android permissions, contacts or call data. */
public final class PreferenceRepository {
    public static final String PREFIX = "local_dialer_visual_";
    public static final String PROFILE = PREFIX + "profile";
    public static final String STYLE = PREFIX + "style";
    public static final String ACCENT = PREFIX + "accent";
    public static final String DARK_ONLY = PREFIX + "dark_only";
    public static final String IN_CALL_ENABLED = PREFIX + "in_call_enabled";
    public static final String CALL_STYLE = PREFIX + "call_style";
    public static final String CALL_ACCENT = PREFIX + "call_accent";
    public static final String COVER_URI = PREFIX + "cover_uri";
    public static final String VIDEO_URI = PREFIX + "video_uri";
    public static final String RINGTONE_URI = PREFIX + "ringtone_uri";
    public static final String CALL_BACKGROUND = PREFIX + "call_background";
    public static final String[] KEYS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
    private PreferenceRepository() { }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }
    public static String get(Context context, String key, String fallback) {
        try { return prefs(context).getString(key, fallback); }
        catch (ClassCastException invalid) { return fallback; }
    }
    public static boolean inCallEnabled(Context context) {
        try { return prefs(context).getBoolean(IN_CALL_ENABLED, false); }
        catch (ClassCastException invalid) { return false; }
    }
    public static boolean darkOnly(Context context) {
        try { return prefs(context).getBoolean(DARK_ONLY, true); }
        catch (ClassCastException invalid) { return true; }
    }
    public static void set(Context context, String key, String value) {
        if (!validString(key, value)) throw new IllegalArgumentException("Invalid visual setting: " + key);
        prefs(context).edit().putString(key, value).apply();
    }
    public static int keyColor(SharedPreferences preferences, String profile, int index) {
        int original = ThemeEngine.color(profile, index);
        if (!"custom".equals(profile)) return original;
        try {
            String saved = preferences.getString(PREFIX + "key_" + KEYS[index], null);
            return saved == null ? original : ThemeEngine.opaque(saved);
        } catch (RuntimeException invalid) { return original; }
    }
    public static void clear(Context context) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (String key : prefs(context).getAll().keySet()) {
            if (key.startsWith(PREFIX)) editor.remove(key);
        }
        editor.apply();
    }
    public static boolean validString(String key, String value) {
        if (key == null || value == null) return false;
        if (PROFILE.equals(key)) return ThemeEngine.isProfile(value);
        if (STYLE.equals(key)) return "filled".equals(value) || "outline".equals(value) || "numbers".equals(value);
        if (ACCENT.equals(key) || CALL_ACCENT.equals(key)) {
            return value.equals("system") || value.equals("green") || value.equals("peach") ||
                value.equals("cyan") || value.equals("violet") || value.equals("pink");
        }
        if (CALL_STYLE.equals(key)) return value.equals("original") || value.equals("filled") || value.equals("outline");
        if (CallColorPalette.PLACEMENT.equals(key)) return CallColorPalette.validPlacement(value);
        if (CallColorPalette.FILTER.equals(key)) return CallColorPalette.validFilter(value);
        if (CallColorPalette.validColorKey(key)) {
            try { ThemeEngine.opaque(value); return true; }
            catch (IllegalArgumentException invalid) { return false; }
        }
        if (CALL_BACKGROUND.equals(key)) return value.equals("original") || value.equals("cover") || value.equals("video");
        if (COVER_URI.equals(key) || VIDEO_URI.equals(key) || RINGTONE_URI.equals(key)) {
            if (value.isEmpty()) return true;
            Uri parsed = Uri.parse(value);
            return "content".equals(parsed.getScheme()) && value.length() < 4096;
        }
        if (key.startsWith(PREFIX + "key_")) {
            String suffix = key.substring((PREFIX + "key_").length());
            boolean known = false;
            for (String symbol : KEYS) if (symbol.equals(suffix)) known = true;
            if (!known) return false;
            try { ThemeEngine.opaque(value); return true; }
            catch (IllegalArgumentException invalid) { return false; }
        }
        return false;
    }
    public static String exportJson(Context context) throws JSONException {
        JSONObject settings = new JSONObject();
        for (Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (!key.startsWith(PREFIX)) continue;
            // The media URIs belong to this specific Android installation.
            // Never export them as portable configuration values.
            if (COVER_URI.equals(key) || VIDEO_URI.equals(key) || RINGTONE_URI.equals(key)) continue;
            if ((DARK_ONLY.equals(key) || IN_CALL_ENABLED.equals(key) ||
                CallColorPalette.ENABLED.equals(key) || CallColorPalette.SHOW_LABELS.equals(key)) &&
                val instanceof Boolean) settings.put(key, val);
            if (val instanceof String && validString(key, (String) val)) settings.put(key, val);
        }
        return new JSONObject().put("schema", 1).put("settings", settings).toString(2);
    }
    public static void importJson(Context context, String input) throws JSONException {
        if (input == null || input.length() > 32768) throw new JSONException("Config too large");
        JSONObject root = new JSONObject(input);
        if (root.getInt("schema") != 1) throw new JSONException("Unsupported schema");
        JSONObject settings = root.getJSONObject("settings");
        SharedPreferences.Editor editor = prefs(context).edit();
        Iterator<String> iterator = settings.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            Object value = settings.get(key);
            if ((DARK_ONLY.equals(key) || IN_CALL_ENABLED.equals(key) ||
                CallColorPalette.ENABLED.equals(key) || CallColorPalette.SHOW_LABELS.equals(key)) &&
                value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof String && validString(key, (String) value) &&
                !COVER_URI.equals(key) && !VIDEO_URI.equals(key) && !RINGTONE_URI.equals(key)) {
                editor.putString(key, (String) value);
            } else throw new JSONException("Invalid setting: " + key);
        }
        editor.apply();
    }
}
