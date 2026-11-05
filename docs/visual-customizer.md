# Dialer Visual Customizer — Google Phone 241

This optional patch targets the inspected original APK `com.google.android.dialer`
`241.0.991235506`, versionCode `20129558` (single APK). It does not modify or
depend on call recording, DroidGuard, or Pure AMOLED patches.

## Entry point and DEX hooks

- Settings entry: `res/xml/display_options_settings.xml`, the verified
  Appearance category inflated by `Lmjz;->aU`.
- Activity: `local.dialer.visual.VisualCustomizerActivity`, registered in the
  original manifest. The AndroidX Preference opens it using the unique action
  `local.dialer.visual.CONFIGURE`.
- Runtime extension: `extensions/visual-customizer.mpe`, automatically compiled
  from `extensions/visual-customizer` by the Morphe Gradle extension plugin.
- Dialpad view: `com.google.android.libraries.communications.ux.views.dialpad.Dialpad`.
  Its inspected `onLayout(ZIIII)V` has zero local registers and delegates to
  `FrameLayout.onLayout`. The hook uses only `p0` to call the extension.
- A tiny `onWindowFocusChanged(boolean)` override refreshes the colors when the
  settings Activity returns and Android does not perform a new layout pass.

## Settings

- Profiles: Original/system, AMOLED classic, Pastel Rainbow, Neon AMOLED,
  Custom. Original/system is the default and restores the captured original
  per-key background/text colors.
- Key modes: Filled, Outline, Numbers only.
- Each of the twelve keys can receive a chosen predefined color or a strict
  six-digit opaque `#RRGGBB` custom color. Picking a color selects Custom.
- Calls: original background or a user-selected accent, applied only to the
  four verified voice/RTT extended call-button IDs, not the chat button.
- Night-only switch (enabled by default) keeps the native light theme intact.
- Preview is rendered directly in the settings Activity; reset deletes only
  `local_dialer_visual_*` preferences.
- Native "Light / Dark / System default" selector is never intercepted.

## Resource verification

The ID order was checked against the user's `res/values/public.xml` and the
Dex static array in `Dialpad.<clinit>`: `one`, `two`, `three`, `four`, `five`,
`six`, `seven`, `eight`, `nine`, `star`, `zero`, `pound`. Number and secondary
labels use `dialpad_key_number` / `dialpad_key_letters` /
`dialpad_key_secondary_letters`. The call-button IDs were likewise checked
against `res/layout/dialpad_call_buttons.xml`.

## Validation checklist

1. CI: `:patches:test`, Java extension compilation, DEX packaging and release.
2. Import the released `.mpp` in Morphe Manager and apply the four selected
   patches to the **original** Google Phone 241 APK. Do not patch an already
   modified `.morphe` clone as the source.
3. Open Options > Display options > Visual customization.
4. Select Pastel Rainbow, switch among Filled / Outline / Numbers, tap a key to
   change its color, and save a `#RRGGBB` custom value.
5. Return to the dialpad; inspect all 12 keys, voice call button, accessibility
   labels, ripple, orientation changes, and rapid tab switching.
6. Switch Android system from dark to light: original light mode must remain
   unchanged when night-only is enabled.
7. Reset appearance: original key background/text and button tint must return.
8. Check recording/DroidGuard behavior separately; CI does not prove that real
   calls, in-call audio, or the Android 16 theme work on-device.

## Security and privacy

No network, phone, contacts or storage permissions are requested. The editor
uses private Android SharedPreferences of the app. The Activity is exported
only because the existing AndroidX preference XML opens it with an action-only
Intent, and the intent filter exposes no user data. A future bytecode-level
non-exported explicit Intent handler may narrow this entry point.
