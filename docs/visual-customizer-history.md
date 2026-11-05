# Call History Colorization — Google Phone 241 (staged)

## Verified source evidence

The user's resource archive of Google Phone 241.0.991235506 contains:

| Resource | Original resource ID |
| --- | --- |
| `layout/conversation_history_entry` | `0x7f0e00b5` |
| `layout/conversation_history_entry_flippable` | `0x7f0e00b6` |
| `layout/call_log_fragment_flippable` | `0x7f0e0076` |
| `layout/call_log_fragment_flex_two_column` | `0x7f0e0075` |
| `id/call_log_recycler_view` | `0x7f0b0155` |
| `id/call_type_icon` | `0x7f0b0181` |
| `id/secondary_text` | `0x7f0b062b` |
| `id/primary_text` | `0x7f0b0576` |
| `id/missed_call_count` | `0x7f0b04b5` |

These resources establish row view IDs. They DO NOT establish which obfuscated
adapter method binds the model, nor the location of its spam flag.

## Implemented in the optional Visual Customizer extension

- `CallTypeResolver`: public Android CallLog.Calls types, explicit spam flag,
  unknown types preserve native rendering. Grouped row uses newest timestamp.
- `CallColorPalette`: six customizable, independently persisted HEX values
  and three placement choices; opt-in disabled by default.
- `CallHistoryRenderer`: restores native icon and text styling on reuse,
  colors only the verified icon/secondary-text IDs, optional side stripe
  uses ViewOverlay and does not intercept touches.
- `CallHistoryStats`: counts typed events in memory, with no persistence
  of identities, phone numbers or individual call records.
- `CallHistorySettingsPanel`: settings and a representative preview, with
  a prominent warning that live history styling and filtering await DEX verification.
- `PreferenceRepository`: recognizes and imports/exports history appearance
  settings in schema 1; the existing global reset clears those settings.

## Explicitly not activated yet

- No injection into unknown adapter or group binding methods.
- No live `RecyclerView` filtering or modification of the original call query.
- No dashboard showing statistics derived from real calls.
- No assumption that an unknown caller or a missed call is spam.

The above functions require a new on-device read-only DEX audit using
`inspeccionar_historial_googlephone_241.py` with the user's existing Termux
and Apktool. Inspect *only bytecode* of the patched APK; the report excludes
device contacts, call logs, and application databases.

Required contracts before wiring:

1. Exact row ViewHolder and native row-bind method (signature, register count,
   try blocks and instruction positions).
2. Exact source field or accessor of the most recent event type within grouped
   rows, including type mapping and ordering.
3. Explicit Google Phone spam verdict and blocked/rejected states; never infer
   from avatar, text, direction arrow or caller number.
4. Row reuse/rebinding lifecycle to restore native colors deterministically.
5. Existing filter repository/query contract, and its behavior under pagination.

## Rollout and safety

- The 3 existing patches `Enable call recording`,
  `Handle rejected DroidGuard connection`, `Pure AMOLED - System default`
  are not modified by this staged implementation.
- The InCall and Dialpad hooks in the fourth patch are preserved.
- No changes to call answering, rejecting, placing, blocking, recording, or
  Telecom APIs.
- A successful CI build does **not** constitute proof of history rendering.
- Test on physical HyperOS/Android 16 only after an audited binding is added.
