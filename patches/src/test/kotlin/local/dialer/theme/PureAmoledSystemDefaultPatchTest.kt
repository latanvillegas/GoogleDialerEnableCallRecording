package local.dialer.theme

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PureAmoledSystemDefaultPatchTest {
    private fun xml(content: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))

    @Test fun `recolors dark surface aliases but not text or accents`() {
        val doc = xml(
            """
            <resources>
                <color name="gm3_sys_color_dark_surface">@android:color/system_neutral1_900</color>
                <color name="dialer_background">#FF121212</color>
                <color name="gm3_sys_color_dark_on_surface">#FFFFFFFF</color>
                <color name="dialer_background_tint">#FF448AFF</color>
                <color name="primary_accent">#FF448AFF</color>
            </resources>
            """.trimIndent(),
        )
        assertEquals(2, applyPureAmoledToNightDocument(doc, styles = false))
        val colors = doc.getElementsByTagName("color")
        assertEquals("#FF000000", colors.item(0).textContent)
        assertEquals("#FF000000", colors.item(1).textContent)
        assertEquals("#FFFFFFFF", colors.item(2).textContent)
        assertEquals("#FF448AFF", colors.item(3).textContent)
        assertEquals("#FF448AFF", colors.item(4).textContent)
    }

    @Test fun `preserves functional colors from inspected Google Phone 241 APK`() {
        val protectedColors = listOf(
            "bam_chronometer_background_color",
            "banner_error_background_color",
            "banner_promo_background_color",
            "banner_success_background_color",
            "banner_warn_background_color",
            "contact_checker_card_background_color",
            "preview_branding_background",
            "preview_label_background_color",
            "settings_banner_background_color",
            "sud_accessibility_button_background_color",
            "survey_button_background_color",
            "sud_system_button_surface",
        )
        protectedColors.forEach { name ->
            assertFalse(isAmoledSurfaceName(name), "Must preserve functional color: $name")
        }

        val neutral = listOf(
            "bam_center_fragment_background",
            "settingslib_materialColorSurfaceContainer",
            "settingslib_materialColorSurfaceContainerHigh",
            "settingslib_materialColorSurfaceContainerHighest",
            "settingslib_materialColorSurfaceVariant",
            "settingslib_colorSurfaceVariant",
            "settingslib_dialog_background",
            "settingslib_surface_dark",
            "sud_color_surface_bright",
            "sud_color_surface_container",
            "sud_color_surface_container_high",
            "sud_system_background_surface",
            "sud_system_surface",
            "google_marterial_dialog_background_color",
            "survey_surface_color",
        )
        neutral.forEach { name ->
            assertTrue(isAmoledSurfaceName(name), "Must allow neutral surface: $name")
        }

        val doc = xml(
            """
            <resources>
                <color name="banner_error_background_color">@color/google_red900</color>
                <color name="banner_success_background_color">@color/google_green900</color>
                <color name="settingslib_materialColorSurfaceContainer">#1f1f1f</color>
                <color name="sud_color_surface_container">@color/sud_system_surface_container_dark</color>
                <color name="sud_accessibility_button_background_color">@color/sud_system_secondary_container_dark</color>
                <color name="survey_button_background_color">@color/google_grey200</color>
            </resources>
            """.trimIndent(),
        )
        assertEquals(2, applyPureAmoledToNightDocument(doc, styles = false))
        val colors = doc.getElementsByTagName("color")
        assertEquals("@color/google_red900", colors.item(0).textContent)
        assertEquals("@color/google_green900", colors.item(1).textContent)
        assertEquals("#FF000000", colors.item(2).textContent)
        assertEquals("#FF000000", colors.item(3).textContent)
        assertEquals("@color/sud_system_secondary_container_dark", colors.item(4).textContent)
        assertEquals("@color/google_grey200", colors.item(5).textContent)
    }

    @Test fun `recolors inspected Google Phone 241 dark aliases in default and API values`() {
        val doc = xml(
            """
            <resources>
                <color name="gm3_sys_color_dynamic_dark_background">@color/gm3_ref_palette_dynamic_neutral_variant6</color>
                <color name="gm3_sys_color_dynamic_dark_surface_container_high">@color/gm3_ref_palette_dynamic_neutral_variant17</color>
                <color name="gm3_sys_color_dynamic_dark_surface_dim">@color/gm3_ref_palette_dynamic_neutral_variant6</color>
                <color name="gm3_sys_color_dynamic_dark_on_surface">@color/gm3_ref_palette_dynamic_neutral90</color>
                <color name="gm3_sys_color_dynamic_dark_inverse_surface">@color/gm3_ref_palette_dynamic_neutral90</color>
                <color name="gm3_sys_color_dynamic_light_background">@color/gm3_ref_palette_dynamic_neutral_variant98</color>
                <color name="m3_sys_color_dynamic_dark_surface">@color/m3_ref_palette_dynamic_neutral_variant6</color>
                <color name="google_dark_default_color_background">#FF191919</color>
                <color name="gm3_dark_default_color_surface_container">#FF222222</color>
                <color name="background_material_dark">#FF101010</color>
                <color name="google_marterial_color_surface_dark">@android:color/system_neutral1_800</color>
                <color name="google_marterial_color_surface_light">@android:color/system_neutral1_10</color>
                <color name="gm3_sys_color_dynamic_dark_surface_variant">@color/gm3_ref_palette_dynamic_neutral_variant30</color>
                <color name="gm3_sys_color_dynamic_dark_surface_tint">@color/gm3_ref_palette_dynamic_primary80</color>
                <color name="dialer_end_call_button_color">@color/google_red400</color>
            </resources>
            """.trimIndent(),
        )
        assertEquals(9, applyPureAmoledToExplicitDarkColors(doc))
        val colors = doc.getElementsByTagName("color")
        for (index in listOf(0, 1, 2, 6, 7, 8, 9, 10, 12)) {
            assertEquals("#FF000000", colors.item(index).textContent, "Dark background index $index")
        }
        assertEquals("@color/gm3_ref_palette_dynamic_neutral90", colors.item(3).textContent)
        assertEquals("@color/gm3_ref_palette_dynamic_neutral90", colors.item(4).textContent)
        assertEquals("@color/gm3_ref_palette_dynamic_neutral_variant98", colors.item(5).textContent)
        assertEquals("@android:color/system_neutral1_10", colors.item(11).textContent)
        assertEquals("@color/gm3_ref_palette_dynamic_primary80", colors.item(13).textContent)
        assertEquals("@color/google_red400", colors.item(14).textContent)
        assertEquals(0, applyPureAmoledToExplicitDarkColors(doc), "Must be idempotent")
    }

    @Test fun `dark palette selectors preserve system light aliases and interaction colors`() {
        val selected = listOf(
            "gm3_sys_color_dynamic_dark_background",
            "gm3_sys_color_dynamic_dark_surface_container",
            "m3_sys_color_dynamic_dark_surface",
            "gm3_dark_default_color_background",
            "google_dark_default_color_surface",
            "background_material_dark",
            "google_marterial_color_surface_dark",
        )
        selected.forEach { name ->
            assertTrue(isExplicitDarkAmoledColor(name), "Must recolor dark surface: $name")
        }

        val preserved = listOf(
            "gm3_sys_color_dynamic_light_background",
            "m3_sys_color_dynamic_light_surface",
            "google_default_color_background",
            "gm3_default_color_surface",
            "gm3_sys_color_dynamic_dark_on_surface",
            "gm3_sys_color_dynamic_dark_inverse_surface",
            "gm3_sys_color_dynamic_dark_surface_tint",
            "gm3_sys_color_dynamic_dark_on_surface_state_content",
            "dialer_call_green",
            "dialer_end_call_button_color",
            "banner_error_background_color",
        )
        preserved.forEach { name ->
            assertFalse(isExplicitDarkAmoledColor(name), "Must preserve color: $name")
        }
    }

    @Test fun `recolors night theme backgrounds without changing controls`() {
        val doc = xml(
            """
            <resources>
                <style name="Theme.Phone.Dark">
                    <item name="android:windowBackground">@color/dialer_background</item>
                    <item name="colorSurface">?attr/colorSurface</item>
                    <item name="colorOnSurface">#FFFFFFFF</item>
                    <item name="android:windowLightStatusBar">false</item>
                </style>
                <style name="Theme.Phone.Light">
                    <item name="android:windowBackground">#FFFFFFFF</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        assertEquals(2, applyPureAmoledToNightDocument(doc, styles = true))
        val items = doc.getElementsByTagName("item")
        assertEquals("#FF000000", items.item(0).textContent)
        assertEquals("#FF000000", items.item(1).textContent)
        assertEquals("#FFFFFFFF", items.item(2).textContent)
        assertEquals("false", items.item(3).textContent)
        assertEquals("#FFFFFFFF", items.item(4).textContent)
    }

    @Test fun `uses narrow semantic selectors`() {
        assertTrue(isAmoledSurfaceName("gm3_sys_color_dark_surface_container"))
        assertTrue(isAmoledSurfaceName("windowBackground"))
        assertFalse(isAmoledSurfaceName("gm3_sys_color_dark_on_surface"))
        assertFalse(isAmoledSurfaceName("surfaceTint"))
        assertFalse(isAmoledSurfaceName("text_background"))
        assertFalse(isAmoledSurfaceName("primary_accent"))
        assertTrue(isAmoledThemeItem("android:windowBackground"))
        assertFalse(isAmoledThemeItem("colorOnSurface"))
    }
}
