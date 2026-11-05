package local.dialer.visual;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** Isolated history preferences and an accessible per-call-type palette. */
public final class CallColorPalette {
    public static final String HISTORY_PREFIX = PreferenceRepository.PREFIX + "history_";
    public static final String ENABLED = HISTORY_PREFIX + "enabled";
    public static final String SHOW_LABELS = HISTORY_PREFIX + "show_labels";
    public static final String PLACEMENT = HISTORY_PREFIX + "placement";
    public static final String FILTER = HISTORY_PREFIX + "filter";
    public static final String ICON_STYLE = HISTORY_PREFIX + "icon_style";
    public static final String ICON_NATIVE = "native";
    public static final String ICON_PHONE = "phone";
    public static final String ICON_BADGE = "badge";
    public static final String COLOR_PREFIX = HISTORY_PREFIX + "color_";
    public static final String ICON = "icon";
    public static final String ICON_AND_TEXT = "icon_text";
    public static final String STRIPE = "stripe";
    private CallColorPalette() { }

    public static CallTypeResolver.Kind[] supported() {
        return new CallTypeResolver.Kind[]{
            CallTypeResolver.Kind.MISSED, CallTypeResolver.Kind.INCOMING,
            CallTypeResolver.Kind.OUTGOING, CallTypeResolver.Kind.SPAM,
            CallTypeResolver.Kind.REJECTED, CallTypeResolver.Kind.BLOCKED
        };
    }
    public static int defaultColor(CallTypeResolver.Kind kind) {
        switch (kind) {
            case MISSED: return 0xffff6b7b;
            case INCOMING: return 0xff55d7a5;
            case OUTGOING: return 0xff65b6ff;
            case SPAM: return 0xffffb454;
            case REJECTED: return 0xffbe9bff;
            case BLOCKED: return 0xffa0a0a0;
            default: return Color.WHITE;
        }
    }
    public static String colorKey(CallTypeResolver.Kind kind) {
        return COLOR_PREFIX + kind.key;
    }
    public static int color(Context context, CallTypeResolver.Kind kind) {
        if (kind == CallTypeResolver.Kind.ORIGINAL) return Color.TRANSPARENT;
        String value = PreferenceRepository.get(context, colorKey(kind), "");
        try { return ThemeEngine.opaque(value); }
        catch (IllegalArgumentException invalid) { return defaultColor(kind); }
    }
    public static boolean enabled(Context context) {
        try { return PreferenceRepository.prefs(context).getBoolean(ENABLED, false); }
        catch (ClassCastException invalid) { return false; }
    }
    public static boolean labels(Context context) {
        try { return PreferenceRepository.prefs(context).getBoolean(SHOW_LABELS, false); }
        catch (ClassCastException invalid) { return false; }
    }
    public static String placement(Context context) {
        String value = PreferenceRepository.get(context, PLACEMENT, ICON_AND_TEXT);
        return validPlacement(value) ? value : ICON_AND_TEXT;
    }
    public static String iconStyle(Context context) {
        String value = PreferenceRepository.get(context, ICON_STYLE, ICON_PHONE);
        return validIconStyle(value) ? value : ICON_PHONE;
    }
    public static boolean validIconStyle(String value) {
        return ICON_NATIVE.equals(value) || ICON_PHONE.equals(value) || ICON_BADGE.equals(value);
    }

    public static boolean validPlacement(String value) {
        return ICON.equals(value) || ICON_AND_TEXT.equals(value) || STRIPE.equals(value);
    }
    public static boolean validColorKey(String key) {
        for (CallTypeResolver.Kind kind : supported()) if (colorKey(kind).equals(key)) return true;
        return false;
    }
    public static boolean validFilter(String key) {
        if ("all".equals(key)) return true;
        for (CallTypeResolver.Kind kind : supported()) if (kind.key.equals(key)) return true;
        return false;
    }
    public static boolean visibleUnderFilter(CallTypeResolver.Kind kind, String filter) {
        return "all".equals(filter) || (kind != null && kind.key.equals(filter));
    }
    public static void setColor(Context context, CallTypeResolver.Kind kind, String hex) {
        if (kind == CallTypeResolver.Kind.ORIGINAL) throw new IllegalArgumentException("Unknown event");
        int checked = ThemeEngine.opaque(hex);
        SharedPreferences.Editor editor = PreferenceRepository.prefs(context).edit();
        editor.putString(colorKey(kind), ThemeEngine.hex(checked)).apply();
    }
    public static void reset(Context context) {
        SharedPreferences.Editor edit = PreferenceRepository.prefs(context).edit();
        edit.remove(ENABLED).remove(SHOW_LABELS).remove(PLACEMENT).remove(FILTER).remove(ICON_STYLE);
        for (CallTypeResolver.Kind kind : supported()) edit.remove(colorKey(kind));
        edit.apply();
    }
}
