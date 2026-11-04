package local.dialer.theme

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import java.util.Locale
import java.util.logging.Logger
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val BLACK = "#FF000000"

/**
 * Only neutral night surfaces are eligible. Preserve functional colors such as
 * error/success/warning banners, branded previews, accessibility controls and
 * buttons; turning those black would erase their purpose and contrast.
 * Foreground and interaction colors must remain unchanged.
 */
internal fun isAmoledSurfaceName(resourceName: String): Boolean {
    val words = resourceName
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .lowercase(Locale.ROOT)
        .split(Regex("[^a-z0-9]+"))
        .filter(String::isNotEmpty)

    if (words.none { it == "background" || it == "surface" }) return false

    val excluded = setOf(
        "on", "foreground", "text", "icon", "ink", "content", "outline",
        "border", "divider", "stroke", "tint", "ripple", "highlight",
        "accent", "inverse", "shadow", "elevation", "state", "pressed",
        "selected", "checked", "focus", "focused", "disabled", "dim",
        "banner", "error", "success", "warn", "warning", "promo",
        "brand", "branding", "preview", "checker", "chronometer",
        "button", "accessibility",
    )
    return words.none { it in excluded }
}

internal fun isAmoledThemeItem(attributeName: String): Boolean = attributeName in setOf(
    "android:windowBackground",
    "android:colorBackground",
    "android:statusBarColor",
    "android:navigationBarColor",
    "colorBackground",
    "colorSurface",
    "colorSurfaceContainer",
    "colorSurfaceContainerLow",
    "colorSurfaceContainerLowest",
    "colorSurfaceContainerHigh",
    "colorSurfaceContainerHighest",
    "windowSplashScreenBackground",
)

private fun isColorValue(value: String): Boolean =
    value.startsWith("#") ||
        value.startsWith("@color/") ||
        value.startsWith("@android:color/") ||
        value.startsWith("?attr/") ||
        value.startsWith("?android:attr/")

/**
 * Modifies only an explicitly night-qualified XML resource document. The
 * Android day/night selector and Phone's preference logic are not changed.
 */
internal fun applyPureAmoledToNightDocument(document: Document, styles: Boolean): Int {
    val root = document.documentElement
    if (root.tagName != "resources") {
        throw PatchException("Pure AMOLED: unexpected XML root ${root.tagName}")
    }

    var changes = 0
    val nodes = root.childNodes
    for (index in 0 until nodes.length) {
        val element = nodes.item(index) as? Element ?: continue
        if (styles) {
            if (element.tagName != "style") continue
            val styleName = element.getAttribute("name").lowercase(Locale.ROOT)
            if ("light" in styleName && "daynight" !in styleName) continue

            val items = element.childNodes
            for (itemIndex in 0 until items.length) {
                val item = items.item(itemIndex) as? Element ?: continue
                if (item.tagName != "item" || !isAmoledThemeItem(item.getAttribute("name"))) continue
                val old = item.textContent.trim()
                if (!isColorValue(old) || old.equals(BLACK, ignoreCase = true)) continue
                item.textContent = BLACK
                changes++
            }
        } else {
            val isColor = element.tagName == "color" ||
                (element.tagName == "item" && element.getAttribute("type") == "color")
            if (!isColor || !isAmoledSurfaceName(element.getAttribute("name"))) continue
            val old = element.textContent.trim()
            if (!isColorValue(old) || old.equals(BLACK, ignoreCase = true)) continue
            element.textContent = BLACK
            changes++
        }
    }
    return changes
}

@Suppress("unused")
val pureAmoledSystemDefaultPatch = resourcePatch(
    name = "Pure AMOLED - System default",
    description = "Experimental: use pure black for night-mode surfaces while following Android's system theme. Leaves the light theme, call recording and DroidGuard unchanged.",
    default = false,
) {
    compatibleWith(
        Compatibility(
            name = "Google Phone",
            packageName = "com.google.android.dialer",
            appIconColor = 0x1A73E8,
            targets = listOf(
                AppTarget(
                    "241.0.991235506",
                    versionCode = 20129558,
                    description = "Experimental AMOLED resource patch; requires APK and device validation.",
                ),
            ),
        ),
    )

    execute {
        if (packageMetadata.packageName != "com.google.android.dialer" ||
            packageMetadata.versionName != "241.0.991235506" ||
            packageMetadata.versionCode != "20129558"
        ) {
            throw PatchException("Pure AMOLED: only the inspected Google Phone 241 release is eligible")
        }

        val resourceDirectory = get("res")
        val nightDirectories = resourceDirectory.listFiles()
            ?.filter { it.isDirectory && (it.name == "values-night" || it.name.startsWith("values-night-")) }
            ?.sortedBy { it.name }
            .orEmpty()

        if (nightDirectories.isEmpty()) {
            throw PatchException("Pure AMOLED: no night-qualified resources to modify")
        }

        var changedColors = 0
        var changedStyles = 0
        for (directory in nightDirectories) {
            for (fileName in listOf("colors.xml", "styles.xml")) {
                val file = directory.resolve(fileName)
                if (!file.isFile) continue
                val path = "res/${directory.name}/$fileName"
                document(path).use { document ->
                    val count = applyPureAmoledToNightDocument(document, fileName == "styles.xml")
                    if (fileName == "colors.xml") changedColors += count else changedStyles += count
                }
            }
        }

        if (changedColors + changedStyles == 0) {
            throw PatchException(
                "Pure AMOLED: no verified night surface resources found; " +
                    "the APK needs resource reconnaissance before theme modification",
            )
        }

        Logger.getLogger("LocalGooglePhone.PureAmoled").info(
            "Pure AMOLED: dark surfaces=$changedColors, night style items=$changedStyles; " +
                "day resources and both existing bytecode patches are untouched",
        )
    }
}
