package local.dialer.visual;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Safe call-control styling boundary. Not registered on InCallService until the
 * in-call lifecycle and exact DEX owner are verified against Google Phone 241.
 * Operates on background/tint only; never replaces touch listeners or call actions.
 */
public final class CallScreenStyler {
    private static final String[] CONTROL_NAMES = {
        "btn_mute", "btn_audio_route", "btn_dialpad", "btn_end_call"
    };
    private static final Map<View, Snapshot> ORIGINAL =
        Collections.synchronizedMap(new WeakHashMap<View, Snapshot>());
    private CallScreenStyler() { }
    private static final class Snapshot {
        final Drawable background;
        final ColorStateList tint;
        Snapshot(View view) { background = view.getBackground(); tint = view.getBackgroundTintList(); }
        void restore(View view) { view.setBackground(background); view.setBackgroundTintList(tint); }
    }
    /** Restore native drawables and tints when the call UI becomes inactive. */
    public static void restoreVerifiedControls(View root) {
        if (root == null) return;
        Context context = root.getContext();
        for (String name : CONTROL_NAMES) {
            int id = context.getResources().getIdentifier(name, "id", context.getPackageName());
            View button = id == 0 ? null : root.findViewById(id);
            if (button == null) continue;
            Snapshot snapshot = ORIGINAL.remove(button);
            if (snapshot != null) snapshot.restore(button);
        }
    }

    public static void styleVerifiedControls(View root) {
        if (root == null) return;
        Context context = root.getContext();
        boolean enabled = !PreferenceRepository.darkOnly(context) || ThemeEngine.isNight(context);
        String style = PreferenceRepository.get(context, PreferenceRepository.CALL_STYLE, "original");
        String accent = PreferenceRepository.get(context, PreferenceRepository.CALL_ACCENT, "system");
        for (String name : CONTROL_NAMES) {
            int id = context.getResources().getIdentifier(name, "id", context.getPackageName());
            View button = id == 0 ? null : root.findViewById(id);
            if (button == null) continue;
            Snapshot original = ORIGINAL.get(button);
            if (original == null) { original = new Snapshot(button); ORIGINAL.put(button, original); }
            if (!enabled || "original".equals(style) || "system".equals(accent)) {
                original.restore(button);
                continue;
            }
            if ("btn_end_call".equals(name)) continue; // Destructive control retains native red.
            int color = ThemeEngine.accentColor(accent);
            if ("outline".equals(style)) {
                button.setBackgroundTintList(null);
                button.setBackground(ThemeEngine.background(context, color, "outline", ThemeEngine.isNight(context)));
            } else {
                button.setBackground(original.background);
                button.setBackgroundTintList(ColorStateList.valueOf(color));
            }
        }
    }
}
