package local.dialer.visual;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Visual-only binder. The verified native adapter must call style AFTER its
 * original row bind with a typed event. No unsafe scraping of visible text.
 * Not activated until the exact Google Phone 241 adapter is audited.
 */
public final class CallHistoryRenderer {
    private static final Map<View, Snapshot> ORIGINAL =
        Collections.synchronizedMap(new WeakHashMap<View, Snapshot>());

    private static final class StripeListener implements View.OnLayoutChangeListener {
        private final ColorDrawable stripe;
        private final int width;
        StripeListener(ColorDrawable stripe, int width) {
            this.stripe = stripe;
            this.width = width;
        }
        @Override public void onLayoutChange(View view, int l, int t, int r, int b,
                                                int oldL, int oldT, int oldR, int oldB) {
            stripe.setBounds(0, 0, width, view.getHeight());
        }
    }
    private static final class Snapshot {
        WeakReference<ImageView> image;
        ColorStateList imageTint;
        WeakReference<TextView> secondary;
        ColorStateList secondaryColor;
        ColorDrawable stripe;
        StripeListener listener;
    }
    private CallHistoryRenderer() { }

    /** Restore intrinsic native tint and text color on RecyclerView reuse. */
    public static void restore(View row) {
        if (row == null) return;
        Snapshot prior = ORIGINAL.remove(row);
        if (prior == null) return;
        ImageView image = prior.image == null ? null : prior.image.get();
        if (image != null) image.setImageTintList(prior.imageTint);
        TextView secondary = prior.secondary == null ? null : prior.secondary.get();
        if (secondary != null) secondary.setTextColor(prior.secondaryColor);
        if (prior.stripe != null) {
            row.getOverlay().remove(prior.stripe);
            row.removeOnLayoutChangeListener(prior.listener);
        }
    }

    public static void style(View row, CallTypeResolver.Kind kind) {
        if (row == null) return;
        restore(row);
        Context context = row.getContext();
        if (!CallColorPalette.enabled(context) || kind == null ||
            kind == CallTypeResolver.Kind.ORIGINAL ||
            (PreferenceRepository.darkOnly(context) && !ThemeEngine.isNight(context))) return;

        int iconId = context.getResources().getIdentifier("call_type_icon", "id", context.getPackageName());
        int textId = context.getResources().getIdentifier("secondary_text", "id", context.getPackageName());
        if (iconId == 0 || textId == 0) return;
        View iconView = row.findViewById(iconId);
        View descriptionView = row.findViewById(textId);
        if (!(iconView instanceof ImageView) || !(descriptionView instanceof TextView)) return;
        ImageView icon = (ImageView) iconView;
        TextView secondary = (TextView) descriptionView;
        Snapshot prior = new Snapshot();
        prior.image = new WeakReference<>(icon);
        prior.imageTint = icon.getImageTintList();
        prior.secondary = new WeakReference<>(secondary);
        prior.secondaryColor = secondary.getTextColors();
        ORIGINAL.put(row, prior);

        int color = CallColorPalette.color(context, kind);
        String placement = CallColorPalette.placement(context);
        icon.setImageTintList(ColorStateList.valueOf(color));
        if (!CallColorPalette.ICON.equals(placement)) secondary.setTextColor(color);
        if (CallColorPalette.STRIPE.equals(placement)) {
            int width = ThemeEngine.dp(context, 3);
            ColorDrawable stripe = new ColorDrawable(color);
            stripe.setBounds(0, 0, width, row.getHeight());
            StripeListener listener = new StripeListener(stripe, width);
            row.addOnLayoutChangeListener(listener);
            row.getOverlay().add(stripe);
            prior.stripe = stripe;
            prior.listener = listener;
        }
    }
}
