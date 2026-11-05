package local.dialer.visual;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Dialpad-specific rendering. Does not read phone calls, contacts or mutate click listeners. */
public final class DialpadStyler {
    private static final int[] KEY_IDS = {
        0x7f0b050f,0x7f0b07cf,0x7f0b078d,0x7f0b03ca,0x7f0b03b7,0x7f0b065f,
        0x7f0b0647,0x7f0b02f3,0x7f0b04e8,0x7f0b0697,0x7f0b08ab,0x7f0b056a
    };
    private static final int NUMBER_ID = 0x7f0b0290;
    private static final int LETTERS_ID = 0x7f0b028f;
    private static final int SECONDARY_LETTERS_ID = 0x7f0b0291;
    private static final int[] CALL_BUTTON_IDS = { 0x7f0b029d, 0x7f0b0293, 0x7f0b0294, 0x7f0b028d };
    private static final Map<View, Snapshot> ORIGINAL =
        Collections.synchronizedMap(new WeakHashMap<View, Snapshot>());
    private static final Map<View, ColorStateList> CALL_ORIGINAL =
        Collections.synchronizedMap(new WeakHashMap<View, ColorStateList>());
    private static final Map<View, String> LAST =
        Collections.synchronizedMap(new WeakHashMap<View, String>());
    private DialpadStyler() { }
    private static final class Snapshot {
        final Drawable background;
        final ColorStateList number;
        final ColorStateList letters;
        final ColorStateList secondary;
        Snapshot(View view) {
            background = view.getBackground();
            number = colors(view, NUMBER_ID);
            letters = colors(view, LETTERS_ID);
            secondary = colors(view, SECONDARY_LETTERS_ID);
        }
        void restore(View view) {
            view.setBackground(background);
            restoreText(view, NUMBER_ID, number);
            restoreText(view, LETTERS_ID, letters);
            restoreText(view, SECONDARY_LETTERS_ID, secondary);
        }
    }
    private static ColorStateList colors(View root, int id) {
        View child = root.findViewById(id);
        return child instanceof TextView ? ((TextView) child).getTextColors() : null;
    }
    private static void restoreText(View root, int id, ColorStateList state) {
        View child = root.findViewById(id);
        if (state != null && child instanceof TextView) ((TextView) child).setTextColor(state);
    }
    private static void setText(View root, int id, int color) {
        View child = root.findViewById(id);
        if (child instanceof TextView) ((TextView) child).setTextColor(color);
    }
    public static void invalidate() { LAST.clear(); }
    public static void apply(View dialpad) {
        if (dialpad == null) return;
        Context context = dialpad.getContext();
        SharedPreferences prefs = PreferenceRepository.prefs(context);
        String profile = PreferenceRepository.get(context, PreferenceRepository.PROFILE, "system");
        String style = PreferenceRepository.get(context, PreferenceRepository.STYLE, "filled");
        boolean night = ThemeEngine.isNight(context);
        boolean enabled = !"system".equals(profile) &&
            (!PreferenceRepository.darkOnly(context) || night);
        StringBuilder key = new StringBuilder(180).append(enabled).append('|')
            .append(profile).append('|').append(style).append('|').append(night)
            .append('|').append(PreferenceRepository.get(context, PreferenceRepository.ACCENT, "system"));
        for (String symbol : PreferenceRepository.KEYS) {
            key.append('|').append(PreferenceRepository.get(context, PreferenceRepository.PREFIX + "key_" + symbol, ""));
        }
        String signature = key.toString();
        if (signature.equals(LAST.get(dialpad))) return;
        for (int i = 0; i < KEY_IDS.length; i++) {
            View view = dialpad.findViewById(KEY_IDS[i]);
            if (view == null) continue;
            Snapshot snapshot = ORIGINAL.get(view);
            if (snapshot == null) {
                snapshot = new Snapshot(view);
                ORIGINAL.put(view, snapshot);
            }
            if (!enabled) { snapshot.restore(view); continue; }
            int background = PreferenceRepository.keyColor(prefs, profile, i);
            if ("numbers".equals(style)) {
                view.setBackground(snapshot.background);
                int ink = "amoled".equals(profile) ? (night ? Color.WHITE : Color.BLACK)
                    : ThemeEngine.coloredText(background, night);
                setText(view, NUMBER_ID, ink);
                setText(view, LETTERS_ID, ink);
                setText(view, SECONDARY_LETTERS_ID, ink);
            } else {
                view.setBackground(ThemeEngine.background(context, background, style, night));
                int ink = "outline".equals(style)
                    ? ("amoled".equals(profile) ? (night ? Color.WHITE : Color.BLACK)
                        : ThemeEngine.coloredText(background, night))
                    : ThemeEngine.contrastText(background);
                setText(view, NUMBER_ID, ink);
                setText(view, LETTERS_ID, ink);
                setText(view, SECONDARY_LETTERS_ID, ink);
            }
        }
        applyDialCallAccent(dialpad, context, enabled || !PreferenceRepository.darkOnly(context) || night);
        LAST.put(dialpad, signature);
    }
    private static void applyDialCallAccent(View dialpad, Context context, boolean allowed) {
        String accent = PreferenceRepository.get(context, PreferenceRepository.ACCENT, "system");
        View root = dialpad.getRootView();
        if (root == null) return;
        for (int id : CALL_BUTTON_IDS) {
            View view = root.findViewById(id);
            if (view == null) continue;
            if (!CALL_ORIGINAL.containsKey(view)) CALL_ORIGINAL.put(view, view.getBackgroundTintList());
            if (!allowed || "system".equals(accent)) view.setBackgroundTintList(CALL_ORIGINAL.get(view));
            else view.setBackgroundTintList(ColorStateList.valueOf(ThemeEngine.accentColor(accent)));
        }
    }
}
