package local.dialer.visual;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Opt-in styling for the audited Google Phone 241 history rows.
 * The native adapter calls restore() before every RecyclerView rebind, then
 * style() after the native binding. Never touches click listeners, caller
 * names, the right-hand call action or the Telecom service.
 */
public final class CallHistoryRenderer {
    private static final Map<View, Snapshot> ORIGINAL =
        Collections.synchronizedMap(new WeakHashMap<View, Snapshot>());

    private static final class StripeListener implements View.OnLayoutChangeListener {
        final GradientDrawable stripe;
        final int inset;
        final int width;
        final int vInset;

        StripeListener(GradientDrawable stripe, int inset, int width, int vInset) {
            this.stripe = stripe;
            this.inset = inset;
            this.width = width;
            this.vInset = vInset;
        }

        void update(View row) {
            int bottom = Math.max(vInset, row.getHeight() - vInset);
            stripe.setBounds(inset, vInset, inset + width, bottom);
        }

        @Override public void onLayoutChange(View view, int left, int top,
                int right, int bottom, int oldLeft, int oldTop,
                int oldRight, int oldBottom) {
            update(view);
        }
    }

    private static final class Snapshot {
        WeakReference<ImageView> image;
        Drawable imageDrawable;
        ColorStateList imageTint;
        ImageView.ScaleType imageScaleType;
        int imageWidth;
        int imageHeight;
        CharSequence imageDescription;
        int imageAccessibility;

        WeakReference<TextView> secondary;
        ColorStateList secondaryColor;
        CharSequence secondaryText;
        TextUtils.TruncateAt secondaryEllipsize;
        int secondaryMaxLines;

        GradientDrawable stripe;
        StripeListener listener;
    }

    private CallHistoryRenderer() { }

    /** Complete restoration is mandatory before a recycled holder is rebound. */
    public static void restore(View row) {
        if (row == null) return;
        Snapshot prior = ORIGINAL.remove(row);
        if (prior == null) return;

        ImageView restoredIcon = prior.image == null ? null : prior.image.get();
        if (restoredIcon != null) {
            ImageView icon = restoredIcon;
            icon.setImageTintList(null);
            icon.setImageDrawable(prior.imageDrawable);
            icon.setImageTintList(prior.imageTint);
            if (prior.imageScaleType != null) icon.setScaleType(prior.imageScaleType);
            icon.setContentDescription(prior.imageDescription);
            icon.setImportantForAccessibility(prior.imageAccessibility);
            ViewGroup.LayoutParams lp = icon.getLayoutParams();
            if (lp != null && prior.imageWidth > 0 && prior.imageHeight > 0) {
                lp.width = prior.imageWidth;
                lp.height = prior.imageHeight;
                icon.setLayoutParams(lp);
            }
        }
        TextView restoredText = prior.secondary == null ? null : prior.secondary.get();
        if (restoredText != null) {
            TextView text = restoredText;
            text.setSingleLine(prior.secondaryMaxLines == 1);
            text.setMaxLines(prior.secondaryMaxLines);
            text.setEllipsize(prior.secondaryEllipsize);
            text.setText(prior.secondaryText);
            text.setTextColor(prior.secondaryColor);
        }
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

        int iconId = context.getResources().getIdentifier(
            "call_type_icon", "id", context.getPackageName());
        int textId = context.getResources().getIdentifier(
            "secondary_text", "id", context.getPackageName());
        if (iconId == 0 || textId == 0) return;
        View iconView = row.findViewById(iconId);
        View textView = row.findViewById(textId);
        if (!(iconView instanceof ImageView) || !(textView instanceof TextView)) return;

        ImageView icon = (ImageView) iconView;
        TextView secondary = (TextView) textView;
        Snapshot prior = new Snapshot();
        prior.image = new WeakReference<>(icon);
        prior.imageDrawable = icon.getDrawable();
        prior.imageTint = icon.getImageTintList();
        prior.imageScaleType = icon.getScaleType();
        prior.imageDescription = icon.getContentDescription();
        prior.imageAccessibility = icon.getImportantForAccessibility();
        ViewGroup.LayoutParams lp = icon.getLayoutParams();
        prior.imageWidth = lp == null ? 0 : lp.width;
        prior.imageHeight = lp == null ? 0 : lp.height;
        prior.secondary = new WeakReference<>(secondary);
        prior.secondaryColor = secondary.getTextColors();
        prior.secondaryText = secondary.getText();
        prior.secondaryMaxLines = secondary.getMaxLines();
        prior.secondaryEllipsize = secondary.getEllipsize();
        ORIGINAL.put(row, prior);

        try {
            int color = CallColorPalette.color(context, kind);
            String placement = CallColorPalette.placement(context);
            String iconStyle = CallColorPalette.iconStyle(context);
            if (!CallColorPalette.ICON_NATIVE.equals(iconStyle)) {
                boolean badge = CallColorPalette.ICON_BADGE.equals(iconStyle);
                int targetSize = ThemeEngine.dp(context, badge ? 23 : 21);
                if (lp != null && lp.width > 0 && lp.height > 0) {
                    lp.width = Math.max(lp.width, targetSize);
                    lp.height = Math.max(lp.height, targetSize);
                    icon.setLayoutParams(lp);
                }
                icon.setImageTintList(null);
                icon.setImageDrawable(new CallHistoryIconDrawable(kind, color, badge));
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else {
                icon.setImageTintList(ColorStateList.valueOf(color));
            }
            icon.setContentDescription("Llamada " + kind.label.toLowerCase(
                java.util.Locale.ROOT));
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

            if (!CallColorPalette.ICON.equals(placement)) {
                secondary.setTextColor(color);
            }
            if (CallColorPalette.labels(context)) {
                // A second line preserves the native date, number type and
                // phone-account metadata instead of replacing or truncating it.
                SpannableStringBuilder twoLines = new SpannableStringBuilder();
                twoLines.append(kind.label).append('\n');
                if (prior.secondaryText != null) twoLines.append(prior.secondaryText);
                secondary.setSingleLine(false);
                secondary.setMaxLines(2);
                secondary.setEllipsize(TextUtils.TruncateAt.END);
                secondary.setText(twoLines);
            }

            if (CallColorPalette.STRIPE.equals(placement)) {
                int width = ThemeEngine.dp(context, 3);
                int inset = ThemeEngine.dp(context, 8);
                int vertical = ThemeEngine.dp(context, 5);
                GradientDrawable stripe = new GradientDrawable();
                stripe.setShape(GradientDrawable.RECTANGLE);
                stripe.setColor(color);
                stripe.setCornerRadius(ThemeEngine.dp(context, 2));
                StripeListener listener = new StripeListener(stripe, inset, width, vertical);
                listener.update(row);
                row.addOnLayoutChangeListener(listener);
                row.getOverlay().add(stripe);
                prior.stripe = stripe;
                prior.listener = listener;
            }
        } catch (RuntimeException failed) {
            restore(row);
            throw failed;
        }
    }
}
