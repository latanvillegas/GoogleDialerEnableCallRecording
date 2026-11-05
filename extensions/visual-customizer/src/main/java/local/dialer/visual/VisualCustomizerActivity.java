package local.dialer.visual;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.VideoView;
import android.widget.FrameLayout;
import android.media.MediaPlayer;
import android.text.method.ScrollingMovementMethod;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Internal, opt-in appearance editor; uses Android framework UI only. */
public final class VisualCustomizerActivity extends Activity {
    private static final String[] PROFILE_LABELS = {
        "Colores del sistema (original)", "AMOLED clásico", "Pastel Rainbow",
        "Neon AMOLED", "Ocean AMOLED", "Sunset Gradient", "Emerald Glass",
        "Monochrome Pro", "Personalizado"
    };
    private static final String[] PROFILE_VALUES = {
        "system", "amoled", "pastel", "neon", "ocean", "sunset", "emerald", "monochrome", "custom"
    };
    private static final String[] STYLE_LABELS = { "Relleno", "Contorno", "Solo números" };
    private static final String[] STYLE_VALUES = { "filled", "outline", "numbers" };
    private static final String[] ACCENT_LABELS = {
        "Original (verde)", "Verde menta", "Melocotón", "Cian", "Violeta", "Rosa"
    };
    private static final String[] ACCENT_VALUES = { "system", "green", "peach", "cyan", "violet", "pink" };
    private static final String[] COLOR_LABELS = {
        "Melocotón", "Dorado", "Lima", "Menta", "Cian", "Rosa", "Violeta", "Gris azulado"
    };
    private static final int[] COLOR_VALUES = {
        0xffffb385, 0xffffe18b, 0xffb9ee83, 0xff77e5c1,
        0xff57e9f0, 0xffff71b7, 0xffb8a3ff, 0xff8995ad
    };

    private static final int EXPORT_JSON = 3501;
    private static final int IMPORT_JSON = 3502;
    private static final String[] CALL_STYLE_LABELS = { "Nativo", "Relleno", "Contorno" };
    private static final String[] CALL_STYLE_VALUES = { "original", "filled", "outline" };
    private static final String[] CALL_BACKGROUND_LABELS = {
        "Original", "Portada estática", "Video sin sonido"
    };
    private static final String[] CALL_BACKGROUND_VALUES = { "original", "cover", "video" };

