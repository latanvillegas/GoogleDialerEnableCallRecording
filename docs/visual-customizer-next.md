# Dialer Visual Customizer – modularización y controles de llamadas

## Incorporado y enlazado al hook funcional del dialpad

* `ThemeEngine`: paletas de 12 teclas. Se conservan sistema, AMOLED, Pastel Rainbow, Neon AMOLED y personalizado, y se añaden Ocean AMOLED, Sunset Gradient, Emerald Glass y Monochrome Pro.
* `DialpadStyler`: preserva `VisualCustomizer.apply(View)` y los IDs verificados de 0–9, asterisco y almohadilla. No modifica botones táctiles, listeners ni audio.
* `PreferenceRepository`: preferencias privadas, validador `#RRGGBB`, JSON esquema 1 con importación transaccional, exportación que excluye URI de medios y datos personales, restablecimiento limitado al prefijo del personalizador.
* `MediaThemeEngine`: selector Android `ACTION_OPEN_DOCUMENT`, permisos de lectura persistentes para archivo de imagen, video o audio, vista previa de portada, video silencioso y escucha voluntaria de audio, liberación de recursos cuando la actividad deja de estar visible. **No altera el timbre del sistema**.
* `CallScreenStyler`: operaciones de color sobre los ID `btn_mute`, `btn_audio_route`, `btn_dialpad`, dejando `btn_end_call` nativo. **No se instala el hook InCallService** en esta etapa.

## Bloqueo técnico pendiente de evidencia DEX

La APK contiene varias rutas de llamada distintas, entre ellas:

* `com.android.dialer.incall.activity.ui.InCallActivity`
* `com.android.incallui.InCallServiceImpl`
* `res/layout/fragment_incoming_call.xml`
* `res/layout/legacy_fragment_incoming_call.xml`
* `res/layout/xatu_in_call_fragment.xml`

Por tanto no se puede asumir que una modificación de `fragment_incoming_call.xml` afecte a la pantalla activa, ni instalar un hook en un método cuyo contrato de registros/desvíos no está auditado. El recolector `inspeccionar_incall_googlephone_241.py` obtiene únicamente los smali requeridos.

**Aún no conectados a llamadas reales:** fondos de contacto, videotonos, controles durante la llamada y tonos por contacto. Las pantallas de preferencias son operativas, pero indican claramente que esas opciones están en vista previa/preparación. No se introducen permisos de lectura de contactos, modificación de ajustes del sistema ni controles de telefonía.

## Compatibilidad

Parche opt-in `Dialer Visual Customizer`, Google Phone `241.0.991235506`, `versionCode 20129558`. **No modificar** `PureAmoledSystemDefaultPatch.kt`, `EnableCallRecordingPatch.kt` ni `DroidGuardConnectionPatch.kt`. El tema nativo Claro/Oscuro/Sistema no cambia.

## Google Phone 241 – auditoría InCallActivity aplicada

Datos identificados en `auditoria-incall-googlephone-241.zip` (7 DEX):

* `Lcom/android/dialer/incall/activity/ui/InCallActivity;->onPostResume()V`:
  una devolución `return-void`, un `invoke-super` de `Loqs;`, 2 registros.
* `Lcom/android/dialer/incall/activity/ui/InCallActivity;->onPause()V`:
  una devolución, `invoke-super` de `Loqs;`, 7 registros.
* `res/layout/fragment_incoming_call.xml` y `legacy_fragment_incoming_call.xml`:
  contenedores hermanos `background_fragment_container` y `foreground_container`,
  con `answer_method_container` dentro del segundo. Las superficies de preview
  de videollamada se distinguen por sus IDs y se excluyen de los medios personalizados.
* `res/layout/xatu_in_call_fragment.xml` identifica `btn_mute`, `btn_audio_route`,
  `btn_dialpad` y `btn_end_call`. Este último permanece en el estilo nativo.

Se instala un **hook exclusivamente en la actividad UI** al completar `onPostResume`
y al comenzar `onPause`. `InCallThemeHooks` observa cambios de layouts de fragmentos,
inyecta solo un fondo detrás de los controles de llamadas de audio entrantes,
colorea únicamente tres botones verificados de la pantalla Xatu y desmonta todo
al pausar. El video es silencioso y libera su reproductor. Las portadas se
resuelven en un hilo de decodificación separado, con muestreo de hasta
1080x1920 para no bloquear el flujo de contestación.

La nueva opción **«Aplicar personalización durante llamadas reales (experimental)»**
se mantiene desactivada por defecto. También respeta «solo en modo oscuro».
No existe un hook a `InCallServiceImpl`, a la gestión Telecom, a la cámara ni a
las decisiones Contestar/Rechazar/Colgar. La selección de audio sigue siendo
**vista previa** y no modifica el timbre de Android. No se implementa la
asignación por contacto hasta validar la resolución de identidad de llamada.

**Estado de validación:** los puntos de entrada y layouts se verificaron en el
DEX proporcionado, pero los controles Xatu, cover y video requieren una prueba
física de recepción de llamada de audio. El éxito de CI no sustituye esta prueba.
