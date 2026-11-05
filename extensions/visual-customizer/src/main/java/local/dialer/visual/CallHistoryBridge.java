package local.dialer.visual;

import android.util.Log;
import android.view.View;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Audited Google Phone 241 row binding bridge.
 *
 * Called on the UI thread before and after the native adapter binds one row.
 * No content provider access, no phone numbers, no contact identities and no
 * modifications to call actions. Fail closed to native rendering.
 */
public final class CallHistoryBridge {
    private static final String TAG = "DialerVisual.History";
    private static final AtomicBoolean REPORTED = new AtomicBoolean(false);

    private CallHistoryBridge() { }

    public static void onBeforeBind(View row) {
        if (row == null) return;
        try {
            CallHistoryRenderer.restore(row);
        } catch (RuntimeException failure) {
            report(failure);
        }
    }

    public static void onAfterBind(View row, int androidCallType) {
        if (row == null || !CallColorPalette.enabled(row.getContext())) return;
        try {
            // Android's raw types are derived from the verified Lkgn.q field.
            // Spam must NOT be inferred from an unknown number or call type.
            CallTypeResolver.Kind kind = CallTypeResolver.resolve(androidCallType, false);
            if (kind != CallTypeResolver.Kind.ORIGINAL) {
                CallHistoryRenderer.style(row, kind);
            }
        } catch (RuntimeException failure) {
            report(failure);
        }
    }

    private static void report(RuntimeException failure) {
        if (REPORTED.compareAndSet(false, true)) {
            Log.w(TAG, "History appearance disabled for affected row; native UI preserved", failure);
        }
    }
}