    private SharedPreferences preferences;
    private LinearLayout grid;
    private TextView previewCall;
    private Spinner profileSpinner;
    private Spinner styleSpinner;
    private Spinner accentSpinner;
    private Spinner callBackgroundSpinner;
    private Spinner callStyleSpinner;
    private Spinner callAccentSpinner;
    private Switch inCallEnabledSwitch;
    private Switch darkOnlySwitch;
    private LinearLayout mediaPreview;
    private VideoView playingPreview;
    private MediaPlayer playingTone;
    private int foreground;
    private int background;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = VisualCustomizer.preferences(this);
        boolean night = VisualCustomizer.isNight(this);
        background = night ? Color.BLACK : Color.WHITE;
        foreground = night ? Color.WHITE : Color.BLACK;

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setBackgroundColor(background);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(35));
        scroller.addView(content);
        setContentView(scroller);

        TextView title = label("Personalización visual", 25, true);
        content.addView(title);
        content.addView(label("Google Phone · Configuración local. La grabación de llamadas y el tema nativo no se modifican.", 13, false));

        profileSpinner = selector(content, "Paleta", PROFILE_LABELS, PROFILE_VALUES,
            VisualCustomizer.PROFILE, "system");
        styleSpinner = selector(content, "Forma de las teclas", STYLE_LABELS, STYLE_VALUES,
            VisualCustomizer.STYLE, "filled");
        accentSpinner = selector(content, "Color del botón Llamar", ACCENT_LABELS,
            ACCENT_VALUES, VisualCustomizer.ACCENT, "system");

        darkOnlySwitch = new Switch(this);
        darkOnlySwitch.setText("Colores personalizados solo en modo oscuro");
        darkOnlySwitch.setTextColor(foreground);
        darkOnlySwitch.setChecked(preferences.getBoolean(VisualCustomizer.DARK_ONLY, true));
        darkOnlySwitch.setPadding(0, dp(18), 0, dp(12));
        darkOnlySwitch.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(VisualCustomizer.DARK_ONLY, checked).apply();
            refreshPreview();
        });
        content.addView(darkOnlySwitch);

        content.addView(label("Vista previa · toca un número para elegir su color", 15, true));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(10), dp(10), dp(10), dp(10));
        grid.setBackgroundColor(Color.BLACK);
        content.addView(grid);

        previewCall = new TextView(this);
        previewCall.setText("☎   Llamar");
        previewCall.setTextSize(16);
        previewCall.setTextColor(Color.BLACK);
        previewCall.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams callParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(53));
        callParams.setMargins(dp(44), dp(8), dp(44), dp(12));
        grid.addView(previewCall, callParams);

        content.addView(label("En Personalizado, toca cada número para definir un color distinto. " +
            "Las paletas se guardan localmente y se actualizan al volver al teclado.", 13, false));

        content.addView(label("Pantallas de llamada · experimental", 19, true));
        content.addView(label("Puedes personalizar llamadas de audio entrantes y los controles Xatu. " +
            "No modifica los botones Contestar, Rechazar o Colgar, ni la gestión de llamadas.", 13, false));
        inCallEnabledSwitch = new Switch(this);
        inCallEnabledSwitch.setText("Aplicar personalización durante llamadas reales (experimental)");
        inCallEnabledSwitch.setTextColor(foreground);
        inCallEnabledSwitch.setChecked(PreferenceRepository.inCallEnabled(this));
        inCallEnabledSwitch.setPadding(0, dp(15), 0, dp(10));
        inCallEnabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(PreferenceRepository.IN_CALL_ENABLED, checked).apply();
        });
        content.addView(inCallEnabledSwitch);
        callStyleSpinner = selector(content, "Estilo de controles durante llamada", CALL_STYLE_LABELS, CALL_STYLE_VALUES,
            PreferenceRepository.CALL_STYLE, "original");
        callAccentSpinner = selector(content, "Acento de controles (no modifica Colgar)", ACCENT_LABELS,
            ACCENT_VALUES, PreferenceRepository.CALL_ACCENT, "system");
        callBackgroundSpinner = selector(content, "Fondo de llamada entrante de audio", CALL_BACKGROUND_LABELS,
            CALL_BACKGROUND_VALUES, PreferenceRepository.CALL_BACKGROUND, "original");

        content.addView(label("Portadas, videotonos y audio", 19, true));
        content.addView(label("La portada y el video silencioso se aplican solo si activas la opción experimental. " +
            "El tono de llamada de Android no se sustituye; el audio se puede previsualizar.", 13, false));
        addButton(content, "Elegir portada desde archivos", v -> MediaThemeEngine.request(this, MediaThemeEngine.PICK_COVER));
        addButton(content, "Elegir video (vista previa silenciosa)", v -> MediaThemeEngine.request(this, MediaThemeEngine.PICK_VIDEO));
        addButton(content, "Elegir audio para tono futuro", v -> MediaThemeEngine.request(this, MediaThemeEngine.PICK_TONE));
        addButton(content, "Escuchar tono seleccionado", v -> {
            if (playingTone != null) { playingTone.release(); playingTone = null; }
            playingTone = MediaThemeEngine.previewTone(this);
            if (playingTone == null) Toast.makeText(this, "Audio no disponible", Toast.LENGTH_SHORT).show();
            else playingTone.setOnCompletionListener(player -> {
                player.release();
                if (playingTone == player) playingTone = null;
            });
        });
        addButton(content, "Eliminar medios seleccionados", v -> {
            MediaThemeEngine.clearMedia(this);
            if (callBackgroundSpinner != null) callBackgroundSpinner.setSelection(0);
            refreshMediaPreview();
        });
        mediaPreview = new LinearLayout(this);
        mediaPreview.setOrientation(LinearLayout.VERTICAL);
        content.addView(mediaPreview);
        refreshMediaPreview();

        content.addView(label("Perfiles de apariencia", 19, true));
        content.addView(label("Importar y exportar configura únicamente colores y estilos. " +
            "Los archivos privados y las referencias a medios no se exportan.", 13, false));
        addButton(content, "Exportar perfil JSON", v -> launchExport());
        addButton(content, "Importar perfil JSON", v -> launchImport());

        Button reset = new Button(this);
        reset.setAllCaps(false);
        reset.setText("Restablecer apariencia original");
        reset.setOnClickListener(view -> new AlertDialog.Builder(this)
            .setTitle("Restablecer apariencia")
            .setMessage("Se borrarán únicamente los colores y estilos del personalizador. " +
                "El tema, AMOLED, la grabación y DroidGuard permanecerán intactos.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Restablecer", (dialog, which) -> {
                MediaThemeEngine.clearMedia(this);
                VisualCustomizer.reset(this);
                profileSpinner.setSelection(0);
                styleSpinner.setSelection(0);
                accentSpinner.setSelection(0);
                if (callStyleSpinner != null) callStyleSpinner.setSelection(0);
                if (callAccentSpinner != null) callAccentSpinner.setSelection(0);
                if (callBackgroundSpinner != null) callBackgroundSpinner.setSelection(0);
                if (inCallEnabledSwitch != null) inCallEnabledSwitch.setChecked(false);
                darkOnlySwitch.setChecked(true);
                refreshPreview();
            }).show());
        content.addView(reset);

        Button finish = new Button(this);
        finish.setAllCaps(false);
        finish.setText("Guardar y volver");
        finish.setOnClickListener(view -> finish());
        content.addView(finish);

        refreshPreview();
    }

    private int dp(float size) { return VisualCustomizer.dp(this, size); }

    private TextView label(String text, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(foreground);
        view.setTextSize(sp);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(13), 0, dp(9));
        return view;
    }

    private Spinner selector(LinearLayout parent, String caption, String[] labels,
                             String[] values, String preferenceKey, String fallback) {
        parent.addView(label(caption, 15, true));
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        String saved = preferences.getString(preferenceKey, fallback);
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(saved)) selected = i;
        }
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                preferences.edit().putString(preferenceKey, values[i]).apply();
                refreshPreview();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        parent.addView(spinner, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        return spinner;
    }

    private void refreshPreview() {
        if (grid == null || previewCall == null) return;
        for (int row = grid.getChildCount() - 1; row >= 0; row--) {
            if (grid.getChildAt(row) != previewCall) grid.removeViewAt(row);
        }
        String profile = preferences.getString(VisualCustomizer.PROFILE, "system");
        String style = preferences.getString(VisualCustomizer.STYLE, "filled");
        boolean onlyNight = preferences.getBoolean(VisualCustomizer.DARK_ONLY, true);
        boolean active = !"system".equals(profile) && (!onlyNight || VisualCustomizer.isNight(this));
        for (int row = 0; row < 4; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 3; col++) {
                int position = row * 3 + col;
                final String symbol = VisualCustomizer.KEYS[position];
                TextView key = new TextView(this);
                key.setGravity(Gravity.CENTER);
                key.setText(symbol);
                key.setTextSize(25);
                int color = VisualCustomizer.colorFor(preferences, profile, position);
                if (!active) {
                    key.setTextColor(Color.WHITE);
                } else if ("numbers".equals(style)) {
                    key.setTextColor("amoled".equals(profile) ? Color.WHITE : color);
                } else {
                    key.setTextColor("outline".equals(style) ?
                        ("amoled".equals(profile) ? Color.WHITE : color) :
                        VisualCustomizer.contrastText(color));
                    key.setBackground(VisualCustomizer.makeBackground(this,
                        "amoled".equals(profile) && "outline".equals(style)
                            ? Color.WHITE : color, style, true));
                }
                key.setOnClickListener(view -> openColorPicker(symbol));
                LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, dp(64), 1f);
                cell.setMargins(dp(4), dp(4), dp(4), dp(4));
                line.addView(key, cell);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            grid.addView(line, grid.getChildCount() - 1, params);
        }
        String accent = preferences.getString(VisualCustomizer.ACCENT, "system");
        int callColor = (!onlyNight || VisualCustomizer.isNight(this)) && !"system".equals(accent)
            ? VisualCustomizer.accentColor(accent) : 0xff58b877;
        previewCall.setBackground(VisualCustomizer.makeBackground(this, callColor, "filled", true));
        previewCall.setTextColor(VisualCustomizer.contrastText(callColor));
        if (mediaPreview != null) refreshMediaPreview();
    }

    private void openColorPicker(String symbol) {
        final String[] choices = new String[COLOR_LABELS.length + 1];
        System.arraycopy(COLOR_LABELS, 0, choices, 0, COLOR_LABELS.length);
        choices[COLOR_LABELS.length] = "Introducir color hexadecimal (#RRGGBB)";
        new AlertDialog.Builder(this)
            .setTitle("Color de tecla " + symbol)
            .setItems(choices, (dialog, selected) -> {
                if (selected == COLOR_LABELS.length) {
                    showHexEditor(symbol);
                } else {
                    saveKeyColor(symbol, VisualCustomizer.hex(COLOR_VALUES[selected]));
                }
            }).show();
    }

    private void showHexEditor(String symbol) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        String existing = preferences.getString(VisualCustomizer.PREFIX + "key_" + symbol, "#FFB385");
        input.setText(existing);
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(20), dp(6), dp(20), dp(4));
        holder.addView(input, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Color hexadecimal · " + symbol)
            .setView(holder)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aplicar", null)
            .create();
        dialog.setOnShowListener(ignore -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(view -> {
                String value = input.getText().toString().trim();
                try {
                    VisualCustomizer.parseOpaqueColor(value);
                    saveKeyColor(symbol, value.toUpperCase(java.util.Locale.ROOT));
                    dialog.dismiss();
                } catch (IllegalArgumentException invalid) {
                    input.setError("Usa exactamente #RRGGBB, por ejemplo #FFB385");
                }
            }));
        dialog.show();
    }

    private void saveKeyColor(String symbol, String hex) {
        preferences.edit().putString(VisualCustomizer.PREFIX + "key_" + symbol, hex).apply();
        preferences.edit().putString(VisualCustomizer.PROFILE, "custom").apply();
        profileSpinner.setSelection(PROFILE_VALUES.length - 1);
        refreshPreview();
        Toast.makeText(this, "Color guardado para " + symbol, Toast.LENGTH_SHORT).show();
    }

    private void addButton(LinearLayout parent, String text, View.OnClickListener callback) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setOnClickListener(callback);
        parent.addView(button);
    }

    private void refreshMediaPreview() {
        if (mediaPreview == null) return;
        if (playingPreview != null) {
            playingPreview.stopPlayback();
            playingPreview = null;
        }
        mediaPreview.removeAllViews();
        String backgroundMode = PreferenceRepository.get(this, PreferenceRepository.CALL_BACKGROUND, "original");
        if ("cover".equals(backgroundMode)) {
            ImageView image = MediaThemeEngine.coverPreview(this);
            if (image == null) {
                mediaPreview.addView(label("Sin portada accesible; selecciona una imagen.", 13, false));
            } else {
                mediaPreview.addView(image, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));
            }
        } else if ("video".equals(backgroundMode)) {
            VideoView video = MediaThemeEngine.mutedVideoPreview(this);
            if (video == null) {
                mediaPreview.addView(label("Sin video accesible; selecciona un video.", 13, false));
            } else {
                playingPreview = video;
                mediaPreview.addView(video, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));
            }
        }
        String tone = PreferenceRepository.get(this, PreferenceRepository.RINGTONE_URI, "");
        if (!tone.isEmpty()) {
            mediaPreview.addView(label("Audio seleccionado para un futuro tono de llamada. " +
                "El tono del sistema permanece sin cambios.", 13, false));
        }
    }

    private void launchExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, "googlephone-visual-theme.json");
        startActivityForResult(intent, EXPORT_JSON);
    }

    private void launchImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, IMPORT_JSON);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        super.onActivityResult(requestCode, resultCode, result);
        if (resultCode != RESULT_OK || result == null || result.getData() == null) return;
        try {
            if (requestCode == MediaThemeEngine.PICK_COVER ||
                requestCode == MediaThemeEngine.PICK_VIDEO ||
                requestCode == MediaThemeEngine.PICK_TONE) {
                if (!MediaThemeEngine.accept(this, requestCode, result)) {
                    Toast.makeText(this, "El archivo no tiene formato o permisos compatibles", Toast.LENGTH_LONG).show();
                    return;
                }
                if (requestCode == MediaThemeEngine.PICK_COVER) {
                    preferences.edit().putString(PreferenceRepository.CALL_BACKGROUND, "cover").apply();
                    if (callBackgroundSpinner != null) callBackgroundSpinner.setSelection(1);
                } else if (requestCode == MediaThemeEngine.PICK_VIDEO) {
                    preferences.edit().putString(PreferenceRepository.CALL_BACKGROUND, "video").apply();
                    if (callBackgroundSpinner != null) callBackgroundSpinner.setSelection(2);
                }
                refreshMediaPreview();
                Toast.makeText(this, "Medio seleccionado y guardado", Toast.LENGTH_SHORT).show();
            } else if (requestCode == EXPORT_JSON) {
                byte[] data = PreferenceRepository.exportJson(this).getBytes(StandardCharsets.UTF_8);
                try (OutputStream stream = getContentResolver().openOutputStream(result.getData(), "wt")) {
                    if (stream == null) throw new java.io.IOException("No se puede abrir destino");
                    stream.write(data);
                }
                Toast.makeText(this, "Perfil exportado", Toast.LENGTH_SHORT).show();
            } else if (requestCode == IMPORT_JSON) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream stream = getContentResolver().openInputStream(result.getData())) {
                    if (stream == null) throw new java.io.IOException("Archivo inaccesible");
                    byte[] buffer = new byte[4096];
                    int size;
                    while ((size = stream.read(buffer)) != -1) {
                        if (bytes.size() + size > 32768) throw new java.io.IOException("Perfil demasiado grande");
                        bytes.write(buffer, 0, size);
                    }
                }
                PreferenceRepository.importJson(this, bytes.toString("UTF-8"));
                DialpadStyler.invalidate();
                recreate();
                Toast.makeText(this, "Perfil importado", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception error) {
            Toast.makeText(this, "No se aplicó el cambio: " + error.getClass().getSimpleName(),
                Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        if (playingPreview != null) playingPreview.stopPlayback();
        if (playingTone != null) { playingTone.release(); playingTone = null; }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPreview != null) refreshMediaPreview();
    }
}
