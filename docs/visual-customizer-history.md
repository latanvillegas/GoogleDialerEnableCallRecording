# Historial de llamadas — Google Phone 241

## Estado: coloración de filas reales integrada (experimental)

La auditoría del usuario `auditoria-historial-googlephone-241.zip` identifica:

- `Lkit;` — adaptador, identificado como `com/android/dialer/calllog/ui/CallLogAdapter`.
- `Lkit;->E(Laegg;Lkaw;Llb;I)V` — enlaza una fila de conversación; 16 registros, sin try blocks, un retorno.
- `Lkjq;` — ViewHolder `ConversationHistoryViewHolder`.
- `Lkjq;->E()Lkgn;` — modelo principal de la fila.
- `Lkgn;->q:I` — tipo de llamada: la lógica nativa distingue `3` como perdida y `5` como rechazada.
- `Llb;->a:Landroid/view/View;` — vista raíz del ViewHolder.
- Recursos `id/call_type_icon=0x7f0b0181`, `id/secondary_text=0x7f0b062b`.

El parche se limita a Google Phone `241.0.991235506` / `20129558`. Antes de cada bind
se restaura la tinta y el texto originales de una fila reciclada. Después del bind,
el hook lee el tipo desde el modelo nativo y aplica el color mediante
`CallHistoryBridge` y `CallHistoryRenderer`.

## Estados de color

- Tipo 1, entrante contestada: menta.
- Tipo 2, saliente: azul.
- Tipo 3, perdida: coral.
- Tipo 5, rechazada: violeta.
- Tipo 6, bloqueada: gris.
- Tipos desconocidos: sin modificación.
- Spam: la paleta y la vista previa están preparadas, **pero el icono de spam
  no se colorea en filas reales** hasta localizar un campo verificado que
  distinga la marca de spam del tipo de llamada.

El modelo de agrupación usa el evento principal que Google Phone ya entrega
a su propio ViewHolder. No se reordena el grupo ni se interpretan nombres,
números, descripciones, avatares o flechas para inferir un tipo.

## Preferencias y garantías

`CallHistorySettingsPanel` incorpora colores HEX, ubicación del color y
vista previa. El cambio real permanece opt-in (desactivado por defecto);
también respeta la preferencia de personalización exclusiva del modo oscuro.

La restauración nativa ocurre antes de reutilizar filas para evitar fugas de
tinta entre contactos, y el renderizador nunca cambia listeners ni controles
de llamada. Si el hook o la vista difieren del contrato auditado, el parche
aborta sin aplicar una mutación parcial.

## Funciones pendientes

- **Clasificación visual explícita de spam**: se requiere la definición DEX
  del veredicto de spam que realmente consume el modelo.
- **Filtro de historial real**: exige verificar el repositorio y el contrato de
  carga/paginación; el filtro existente afecta solo la vista previa.
- **Estadísticas de llamadas reales**: exigen datos de eventos verificados,
  no solo filas cargadas u observadas. `CallHistoryStats` aún no está
  conectado a la fuente nativa.

No se modifican grabación, DroidGuard ni Pure AMOLED. No se accede a contactos,
registros de llamadas ni bases de datos del dispositivo durante el análisis.
Las pruebas CI verifican construcción, **no sustituyen la prueba visual en
el teléfono con Android 16**.

## Auditoría de modelos de filtros (Google Phone 241)

El ZIP del dispositivo `auditoria-modelos-historial-241.zip` confirmó las 19
clases solicitadas y el SHA-256 de la APK analizada:
`083e0fced1b4c61520a719f627fed97225edcd7f5bb8dadc49ef5ac015c2c051`.

- El enum nativo `Lkdx;` contiene exactamente `ALL`, `MISSED`,
  `CONTACTS`, `NON_SPAM`, `SPAM`, `CALL_RECORDING` y `CALL_NOTES`.
- `Lkei;->a(Lkdx;Laodk;)Ljava/lang/Object;` interviene en la selección
  del filtro, mediante coroutine.
- `Lkzk;->j(Lkdx;)Lkdt;` resuelve el filtro desde un mapa o falla si
  no existe; no debe invocarse con tipos inventados.
- El reporte no prueba un campo de veredicto `SPAM` por cada fila, ni
  expone una API para insertar filtros `INCOMING` o `OUTGOING` en ese enum.

Por esa razón el cuarto parche **no altera la consulta ni los filtros
nativos de Google Phone**. Sigue sin intentar identificar spam por el número
o por la etiqueta visible.

## Explorador local de historial y estadísticas reales

Se agrega `CallHistoryExplorer` dentro de `Personalización visual →
Historial de llamadas`. Al elegir «Abrir historial filtrado y estadísticas
reales», utiliza el contrato público de Android
`android.provider.CallLog.Calls.CONTENT_URI` exclusivamente en lectura:

