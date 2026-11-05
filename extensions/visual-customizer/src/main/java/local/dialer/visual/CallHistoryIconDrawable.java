package local.dialer.visual;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * Self-contained vector indicator for the verified Google Phone call_type_icon
 * ImageView. Drawn in a 24x24 viewport: no resource IDs, bitmap allocations,
 * hidden platform APIs or remote assets. The call button is not modified.
 */
public final class CallHistoryIconDrawable extends Drawable {
    private static final float VIEWPORT = 24f;
    private final CallTypeResolver.Kind kind;
    private final int color;
    private final boolean badge;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int alpha = 255;
    private ColorFilter filter;

    public CallHistoryIconDrawable(CallTypeResolver.Kind kind, int color, boolean badge) {
        if (kind == null || kind == CallTypeResolver.Kind.ORIGINAL) {
            throw new IllegalArgumentException("A verified call type is required");
        }
        this.kind = kind;
        this.color = color | Color.BLACK;
        this.badge = badge;
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        int save = canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(bounds.width() / VIEWPORT, bounds.height() / VIEWPORT);
        paint.setColorFilter(filter);

        if (badge) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb((42 * alpha) / 255,
                Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawCircle(12, 12, 11.8f, paint);
            canvas.save();
            canvas.scale(0.78f, 0.78f, 12, 12);
        }

        paint.setColor(color);
        paint.setAlpha(alpha);
        paint.setStyle(Paint.Style.FILL);
        // Android's canonical telephone handset geometry, scaled to leave
        // room for the call-state indicator in the upper-right corner.
        Path phone = new Path();
        phone.moveTo(6.62f, 10.79f);
        phone.cubicTo(8.06f, 13.62f, 10.38f, 15.93f, 13.21f, 17.38f);
        phone.lineTo(15.41f, 15.18f);
        phone.cubicTo(15.68f, 14.91f, 16.08f, 14.82f, 16.43f, 14.94f);
        phone.cubicTo(17.55f, 15.31f, 18.76f, 15.51f, 20f, 15.51f);
        phone.cubicTo(20.55f, 15.51f, 21f, 15.96f, 21f, 16.51f);
        phone.lineTo(21f, 20f);
        phone.cubicTo(21f, 20.55f, 20.55f, 21f, 20f, 21f);
        phone.cubicTo(10.61f, 21f, 3f, 13.39f, 3f, 4f);
        phone.cubicTo(3f, 3.45f, 3.45f, 3f, 4f, 3f);
        phone.lineTo(7.5f, 3f);
        phone.cubicTo(8.05f, 3f, 8.5f, 3.45f, 8.5f, 4f);
        phone.cubicTo(8.5f, 5.24f, 8.7f, 6.45f, 9.07f, 7.57f);
        phone.cubicTo(9.18f, 7.92f, 9.1f, 8.32f, 8.82f, 8.6f);
        phone.close();
        canvas.save();
        canvas.translate(1f, 4f);
        canvas.scale(0.76f, 0.76f);
        canvas.drawPath(phone, paint);
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.15f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        switch (kind) {
            case INCOMING:
                // Arrow pointing towards the handset.
                canvas.drawLine(22, 2.5f, 14.5f, 10, paint);
                canvas.drawLine(14.5f, 4.4f, 14.5f, 10, paint);
                canvas.drawLine(14.5f, 10, 20.1f, 10, paint);
                break;
            case OUTGOING:
                canvas.drawLine(14.5f, 10, 22, 2.5f, paint);
                canvas.drawLine(16.4f, 2.5f, 22, 2.5f, paint);
                canvas.drawLine(22, 2.5f, 22, 8.1f, paint);
                break;
            case MISSED:
                canvas.drawLine(15, 3, 22, 10, paint);
                canvas.drawLine(22, 3, 15, 10, paint);
                break;
            case REJECTED:
                canvas.drawLine(14.7f, 3.1f, 22.3f, 10.7f, paint);
                canvas.drawLine(20.7f, 3.1f, 16.3f, 7.5f, paint);
                break;
            case BLOCKED:
                Path shield = new Path();
                shield.moveTo(18f, 2f);
                shield.lineTo(22f, 3.5f);
                shield.lineTo(22f, 7f);
                shield.quadTo(22f, 9.1f, 18f, 11.2f);
                shield.quadTo(14f, 9.1f, 14f, 7f);
                shield.lineTo(14f, 3.5f);
                shield.close();
                canvas.drawPath(shield, paint);
                canvas.drawLine(16.4f, 4.5f, 19.6f, 8.5f, paint);
                break;
            case SPAM:
                Path warning = new Path();
                warning.moveTo(18f, 2f);
                warning.lineTo(22.3f, 10.3f);
                warning.lineTo(13.7f, 10.3f);
                warning.close();
                canvas.drawPath(warning, paint);
                canvas.drawLine(18f, 4.4f, 18f, 7.1f, paint);
                canvas.drawPoint(18f, 8.6f, paint);
                break;
            default:
                break;
        }

        if (badge) canvas.restore();
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) {
        this.alpha = Math.max(0, Math.min(255, alpha));
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter colorFilter) {
        this.filter = colorFilter;
        invalidateSelf();
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 24; }
    @Override public int getIntrinsicHeight() { return 24; }
}
