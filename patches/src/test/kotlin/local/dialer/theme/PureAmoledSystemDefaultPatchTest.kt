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
