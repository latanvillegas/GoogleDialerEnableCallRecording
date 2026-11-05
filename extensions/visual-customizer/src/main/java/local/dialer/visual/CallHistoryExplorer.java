package local.dialer.visual;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.provider.CallLog;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Independent read-only view of Android's public call-log provider.
 * Does not alter the Google Phone RecyclerView, its pagination, its
 * proprietary spam classification, or any call/recording operation.
 * Personal call data is never persisted, logged, or exported.
 */
public final class CallHistoryExplorer {
    private static final int MAX_SCAN = 5000;
    private static final int MAX_VISIBLE = 80;
    private static final CallTypeResolver.Kind[] FILTERS = {
        CallTypeResolver.Kind.ORIGINAL,
        CallTypeResolver.Kind.MISSED,
        CallTypeResolver.Kind.INCOMING,
        CallTypeResolver.Kind.OUTGOING,
        CallTypeResolver.Kind.REJECTED,
        CallTypeResolver.Kind.BLOCKED
    };
    private static final String[] FILTER_LABELS = {
        "Todas", "Perdidas", "Recibidas", "Realizadas", "Rechazadas", "Bloqueadas"
    };

    private CallHistoryExplorer() { }

    private static final class Record {
        final CallTypeResolver.Kind kind;
        final String caller;
        final long when;

        Record(CallTypeResolver.Kind kind, String caller, long when) {
            this.kind = kind;
            this.caller = caller;
            this.when = when;
        }
    }

    private static final class Result {
        final List<Record> records = new ArrayList<>();
        final Map<CallTypeResolver.Kind, Integer> counts =
            new EnumMap<>(CallTypeResolver.Kind.class);
        boolean limited;

        Result() {
            for (CallTypeResolver.Kind kind : CallTypeResolver.Kind.values()) {
                counts.put(kind, 0);
            }
        }
    }

