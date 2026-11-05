package local.dialer.visual;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Settings for audited real-history row colors. Native app filtering stays
 * unchanged; CallHistoryExplorer supplies a separate read-only Android
 * CallLog.Calls view with actual type filters and event counts.
 */
public final class CallHistorySettingsPanel {
    private static final String[] PLACEMENT_LABELS = {
        "Solo flecha", "Flecha y descripción", "Flecha, descripción y franja lateral"
    };
    private static final String[] PLACEMENT_VALUES = {
        CallColorPalette.ICON, CallColorPalette.ICON_AND_TEXT, CallColorPalette.STRIPE
    };
    private static final String[] FILTER_LABELS = {
        "Todas", "Perdidas", "Recibidas", "Realizadas", "Spam", "Rechazadas", "Bloqueadas"
    };
    private static final String[] FILTER_VALUES = {
        "all", "missed", "incoming", "outgoing", "spam", "rejected", "blocked"
    };
    private final Activity activity;
    private final SharedPreferences prefs;
    private final LinearLayout preview;
    private final int foreground;

    private CallHistorySettingsPanel(Activity activity) {
        this.activity = activity;
        this.prefs = PreferenceRepository.prefs(activity);
        this.foreground = ThemeEngine.isNight(activity) ? Color.WHITE : Color.BLACK;
        this.preview = new LinearLayout(activity);
        preview.setOrientation(LinearLayout.VERTICAL);
    }
    public static void install(Activity activity, LinearLayout parent) {
        new CallHistorySettingsPanel(activity).build(parent);
    }
    private int dp(float size) { return ThemeEngine.dp(activity, size); }
    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(foreground);
        view.setTextSize(sp);
        view.setPadding(0, dp(7), 0, dp(7));
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private void build(LinearLayout root) {
        root.addView(text("Historial de llamadas · personalización", 19, true));
        root.addView(text(
            "Colores en filas reales para recibidas, realizadas, perdidas, rechazadas y bloqueadas. "
            + "Spam conserva su estilo nativo hasta verificar su indicador explícito. "
            + "Los filtros y recuentos reales están disponibles en el explorador independiente; "
            + "no modifica los filtros nativos de Google Phone.",
            13, false));

        Switch enabled = new Switch(activity);
        enabled.setText("Activar colores del historial real (experimental)");
        enabled.setTextColor(foreground);
        enabled.setChecked(CallColorPalette.enabled(activity));
        enabled.setOnCheckedChangeListener((button, checked) ->
            prefs.edit().putBoolean(CallColorPalette.ENABLED, checked).apply());
        root.addView(enabled);

        spinner(root, "Ubicación del color", PLACEMENT_LABELS, PLACEMENT_VALUES,
            CallColorPalette.PLACEMENT, CallColorPalette.ICON_AND_TEXT);
        Switch labels = new Switch(activity);
        labels.setText("Mostrar etiquetas en la vista previa");
        labels.setTextColor(foreground);
        labels.setChecked(CallColorPalette.labels(activity));
        labels.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(CallColorPalette.SHOW_LABELS, checked).apply();
            refresh();
        });
        root.addView(labels);

        root.addView(text("Colores por tipo · toca para introducir HEX", 15, true));
        root.addView(text("El color Spam está preparado, pero no se aplica en filas reales " +
            "hasta identificar la marca explícita del modelo.", 12, false));
        for (CallTypeResolver.Kind kind : CallColorPalette.supported()) {
            Button button = new Button(activity);
            button.setAllCaps(false);
            button.setText(kind.label + " · elegir color");
            button.setOnClickListener(view -> pickColor(kind));
            root.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        }

        spinner(root, "Filtrar vista previa (no filtra llamadas reales)",
            FILTER_LABELS, FILTER_VALUES, CallColorPalette.FILTER, "all");
        root.addView(text("Vista previa de ejemplo", 15, true));
        preview.setPadding(dp(10), dp(10), dp(10), dp(10));
        preview.setBackgroundColor(Color.BLACK);
        root.addView(preview);

        root.addView(text("Historial real · consulta de solo lectura", 16, true));
        root.addView(text(
            "El explorador consulta los eventos individuales del proveedor Android "
            + "(hasta 5.000 recientes), muestra los primeros 80 resultados y calcula "
            + "recuentos por tipo. Requiere READ_CALL_LOG ya concedido. "
            + "No cambia el historial principal, no guarda llamadas y no identifica "
            + "spam sin una marca explícita verificada.", 12, false));
        Button explorer = new Button(activity);
        explorer.setAllCaps(false);
        explorer.setText("Abrir historial filtrado y estadísticas reales");
        explorer.setOnClickListener(view -> CallHistoryExplorer.show(activity));
        root.addView(explorer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        Button restore = new Button(activity);
        restore.setAllCaps(false);
        restore.setText("Restablecer colores del historial");
        restore.setOnClickListener(v -> new AlertDialog.Builder(activity)
            .setTitle("Restablecer historial")
            .setMessage("Restaura solo los colores del personalizador. No elimina llamadas.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Restablecer", (dialog, which) -> {
                CallColorPalette.reset(activity);
                activity.recreate();
            }).show());
        root.addView(restore);
        refresh();
    }

    private void spinner(LinearLayout root, String title, String[] captions,
                         String[] values, String key, String fallback) {
        root.addView(text(title, 15, true));
        Spinner spinner = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
            android.R.layout.simple_spinner_item, captions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        String saved = PreferenceRepository.get(activity, key, fallback);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(saved)) { spinner.setSelection(i); break; }
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View item,
                                                  int position, long id) {
                prefs.edit().putString(key, values[position]).apply();
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        root.addView(spinner, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
    }

    private void pickColor(CallTypeResolver.Kind kind) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setText(ThemeEngine.hex(CallColorPalette.color(activity, kind)));
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(activity)
            .setTitle("Color: " + kind.label)
            .setMessage("Formato #RRGGBB")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Original", (dialog, which) -> {
                prefs.edit().remove(CallColorPalette.colorKey(kind)).apply();
                refresh();
            })
            .setPositiveButton("Guardar", (dialog, which) -> {
                try {
                    CallColorPalette.setColor(activity, kind, input.getText().toString().trim());
                    refresh();
                } catch (IllegalArgumentException invalid) {
                    Toast.makeText(activity, "Color inválido: usa #RRGGBB",
                        Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    private void refresh() {
        preview.removeAllViews();
        String filter = PreferenceRepository.get(activity, CallColorPalette.FILTER, "all");
        if (!CallColorPalette.validFilter(filter)) filter = "all";
        boolean labels = CallColorPalette.labels(activity);
        String placement = CallColorPalette.placement(activity);
        for (CallTypeResolver.Kind kind : CallColorPalette.supported()) {
            if (!CallColorPalette.visibleUnderFilter(kind, filter)) continue;
            int color = CallColorPalette.color(activity, kind);
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(8), dp(4), dp(8));
            if (CallColorPalette.STRIPE.equals(placement)) {
                TextView stripe = text("▏", 22, true);
                stripe.setTextColor(color);
                row.addView(stripe);
            }
            String arrow = kind == CallTypeResolver.Kind.INCOMING ? "↘" :
                kind == CallTypeResolver.Kind.OUTGOING ? "↗" :
                kind == CallTypeResolver.Kind.MISSED ? "↙" :
                kind == CallTypeResolver.Kind.SPAM ? "!" :
                kind == CallTypeResolver.Kind.REJECTED ? "×" : "⊘";
            TextView marker = text(arrow, 23, true);
            marker.setTextColor(color);
            row.addView(marker, new LinearLayout.LayoutParams(dp(34), dp(45)));
            LinearLayout column = new LinearLayout(activity);
            column.setOrientation(LinearLayout.VERTICAL);
            TextView name = text("Contacto de ejemplo", 14, false);
            name.setTextColor(Color.WHITE);
            column.addView(name);
            TextView summary = text(labels ? "Ejemplo · " + kind.label : "Ejemplo · llamada", 12, false);
            summary.setTextColor(CallColorPalette.ICON.equals(placement) ? 0xffaaaaaa : color);
            column.addView(summary);
            row.addView(column);
            preview.addView(row);
        }
    }
}
