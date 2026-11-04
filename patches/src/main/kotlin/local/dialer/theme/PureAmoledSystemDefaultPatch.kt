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

/**
 * The inspected Google Phone 241 APK also has explicitly dark Material3
 * palettes in unqualified values/colors.xml and API-qualified values-v31.
 * They are referenced by dark styles; light palettes in the same files are
 * never changed. The allowlist is derived from inspected resource names.
 */
internal fun isExplicitDarkAmoledColor(resourceName: String): Boolean {
    val name = resourceName.lowercase(Locale.ROOT)
    val darkPalette =
        name.startsWith("google_dark_default_color_") ||
            name.startsWith("gm3_dark_default_color_") ||
            name.startsWith("gm3_sys_color_dynamic_dark_") ||
            name.startsWith("m3_sys_color_dynamic_dark_") ||
            name == "background_material_dark" ||
            name == "google_marterial_color_surface_dark"

    if (!darkPalette) return false

    // "surface_dim" is a background surface, not a foreground dimming overlay.
    // All other exclusions, notably on_surface and inverse_surface, still apply.
    val normalized = resourceName.replace(Regex("_surface_dim$", RegexOption.IGNORE_CASE), "_surface")
    return isAmoledSurfaceName(normalized)
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
 * Modifies only explicitly night-qualified XML resource documents. The
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

/**
 * Only explicitly dark-named colors in default and API-qualified values
 * directories are eligible. No styles or night/day selection are modified.
 */
internal fun applyPureAmoledToExplicitDarkColors(document: Document): Int {
    val root = document.documentElement
    if (root.tagName != "resources") {
        throw PatchException("Pure AMOLED: unexpected XML root ${root.tagName}")
    }

    var changes = 0
    val nodes = root.childNodes
    for (index in 0 until nodes.length) {
        val element = nodes.item(index) as? Element ?: continue
        val isColor = element.tagName == "color" ||
            (element.tagName == "item" && element.getAttribute("type") == "color")
        if (!isColor || !isExplicitDarkAmoledColor(element.getAttribute("name"))) continue

        val old = element.textContent.trim()
        if (!isColorValue(old) || old.equals(BLACK, ignoreCase = true)) continue
        element.textContent = BLACK
        changes++
    }
    return changes
}


/**
 * APK evidence (Google Phone 241):
 * - layout/drawer_main_fragment*.xml uses ?colorNavigationBar for bottom navigation
 * - Base.Theme.GoogleMaterial3.Dialer.Dark.NoActionBar defines colorNavigationBar
 * - Widget.Material3.NavigationView has ?colorSurfaceContainerLow background
 * - Widget.Material3Expressive.BottomNavigationView.ActiveIndicator is
 *   colored by ?colorSecondaryContainer.
 *
 * Patch only the dark Dialer theme and night-specific widget variants.
 * Replicating existing widget declarations under values-night preserves their
 * resource IDs, default-day declarations, and other widget attributes.
 */
private data class NightWidgetOverride(
    val name: String,
    val attribute: String,
    val expectedValue: String,
    val amoledValue: String,
)

private val nightWidgetOverrides = listOf(
    NightWidgetOverride(
        "Widget.Material3.NavigationView",
        "android:background",
        "?colorSurfaceContainerLow",
        BLACK,
    ),
    NightWidgetOverride(
        "Widget.Material3Expressive.BottomNavigationView.ActiveIndicator",
        "android:color",
        "?colorSecondaryContainer",
        "#FF262626",
    ),
)

/**
 * Morphe may materialize shorthand ?attribute references as ?attr/attribute.
 * Accept precisely those two encodings, never a different attribute.
 */
internal fun matchesExpectedAmoledAttributeReference(actual: String, expected: String): Boolean {
    val normalized = actual.trim()
    if (normalized == expected) return true
    if (!expected.startsWith("?") || expected.startsWith("?attr/")) return false
    return normalized == "?attr/${expected.removePrefix("?")}"
}

private fun namedStyle(root: Element, name: String): Element? {
    val children = root.childNodes
    var result: Element? = null
    for (index in 0 until children.length) {
        val style = children.item(index) as? Element ?: continue
        if (style.tagName != "style" || style.getAttribute("name") != name) continue
        if (result != null) throw PatchException("Pure AMOLED: duplicate style $name")
        result = style
    }
    return result
}

private fun styleItem(style: Element, attribute: String): Element? {
    val children = style.childNodes
    var result: Element? = null
    for (index in 0 until children.length) {
        val item = children.item(index) as? Element ?: continue
        if (item.tagName != "item" || item.getAttribute("name") != attribute) continue
        if (result != null) {
            throw PatchException("Pure AMOLED: duplicate $attribute in ${style.getAttribute("name")}")
        }
        result = item
    }
    return result
}

internal fun applyPureAmoledDarkDialerNavigation(document: Document): Int {
    val root = document.documentElement
    if (root.tagName != "resources") {
        throw PatchException("Pure AMOLED: unexpected styles XML root ${root.tagName}")
    }

    val darkStyle = namedStyle(root, "Base.Theme.GoogleMaterial3.Dialer.Dark.NoActionBar")
        ?: throw PatchException("Pure AMOLED: inspected dark Dialer theme is missing")
    if (namedStyle(root, "Base.Theme.GoogleMaterial3.Dialer.Light.NoActionBar") == null) {
        throw PatchException("Pure AMOLED: inspected light Dialer theme is missing")
    }

    val background = styleItem(darkStyle, "colorNavigationBar")
        ?: throw PatchException("Pure AMOLED: dark colorNavigationBar is missing")
    val original = background.textContent.trim()
    if (!matchesExpectedAmoledAttributeReference(original, "?colorSurfaceContainerLow") &&
        !original.equals(BLACK, true)
    ) {
        throw PatchException("Pure AMOLED: unexpected dark colorNavigationBar=$original")
    }

    var changes = 0
    if (!original.equals(BLACK, true)) {
        background.textContent = BLACK
        changes++
    }

    val systemNavigation = styleItem(darkStyle, "android:navigationBarColor")
    if (systemNavigation == null) {
        val item = document.createElement("item")
        item.setAttribute("name", "android:navigationBarColor")
        item.textContent = BLACK
        darkStyle.appendChild(item)
        changes++
    } else {
        val previous = systemNavigation.textContent.trim()
        if (previous != "@android:color/transparent" &&
            !matchesExpectedAmoledAttributeReference(previous, "?colorNavigationBar") &&
            !previous.equals(BLACK, true)
        ) {
            throw PatchException("Pure AMOLED: unexpected system navigationBarColor=$previous")
        }
        if (!previous.equals(BLACK, true)) {
            systemNavigation.textContent = BLACK
            changes++
        }
    }
    return changes
}

internal fun installPureAmoledNightWidgetOverrides(
    sourceStyles: Document,
    nightStyles: Document,
): Int {
    val sourceRoot = sourceStyles.documentElement
    val nightRoot = nightStyles.documentElement
    if (sourceRoot.tagName != "resources" || nightRoot.tagName != "resources") {
        throw PatchException("Pure AMOLED: unexpected widget styles XML root")
    }

    var changes = 0
    for (rule in nightWidgetOverrides) {
        val sourceStyle = namedStyle(sourceRoot, rule.name)
            ?: throw PatchException("Pure AMOLED: inspected widget style missing: ${rule.name}")
        val sourceItem = styleItem(sourceStyle, rule.attribute)
            ?: throw PatchException("Pure AMOLED: widget attribute missing: ${rule.name}")

        if (!matchesExpectedAmoledAttributeReference(sourceItem.textContent, rule.expectedValue)) {
            throw PatchException("Pure AMOLED: source widget changed: ${rule.name}")
        }

        val existing = namedStyle(nightRoot, rule.name)
        if (existing != null) {
            val value = styleItem(existing, rule.attribute)?.textContent?.trim()
            if (value != rule.amoledValue) {
                throw PatchException("Pure AMOLED: conflicting night widget override: ${rule.name}")
            }
            continue
        }

        val nightStyle = nightStyles.importNode(sourceStyle, true) as Element
        val nightItem = styleItem(nightStyle, rule.attribute)
            ?: throw PatchException("Pure AMOLED: imported widget attribute missing")
        nightItem.textContent = rule.amoledValue
        nightRoot.appendChild(nightStyle)
        changes++
    }
    return changes
}


/**
 * The actual main screens use two drawer_main_fragment layout variants.
 * The inspected Phone 241 APK additionally has landscape and sw600dp variants.
 *
 * The Material3 dynamic overlay can override theme-based surface colors.
 * Therefore, explicit backgrounds are applied to NIGHT-QUALIFIED COPIES ONLY:
 * the original six day/layout files are never modified.
 */
internal fun applyPureAmoledNightMainLayout(
    document: Document,
    expectBottomNavigation: Boolean,
): Int {
    val root = document.documentElement
    if (root.tagName != "FrameLayout") {
        throw PatchException("Pure AMOLED: unexpected main layout root ${root.tagName}")
    }

    fun exactlyOne(tag: String): Element {
        val matches = root.getElementsByTagName(tag)
        if (matches.length != 1) {
            throw PatchException("Pure AMOLED: expected one $tag, found ${matches.length}")
        }
        return matches.item(0) as? Element
            ?: throw PatchException("Pure AMOLED: invalid $tag element")
    }

    val drawer = exactlyOne("androidx.drawerlayout.widget.DrawerLayout")
    val navigationDrawer = exactlyOne("com.google.android.material.navigation.NavigationView")
    val bottomViews = root.getElementsByTagName(
        "com.google.android.material.bottomnavigation.BottomNavigationView",
    )
    val expectedBottomCount = if (expectBottomNavigation) 1 else 0
    if (bottomViews.length != expectedBottomCount) {
        throw PatchException(
            "Pure AMOLED: expected $expectedBottomCount bottom navigation views, " +
                "found ${bottomViews.length}",
        )
    }

    fun setBlackBackground(
        element: Element,
        expectedAttribute: String?,
        component: String,
    ): Int {
        val previous = element.getAttribute("android:background").trim()
        if (previous.equals(BLACK, ignoreCase = true)) return 0

        if (expectedAttribute == null) {
            if (previous.isNotEmpty()) {
                throw PatchException(
                    "Pure AMOLED: unexpected $component background=$previous",
                )
            }
        } else if (!matchesExpectedAmoledAttributeReference(
                previous,
                expectedAttribute,
            )
        ) {
            throw PatchException("Pure AMOLED: unexpected $component background=$previous")
        }

        element.setAttribute("android:background", BLACK)
        return 1
    }

    var changes = 0
    changes += setBlackBackground(drawer, "?colorSurfaceContainer", "drawer root")
    changes += setBlackBackground(navigationDrawer, null, "navigation drawer")
    if (expectBottomNavigation) {
        val bottomNavigation = bottomViews.item(0) as? Element
            ?: throw PatchException("Pure AMOLED: invalid bottom navigation")
        changes += setBlackBackground(
            bottomNavigation,
            "?colorNavigationBar",
            "bottom navigation",
        )
    }
    return changes
}

@Suppress("unused")
val pureAmoledSystemDefaultPatch = resourcePatch(
    name = "Pure AMOLED - System default",
    description = "Experimental: system-following pure black main surfaces, drawer, and bottom navigation via night-only layout overlays; neutral active-tab indicator. Light mode, call recording and DroidGuard unchanged.",
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

        // Resource names confirmed in the inspected APK:
        // values-v31/colors.xml: gm3_sys_color_dynamic_dark_* and m3_*_dark_*
        // values/styles.xml: dark themes reference *_dark_default_color_*.
        // Restrict unqualified resources to explicitly dark palette names.
        val apiValueDirectories = resourceDirectory.listFiles()
            ?.filter {
                it.isDirectory &&
                    (it.name == "values" || it.name.matches(Regex("values-v[0-9]+")))
            }
            ?.sortedBy { it.name }
            .orEmpty()

        var changedDarkPalettes = 0
        for (directory in apiValueDirectories) {
            val colors = directory.resolve("colors.xml")
            if (!colors.isFile) continue
            document("res/${directory.name}/colors.xml").use { document ->
                changedDarkPalettes += applyPureAmoledToExplicitDarkColors(document)
            }
        }

        // The source resources show the actual drawer and bottom-nav style chain.
        // The dark theme attribute fixes the navigation container and system nav bar.
        // Two qualifier-specific widget variants fix the drawer and the selected
        // tab indicator; the original day styles are not touched.
        var changedNavigation = 0
        document("res/values/styles.xml").use { baseStyles ->
            changedNavigation += applyPureAmoledDarkDialerNavigation(baseStyles)
            document("res/values-night/styles.xml").use { nightStyles ->
                changedNavigation += installPureAmoledNightWidgetOverrides(
                    baseStyles,
                    nightStyles,
                )
            }
        }

        // The six original layout variants have been inspected from the
        // user's Google Phone 241 APK. Create only night-qualified overrides.
        // Explicit view backgrounds take precedence over dynamic color overlays.
        val layoutVariants = listOf(
            "layout" to "layout-night",
            "layout-land" to "layout-land-night",
            "layout-sw600dp" to "layout-sw600dp-night",
        )
        val mainLayouts = listOf(
            "drawer_main_fragment.xml",
            "drawer_main_fragment_cc26.xml",
        )
        var changedNightLayouts = 0
        for ((sourceQualifier, nightQualifier) in layoutVariants) {
            val nightDirectory = resourceDirectory.resolve(nightQualifier)
            if (!nightDirectory.isDirectory && !nightDirectory.mkdirs()) {
                throw PatchException("Pure AMOLED: cannot create $nightQualifier")
            }
            for (layoutName in mainLayouts) {
                val originalLayout = resourceDirectory
                    .resolve(sourceQualifier)
                    .resolve(layoutName)
                if (!originalLayout.isFile) {
                    throw PatchException(
                        "Pure AMOLED: inspected layout is missing: $sourceQualifier/$layoutName",
                    )
                }

                val destination = nightDirectory.resolve(layoutName)
                if (destination.exists()) {
                    throw PatchException(
                        "Pure AMOLED: refusing to overwrite $nightQualifier/$layoutName",
                    )
                }
                originalLayout.copyTo(destination, overwrite = false)
                document("res/$nightQualifier/$layoutName").use { nightLayout ->
                    changedNightLayouts += applyPureAmoledNightMainLayout(
                        nightLayout,
                        expectBottomNavigation = sourceQualifier == "layout",
                    )
                }
            }
        }

        if (changedColors + changedStyles + changedDarkPalettes + changedNavigation + changedNightLayouts == 0) {
            throw PatchException(
                "Pure AMOLED: no verified dark surface resources found; " +
                    "the APK needs resource reconnaissance before theme modification",
            )
        }

        Logger.getLogger("LocalGooglePhone.PureAmoled").info(
            "Pure AMOLED: night surfaces=$changedColors, night styles=$changedStyles, " +
                "named dark palettes=$changedDarkPalettes, dark navigation=$changedNavigation, " +
                "night main layouts=$changedNightLayouts; day layouts and both bytecode patches untouched",
        )
    }
}
