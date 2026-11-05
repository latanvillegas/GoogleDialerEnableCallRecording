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
