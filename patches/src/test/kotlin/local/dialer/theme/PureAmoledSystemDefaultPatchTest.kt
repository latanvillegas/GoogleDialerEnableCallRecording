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

    @Test fun `accepts only the two verified encodings of an expected theme attribute`() {
        assertTrue(matchesExpectedAmoledAttributeReference("?colorSurfaceContainerLow", "?colorSurfaceContainerLow"))
        assertTrue(matchesExpectedAmoledAttributeReference("?attr/colorSurfaceContainerLow", "?colorSurfaceContainerLow"))
        assertTrue(matchesExpectedAmoledAttributeReference("  ?attr/colorSecondaryContainer  ", "?colorSecondaryContainer"))
        assertFalse(matchesExpectedAmoledAttributeReference("?attr/colorSurfaceContainerHigh", "?colorSurfaceContainerLow"))
        assertFalse(matchesExpectedAmoledAttributeReference("?attr/colorPrimary", "?colorSecondaryContainer"))
        assertFalse(matchesExpectedAmoledAttributeReference("?android:attr/colorSurfaceContainerLow", "?colorSurfaceContainerLow"))
        assertFalse(matchesExpectedAmoledAttributeReference("?colorSurfaceContainerLow", "@color/unknown"))
    }

    @Test fun `Morphe normalized dark Dialer navigation attributes no longer abort patching`() {
        val doc = xml(
            """
            <resources>
                <style name="Base.Theme.GoogleMaterial3.Dialer.Dark.NoActionBar">
                    <item name="colorNavigationBar">?attr/colorSurfaceContainerLow</item>
                    <item name="android:navigationBarColor">?attr/colorNavigationBar</item>
                    <item name="colorOnSurface">#FFE8E8E8</item>
                </style>
                <style name="Base.Theme.GoogleMaterial3.Dialer.Light.NoActionBar">
                    <item name="colorNavigationBar">?attr/colorSurfaceContainerHigh</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        assertEquals(2, applyPureAmoledDarkDialerNavigation(doc))
        assertEquals(0, applyPureAmoledDarkDialerNavigation(doc))
        val styles = doc.getElementsByTagName("style")
        val dark = styles.item(0) as org.w3c.dom.Element
        val light = styles.item(1) as org.w3c.dom.Element
        val items = dark.getElementsByTagName("item")
        assertEquals("#FF000000", items.item(0).textContent)
        assertEquals("#FF000000", items.item(1).textContent)
        assertEquals("#FFE8E8E8", items.item(2).textContent)
        assertEquals("?attr/colorSurfaceContainerHigh", light.getElementsByTagName("item").item(0).textContent)
    }

    @Test fun `Morphe normalized widget references are accepted in night overlays`() {
        val base = xml(
            """
            <resources>
                <style name="Widget.Material3.NavigationView" parent="@style/Widget.MaterialComponents.NavigationView">
                    <item name="android:background">?attr/colorSurfaceContainerLow</item>
                    <item name="itemTextColor">@color/m3_navigation_item_text_color</item>
                </style>
                <style name="Widget.Material3Expressive.BottomNavigationView.ActiveIndicator" parent="@style/Widget.Material3.BottomNavigationView.ActiveIndicator">
                    <item name="android:height">@dimen/m3_comp_nav_bar_item_vertical_active_indicator_height</item>
                    <item name="android:color">?attr/colorSecondaryContainer</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        val night = xml("<resources/>")
        assertEquals(2, installPureAmoledNightWidgetOverrides(base, night))
        assertEquals(0, installPureAmoledNightWidgetOverrides(base, night))
        val nightStyles = night.getElementsByTagName("style")
        val drawer = nightStyles.item(0) as org.w3c.dom.Element
        val indicator = nightStyles.item(1) as org.w3c.dom.Element
        assertEquals("#FF000000", drawer.getElementsByTagName("item").item(0).textContent)
        assertEquals("@color/m3_navigation_item_text_color", drawer.getElementsByTagName("item").item(1).textContent)
        assertEquals("#FF262626", indicator.getElementsByTagName("item").item(1).textContent)

        val original = base.getElementsByTagName("style")
        assertEquals("?attr/colorSurfaceContainerLow", (original.item(0) as org.w3c.dom.Element).getElementsByTagName("item").item(0).textContent)
        assertEquals("?attr/colorSecondaryContainer", (original.item(1) as org.w3c.dom.Element).getElementsByTagName("item").item(1).textContent)
    }

    @Test fun `sets black bottom and system navigation in inspected dark Dialer theme only`() {
        val document = xml(
            """
            <resources>
                <style name="Base.Theme.GoogleMaterial3.Dialer.Dark.NoActionBar">
                    <item name="colorNavigationBar">?colorSurfaceContainerLow</item>
                    <item name="colorOnSurface">#FFE8E8E8</item>
                </style>
                <style name="Base.Theme.GoogleMaterial3.Dialer.Light.NoActionBar">
                    <item name="colorNavigationBar">?colorSurfaceContainerHigh</item>
                    <item name="colorOnSurface">#FF202020</item>
                </style>
            </resources>
            """.trimIndent(),
        )

        assertEquals(2, applyPureAmoledDarkDialerNavigation(document))
        assertEquals(0, applyPureAmoledDarkDialerNavigation(document))

        val dark = document.getElementsByTagName("style").item(0) as org.w3c.dom.Element
        val light = document.getElementsByTagName("style").item(1) as org.w3c.dom.Element
        assertTrue(dark.textContent.contains("#FF000000"))
        val items = dark.getElementsByTagName("item")
        assertEquals("#FF000000", items.item(0).textContent)
        assertEquals("#FFE8E8E8", items.item(1).textContent)
        assertEquals("#FF000000", items.item(2).textContent)

        assertEquals("?colorSurfaceContainerHigh", light.getElementsByTagName("item").item(0).textContent)
        assertEquals("#FF202020", light.getElementsByTagName("item").item(1).textContent)
    }

    @Test fun `night drawer and tab indicator override original material widget styles`() {
        val base = xml(
            """
            <resources>
                <style name="Widget.Material3.NavigationView" parent="@style/Widget.MaterialComponents.NavigationView">
                    <item name="android:background">?colorSurfaceContainerLow</item>
                    <item name="itemTextColor">@color/m3_navigation_item_text_color</item>
                </style>
                <style name="Widget.Material3Expressive.BottomNavigationView.ActiveIndicator" parent="@style/Widget.Material3.BottomNavigationView.ActiveIndicator">
                    <item name="android:height">@dimen/m3_comp_nav_bar_item_vertical_active_indicator_height</item>
                    <item name="android:color">?colorSecondaryContainer</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        val night = xml(
            """
            <resources>
                <style name="Theme.GoogleMaterial3.DayNight" parent="@style/Theme.GoogleMaterial3.Dark" />
            </resources>
            """.trimIndent(),
        )

        assertEquals(2, installPureAmoledNightWidgetOverrides(base, night))
        assertEquals(0, installPureAmoledNightWidgetOverrides(base, night))
        val nightWidgets = night.getElementsByTagName("style")
        val drawer = nightWidgets.item(1) as org.w3c.dom.Element
        val indicator = nightWidgets.item(2) as org.w3c.dom.Element
        assertEquals("Widget.Material3.NavigationView", drawer.getAttribute("name"))
        assertEquals("#FF000000", drawer.getElementsByTagName("item").item(0).textContent)
        assertEquals("@color/m3_navigation_item_text_color", drawer.getElementsByTagName("item").item(1).textContent)
        assertEquals("Widget.Material3Expressive.BottomNavigationView.ActiveIndicator", indicator.getAttribute("name"))
        assertEquals("#FF262626", indicator.getElementsByTagName("item").item(1).textContent)

        val sourceWidgets = base.getElementsByTagName("style")
        val sourceDrawer = sourceWidgets.item(0) as org.w3c.dom.Element
        val sourceIndicator = sourceWidgets.item(1) as org.w3c.dom.Element
        assertEquals("?colorSurfaceContainerLow", sourceDrawer.getElementsByTagName("item").item(0).textContent)
        assertEquals("?colorSecondaryContainer", sourceIndicator.getElementsByTagName("item").item(1).textContent)
    }

    @Test fun `dark Dialer theme disables tonal elevation overlay without changing light`() {
        val doc = xml(
            """
            <resources>
                <style name="Base.Theme.GoogleMaterial3.Dialer.Dark.NoActionBar">
                    <item name="colorNavigationBar">#FF000000</item>
                </style>
                <style name="Base.Theme.GoogleMaterial3.Dialer.Light.NoActionBar">
                    <item name="elevationOverlayEnabled">true</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        assertEquals(1, disablePureAmoledDarkTonalElevation(doc))
        assertEquals(0, disablePureAmoledDarkTonalElevation(doc))

        val styles = doc.getElementsByTagName("style")
        val dark = styles.item(0) as org.w3c.dom.Element
        val light = styles.item(1) as org.w3c.dom.Element
        val darkItems = dark.getElementsByTagName("item")
        assertEquals("elevationOverlayEnabled", (darkItems.item(1) as org.w3c.dom.Element).getAttribute("name"))
        assertEquals("false", darkItems.item(1).textContent)
        assertEquals("true", light.getElementsByTagName("item").item(0).textContent)
    }

    @Test fun `existing dark elevation setting is safely overridden`() {
        val doc = xml(
            """
            <resources>
                <style name="Base.Theme.GoogleMaterial3.Dialer.Dark.NoActionBar">
                    <item name="elevationOverlayEnabled">true</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        assertEquals(1, disablePureAmoledDarkTonalElevation(doc))
        assertEquals(0, disablePureAmoledDarkTonalElevation(doc))
    }

    @Test fun `night bottom navigation removes Material3 surface tint and elevation`() {
        val base = xml(
            """
            <resources>
                <style name="Widget.Material3Expressive.BottomNavigationView" parent="@style/Widget.Material3.BottomNavigationView">
                    <item name="android:background">?attr/colorSurfaceContainer</item>
                    <item name="elevation">@dimen/m3_comp_nav_bar_container_elevation</item>
                    <item name="itemIconTint">@color/m3expressive_bottom_nav_item_icon_tint</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        val night = xml("<resources/>")
        assertEquals(1, installPureAmoledNightBottomNavigationOverride(base, night))
        assertEquals(0, installPureAmoledNightBottomNavigationOverride(base, night))

        val source = base.getElementsByTagName("style").item(0) as org.w3c.dom.Element
        val copy = night.getElementsByTagName("style").item(0) as org.w3c.dom.Element
        val sourceItems = source.getElementsByTagName("item")
        val nightItems = copy.getElementsByTagName("item")
        assertEquals("?attr/colorSurfaceContainer", sourceItems.item(0).textContent)
        assertEquals("@dimen/m3_comp_nav_bar_container_elevation", sourceItems.item(1).textContent)
        assertEquals("#FF000000", nightItems.item(0).textContent)
        assertEquals("0dp", nightItems.item(1).textContent)
        assertEquals("@color/m3expressive_bottom_nav_item_icon_tint", nightItems.item(2).textContent)
    }

    @Test fun `main portrait layout receives explicit black only in night copy`() {
        val source = xml(
            """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <androidx.drawerlayout.widget.DrawerLayout android:background="?attr/colorSurfaceContainer">
                    <com.google.android.material.bottomnavigation.BottomNavigationView
                        android:id="@id/navigation_bar_layout"
                        android:background="?attr/colorNavigationBar" />
                    <com.google.android.material.navigation.NavigationView
                        android:id="@id/main_fragment_navigation" />
                </androidx.drawerlayout.widget.DrawerLayout>
            </FrameLayout>
            """.trimIndent(),
        )
        val night = xml(
            """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <androidx.drawerlayout.widget.DrawerLayout android:background="?attr/colorSurfaceContainer">
                    <com.google.android.material.bottomnavigation.BottomNavigationView
                        android:id="@id/navigation_bar_layout"
                        android:background="?attr/colorNavigationBar" />
                    <com.google.android.material.navigation.NavigationView
                        android:id="@id/main_fragment_navigation" />
                </androidx.drawerlayout.widget.DrawerLayout>
            </FrameLayout>
            """.trimIndent(),
        )
        assertEquals(3, applyPureAmoledNightMainLayout(night, expectBottomNavigation = true))
        assertEquals(0, applyPureAmoledNightMainLayout(night, expectBottomNavigation = true))

        val drawer = night.getElementsByTagName("androidx.drawerlayout.widget.DrawerLayout")
        val bottom = night.getElementsByTagName("com.google.android.material.bottomnavigation.BottomNavigationView")
        val side = night.getElementsByTagName("com.google.android.material.navigation.NavigationView")
        assertEquals("#FF000000", (drawer.item(0) as org.w3c.dom.Element).getAttribute("android:background"))
        assertEquals("#FF000000", (bottom.item(0) as org.w3c.dom.Element).getAttribute("android:background"))
        assertEquals("#FF000000", (side.item(0) as org.w3c.dom.Element).getAttribute("android:background"))

        val dayDrawer = source.getElementsByTagName("androidx.drawerlayout.widget.DrawerLayout")
        val dayBottom = source.getElementsByTagName("com.google.android.material.bottomnavigation.BottomNavigationView")
        val daySide = source.getElementsByTagName("com.google.android.material.navigation.NavigationView")
        assertEquals("?attr/colorSurfaceContainer", (dayDrawer.item(0) as org.w3c.dom.Element).getAttribute("android:background"))
        assertEquals("?attr/colorNavigationBar", (dayBottom.item(0) as org.w3c.dom.Element).getAttribute("android:background"))
        assertEquals("", (daySide.item(0) as org.w3c.dom.Element).getAttribute("android:background"))
    }

    @Test fun `landscape main layout has black drawer and no bottom navigation`() {
        val night = xml(
            """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <androidx.drawerlayout.widget.DrawerLayout android:background="?colorSurfaceContainer">
                    <com.google.android.material.navigation.NavigationView
                        android:id="@id/main_fragment_navigation" />
                </androidx.drawerlayout.widget.DrawerLayout>
            </FrameLayout>
            """.trimIndent(),
        )
        assertEquals(2, applyPureAmoledNightMainLayout(night, expectBottomNavigation = false))
        assertEquals(0, applyPureAmoledNightMainLayout(night, expectBottomNavigation = false))
    }

    @Test fun `night main layout rejects unknown dynamic backgrounds`() {
        val night = xml(
            """
            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android">
                <androidx.drawerlayout.widget.DrawerLayout android:background="?attr/colorPrimary">
                    <com.google.android.material.bottomnavigation.BottomNavigationView
                        android:background="?attr/colorNavigationBar" />
                    <com.google.android.material.navigation.NavigationView />
                </androidx.drawerlayout.widget.DrawerLayout>
            </FrameLayout>
            """.trimIndent(),
        )
        val error = kotlin.runCatching {
            applyPureAmoledNightMainLayout(night, expectBottomNavigation = true)
        }.exceptionOrNull()
        assertTrue(error is app.morphe.patcher.patch.PatchException)
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
    @Test fun `matches Phone 241 without requiring a fixed version code`() {
        assertTrue(isSupportedPureAmoledApp("com.google.android.dialer", "241.0.991235506"))
        assertFalse(isSupportedPureAmoledApp("com.google.android.dialer.morphe", "241.0.991235506"))
        assertFalse(isSupportedPureAmoledApp("com.google.android.dialer", "240.0.999999999"))
    }

}
