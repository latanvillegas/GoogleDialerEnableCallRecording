package local.dialer.theme

import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.StringReader

class DialerVisualCustomizerPatchTest {
    private fun xml(source: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(source)))
    }

    @Test
    fun `adds a single AndroidX Preference intent under real appearance category`() {
        val document = xml(
            """
            <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                <PreferenceCategory android:persistent="false" android:title="@string/appearance_category_title"
                    android:key="@string/appearance_category_key">
                    <ListPreference android:title="@string/theme_preference_label"
                        android:key="@string/display_options_theme_preference_key" />
                </PreferenceCategory>
                <PreferenceCategory android:title="@string/contacts_category_title" />
            </PreferenceScreen>
            """.trimIndent(),
        )
        assertEquals(1, addVisualCustomizationPreference(document))
        assertEquals(0, addVisualCustomizationPreference(document))
        val categories = document.getElementsByTagName("PreferenceCategory")
        val appearance = categories.item(0) as org.w3c.dom.Element
        assertEquals(1, appearance.getElementsByTagName("Preference").length)
        val preference = appearance.getElementsByTagName("Preference").item(0) as org.w3c.dom.Element
        assertEquals("Personalización visual",
            preference.getAttributeNS("http://schemas.android.com/apk/res/android", "title"))
        val intent = preference.getElementsByTagName("intent").item(0) as org.w3c.dom.Element
        assertEquals("local.dialer.visual.CONFIGURE",
            intent.getAttributeNS("http://schemas.android.com/apk/res/android", "action"))
        assertEquals(1, appearance.getElementsByTagName("ListPreference").length)
        assertEquals(0, (categories.item(1) as org.w3c.dom.Element)
            .getElementsByTagName("Preference").length)
    }

    @Test
    fun `registers a unique launchable visual Activity with no permissions`() {
        val manifest = xml(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.google.android.dialer">
                <application android:label="Phone">
                    <activity android:name="com.android.dialer.main.impl.MainActivity" />
                </application>
            </manifest>
            """.trimIndent(),
        )
        assertEquals(1, registerVisualCustomizerActivity(manifest))
        assertEquals(0, registerVisualCustomizerActivity(manifest))
        val apps = manifest.getElementsByTagName("application")
        val activities = (apps.item(0) as org.w3c.dom.Element).getElementsByTagName("activity")
        assertEquals(2, activities.length)
        val customizer = activities.item(1) as org.w3c.dom.Element
        assertEquals("local.dialer.visual.VisualCustomizerActivity",
            customizer.getAttributeNS("http://schemas.android.com/apk/res/android", "name"))
        assertEquals("true", customizer.getAttributeNS("http://schemas.android.com/apk/res/android", "exported"))
        assertEquals("local.dialer.visual.CONFIGURE", (customizer.getElementsByTagName("action")
            .item(0) as org.w3c.dom.Element)
            .getAttributeNS("http://schemas.android.com/apk/res/android", "name"))
        assertEquals(0, manifest.getElementsByTagName("uses-permission").length)
    }

    @Test
    fun `rejects unknown settings structure instead of adding an inert control`() {
        val doc = xml("<resources/>")
        val error = kotlin.runCatching { addVisualCustomizationPreference(doc) }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is app.morphe.patcher.patch.PatchException)
    }
    @Test
    fun `identifies supported Google Phone version without assuming a single version code`() {
        assertTrue(isSupportedVisualCustomizerApp("com.google.android.dialer", "241.0.991235506"))
        assertFalse(isSupportedVisualCustomizerApp("com.google.android.dialer.morphe", "241.0.991235506"))
        assertFalse(isSupportedVisualCustomizerApp("com.google.android.dialer", "240.0.989999999"))
    }

}