    private static final class State {
        final CancellationSignal cancel = new CancellationSignal();
        final AtomicBoolean closed = new AtomicBoolean(false);
        Result result;
    }

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (activity.checkSelfPermission(Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(activity)
                .setTitle("Acceso al historial")
                .setMessage("Android no ha concedido READ_CALL_LOG a esta instalación. "
                    + "No se leerán registros. Comprueba los permisos de Teléfono en "
                    + "Ajustes del sistema; el personalizador no solicita nuevos permisos.")
                .setPositiveButton("Entendido", null)
                .show();
            return;
        }

        boolean night = ThemeEngine.isNight(activity);
        int foreground = night ? Color.WHITE : Color.BLACK;
        int secondary = night ? 0xffbababa : 0xff555555;
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 18), dp(activity, 6),
            dp(activity, 18), dp(activity, 8));
        content.setBackgroundColor(night ? Color.BLACK : Color.WHITE);

        content.addView(text(activity,
            "Consulta local del historial de Android. No modifica la lista principal "
            + "ni realiza llamadas. Spam no se deduce de un número desconocido.",
            secondary, 13));

        Spinner selector = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
            android.R.layout.simple_spinner_item, FILTER_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        selector.setAdapter(adapter);
        content.addView(selector);

        TextView status = text(activity, "Leyendo historial…", secondary, 13);
        content.addView(status);
        TextView statistics = text(activity, "", foreground, 14);
        content.addView(statistics);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        content.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 340)));

        State state = new State();
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("Historial filtrado y estadísticas")
            .setView(content)
            .setPositiveButton("Cerrar", null)
            .create();
        dialog.setOnDismissListener(ignored -> {
            state.closed.set(true);
            state.cancel.cancel();
            state.result = null;
            rows.removeAllViews();
        });
        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                 int position, long id) {
                if (state.result != null) {
                    render(activity, state.result, FILTERS[position],
                        rows, status, statistics, scroll, secondary, night);
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        dialog.show();

        new Thread(() -> {
            try {
                Result result = query(activity, state);
                if (state.closed.get()) return;
                activity.runOnUiThread(() -> {
                    if (state.closed.get() || !dialog.isShowing()) return;
                    state.result = result;
                    render(activity, result, FILTERS[selector.getSelectedItemPosition()],
                        rows, status, statistics, scroll, secondary, night);
                });
            } catch (SecurityException permission) {
                displayError(activity, dialog, state, status,
                    "Android rechazó el acceso al historial. Revisa los permisos de Teléfono.");
            } catch (OperationCanceledException cancelled) {
                // Dismissal cancels the provider traversal.
            } catch (RuntimeException failure) {
                displayError(activity, dialog, state, status,
                    "No se pudo consultar el proveedor del historial: "
                    + failure.getClass().getSimpleName());
            }
        }, "dialer-visual-history-reader").start();
    }

    private static Result query(Activity activity, State state) {
        Result result = new Result();
        String[] projection = {
            CallLog.Calls.TYPE, CallLog.Calls.DATE,
            CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME
        };
        try (Cursor cursor = activity.getContentResolver().query(
                CallLog.Calls.CONTENT_URI, projection, null, null,
                CallLog.Calls.DATE + " DESC", state.cancel)) {
            if (cursor == null) throw new IllegalStateException("Proveedor no disponible");
            int typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE);
            int dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE);
            int numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER);
            int nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
            while (!state.closed.get()
                    && result.records.size() < MAX_SCAN && cursor.moveToNext()) {
                CallTypeResolver.Kind kind =
                    CallTypeResolver.resolve(cursor.getInt(typeIndex), false);
                String name = nameIndex < 0 ? null : cursor.getString(nameIndex);
                String number = cursor.getString(numberIndex);
                String caller = !TextUtils.isEmpty(name) ? name
                    : !TextUtils.isEmpty(number) ? number : "Número oculto";
                result.records.add(new Record(kind, caller, cursor.getLong(dateIndex)));
                result.counts.put(kind, result.counts.get(kind) + 1);
            }
            if (!state.closed.get() && result.records.size() == MAX_SCAN) {
                result.limited = cursor.moveToNext();
            }
        }
        return result;
    }

    private static void displayError(Activity activity, AlertDialog dialog,
                                     State state, TextView status, String message) {
        if (state.closed.get()) return;
        activity.runOnUiThread(() -> {
            if (!state.closed.get() && dialog.isShowing()) status.setText(message);
        });
    }

    private static void render(Activity activity, Result result,
                               CallTypeResolver.Kind selected, LinearLayout rows,
                               TextView status, TextView statistics, ScrollView scroll,
                               int secondary, boolean night) {
        rows.removeAllViews();
        StringBuilder summary = new StringBuilder("Recuentos reales (eventos individuales):");
        for (CallTypeResolver.Kind kind : FILTERS) {
            if (kind == CallTypeResolver.Kind.ORIGINAL) continue;
            summary.append("\n").append(kind.label).append(": ")
                .append(result.counts.get(kind));
        }
        int other = result.counts.get(CallTypeResolver.Kind.ORIGINAL);
        if (other > 0) summary.append("\nOtros: ").append(other);
        statistics.setText(summary.toString());

        DateFormat formatter = DateFormat.getDateTimeInstance(
            DateFormat.SHORT, DateFormat.SHORT);
        int shown = 0;
        int matched = 0;
        for (Record record : result.records) {
            if (selected != CallTypeResolver.Kind.ORIGINAL
                    && selected != record.kind) continue;
            matched++;
            if (shown >= MAX_VISIBLE) continue;
            shown++;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(activity, 6), 0, dp(activity, 6));

            int color = record.kind == CallTypeResolver.Kind.ORIGINAL
                ? secondary : CallColorPalette.color(activity, record.kind);
            if (!night) color = ThemeEngine.coloredText(color, false);
            TextView badge = text(activity, symbol(record.kind), color, 23);
            row.addView(badge, new LinearLayout.LayoutParams(
                dp(activity, 34), ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout column = new LinearLayout(activity);
            column.setOrientation(LinearLayout.VERTICAL);
            TextView caller = text(activity, record.caller,
                night ? Color.WHITE : Color.BLACK, 15);
            caller.setSingleLine(true);
            caller.setEllipsize(TextUtils.TruncateAt.END);
            column.addView(caller);
            TextView details = text(activity,
                record.kind.label + " · " + formatter.format(new Date(record.when)),
                color, 12);
            column.addView(details);
            row.addView(column, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            rows.addView(row);
        }
        status.setText(result.records.size() + " eventos examinados"
            + (result.limited ? " (límite de los 5.000 más recientes)" : "")
            + " · " + matched + " coincidentes"
            + (matched > MAX_VISIBLE ? " · mostrando los primeros " + MAX_VISIBLE : ""));
        if (matched == 0) rows.addView(text(activity,
            "No hay entradas de este tipo.", secondary, 14));
        scroll.scrollTo(0, 0);
    }

    private static String symbol(CallTypeResolver.Kind kind) {
        switch (kind) {
            case MISSED: return "↙";
            case INCOMING: return "↘";
            case OUTGOING: return "↗";
            case REJECTED: return "×";
            case BLOCKED: return "⊘";
            default: return "•";
        }
    }

    private static TextView text(Activity activity, String value, int color, int size) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setPadding(0, dp(activity, 3), 0, dp(activity, 3));
        return view;
    }

    private static int dp(Activity activity, float value) {
        return ThemeEngine.dp(activity, value);
    }
}