- Solo se ejecuta tras una acción explícita del usuario y con
  `READ_CALL_LOG` ya autorizado. No solicita nuevos permisos.
- Usa únicamente `TYPE`, `DATE`, `NUMBER` y `CACHED_NAME`.
- Filtra las categorías documentadas `INCOMING`, `OUTGOING`, `MISSED`,
  `REJECTED` y `BLOCKED`, además de «Todas».
- Calcula estadísticas reales del máximo de **5.000 eventos recientes**,
  e indica si existen más eventos no examinados. Muestra hasta **80 filas**
  de la categoría seleccionada, manteniendo el orden de fecha descendente.
- No combina entradas en grupos. Sus recuentos pueden diferir de los
  grupos/conversaciones que muestra Google Phone en su pantalla principal.
- Los nombres o números se conservan solamente en la memoria de este diálogo
  durante su uso; no se persisten, exportan ni envían a la red.
- La lectura se hace en un hilo de trabajo y se cancela al cerrar el diálogo.
  Los errores de permiso o proveedor muestran un mensaje, sin modificar datos.
- Spam se mantiene sin clasificar en este explorador hasta verificar el
  veredicto explícito del modelo Google Phone.

Esta funcionalidad cumple el filtrado de eventos **en una vista separada y
de solo lectura**, no el filtrado del RecyclerView principal. Los colores
sobre las filas del historial original se mantienen en el hook ya auditado
`Lkit;->E(Laegg;Lkaw;Llb;I)V`, opt-in, sin modificaciones nuevas.

### Validación pendiente

CI compila el módulo Java y las pruebas del parche; la consulta local requiere
prueba física de compatibilidad con el permiso `READ_CALL_LOG` del clon Morphe
en Android 16. Un APK sin ese permiso seguirá mostrando un aviso y no consultará
registros. Para habilitar el spam de Google Phone dentro del adaptador nativo
aún necesitamos verificar el campo concreto que representa ese veredicto.

## Indicadores vectoriales y etiquetas — Google Phone 241

La inspección de `googlephone-241-amoled-resources.zip` verificó que tanto
`res/layout/conversation_history_entry.xml` como su variante `_flippable`
usan un `ImageView` con ID `call_type_icon`, con dimensiones originales de
16×16 dp y 12×12 dp respectivamente. El botón de la derecha usa otro ID:
`call_button`, que **no se modifica**.

El motor `CallHistoryIconDrawable` genera indicadores vectoriales nativos
de Android (Canvas/Path, sin bibliotecas externas ni recursos inventados),
con tres estilos configurables:

- **Flechas originales:** no se sustituye el drawable nativo; solamente se
  aplica el tinte del estado, como antes.
- **Iconos telefónicos modernos:** teléfono con marca direccional para
  recibidas/realizadas, cruz para perdidas, barra diagonal de rechazo y
  símbolo de bloqueo. Tamaños adaptativos: 20 dp normal, 17 dp compacto.
- **Iconos dentro de círculo de color:** el mismo indicador vectorial con
  un círculo translúcido y área de 22 dp normal o 18 dp compacto.

Los tipos emplean `CallTypeResolver`, ya conectado al binder `Lkit.E`.
Ningún texto ni número de llamante se emplea para clasificar el tipo.
El indicador de **spam permanece en la vista previa**, sin aplicación real
hasta verificar la bandera explícita del modelo nativo.

El `CallHistoryRenderer` almacena el drawable, tinte, tipo de escala,
dimensiones de la vista, descripción accesible y propiedades originales
del `TextView`. Antes de reutilizar una fila, restaura estos atributos.
Los cambios se revierten también si se produce una excepción durante el
renderizado, preservando la UI nativa.

Las franjas usan `GradientDrawable` redondeado de 3 dp, dentro de la fila
con 8 dp de separación horizontal y 5 dp vertical; no interceptan toques.
La opción **mostrar etiquetas** (desactivada por defecto) incorpora una
primera línea con el tipo de llamada y una segunda con el texto nativo,
para preservar fecha y metadatos. No se modifican los nombres.

**Agrupaciones:** el tipo de cada fila sigue siendo el tipo del evento
que Google Phone ya expone mediante `Lkgn.q`, sin inferir la composición
histórica del grupo, que no se encuentra en el contrato auditado. El
contador nativo de llamadas agrupadas permanece inalterado.

En la prueba física hay que verificar las dos variantes de fila, tamaño y
alineación del icono, salto de línea opcional, reciclado del ViewHolder,
contraste AMOLED, y cambios de estilo sin afectar el botón `call_button`.
El éxito de compilación no demuestra por sí solo la corrección visual.

El ajuste de tamaño se fundamenta en las dimensiones 16dp/12dp de los dos layouts verificados, para preservar la anchura disponible del texto secundario y evitar recortes adicionales.
