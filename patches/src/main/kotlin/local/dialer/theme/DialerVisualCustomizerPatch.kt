package local.dialer.theme

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val DIALPAD = "Lcom/google/android/libraries/communications/ux/views/dialpad/Dialpad;"
private const val IN_CALL_ACTIVITY = "Lcom/android/dialer/incall/activity/ui/InCallActivity;"
private const val IN_CALL_RESUME =
    "Llocal/dialer/visual/InCallThemeHooks;->onPostResume(Landroid/app/Activity;)V"
private const val IN_CALL_PAUSE =
    "Llocal/dialer/visual/InCallThemeHooks;->onPause(Landroid/app/Activity;)V"
private const val VISUAL_ACTIVITY = "local.dialer.visual.VisualCustomizerActivity"
private const val VISUAL_ACTION = "local.dialer.visual.CONFIGURE"
private const val VISUAL_HOOK =
    "Llocal/dialer/visual/VisualCustomizer;->apply(Landroid/view/View;)V"
private const val CALL_LOG_ADAPTER = "Lkit;"
private const val CALL_HISTORY_BEFORE =
    "Llocal/dialer/visual/CallHistoryBridge;->onBeforeBind(Landroid/view/View;)V"
private const val CALL_HISTORY_AFTER =
    "Llocal/dialer/visual/CallHistoryBridge;->onAfterBind(Landroid/view/View;I)V"

/**
 * Google Phone can ship different version codes under one visible release.
 * The resource patch checks the package and version; the bytecode path still
 * rejects any unverified method/field layout before injecting instructions.
 */
internal fun isSupportedVisualCustomizerApp(packageName: String, versionName: String): Boolean =
    packageName == "com.google.android.dialer" && versionName == "241.0.991235506"

/**
 * The inspected res/xml/display_options_settings.xml is inflated by Lmjz;->aU.
 * A Preference intent is handled by AndroidX without injecting a brittle listener
 * into an obfuscated settings peer. The corresponding activity is in the .mpe.
 */
internal fun addVisualCustomizationPreference(document: Document): Int {
    val root = document.documentElement
    if (root.tagName != "PreferenceScreen") {
        throw PatchException("Visual Customizer: unexpected display settings root")
    }
    val categories = root.getElementsByTagName("PreferenceCategory")
    var appearance: Element? = null
    for (i in 0 until categories.length) {
        val category = categories.item(i) as Element
        if (category.getAttributeNS(ANDROID_URI, "title") == "@string/appearance_category_title") {
            appearance = category
            break
        }
    }
    val group = appearance ?: throw PatchException("Visual Customizer: missing appearance category")
    val children = group.getElementsByTagName("Preference")
    for (i in 0 until children.length) {
        val existing = children.item(i) as Element
        if (existing.getAttributeNS(ANDROID_URI, "key") == "local_dialer_visual_customizer") {
            return 0
        }
    }

    val preference = document.createElement("Preference")
    preference.setAttributeNS(ANDROID_URI, "android:key", "local_dialer_visual_customizer")
    preference.setAttributeNS(ANDROID_URI, "android:title", "Personalización visual")
    preference.setAttributeNS(ANDROID_URI, "android:summary", "Paletas, teclas, contornos y colores")
    preference.setAttributeNS(ANDROID_URI, "android:persistent", "false")
    val intent = document.createElement("intent")
    intent.setAttributeNS(ANDROID_URI, "android:action", VISUAL_ACTION)
    preference.appendChild(intent)
    group.appendChild(preference)
    return 1
}

/**
 * Exported solely to receive the action declared in an AndroidX Preference
 * intent. The action doesn't disclose contacts/calls, and the Activity only
 * accesses this application's private appearance preferences.
 * No new Android permissions are introduced.
 */
internal fun registerVisualCustomizerActivity(document: Document): Int {
    val root = document.documentElement
    if (root.tagName != "manifest") {
        throw PatchException("Visual Customizer: unexpected manifest root")
    }
    val apps = root.getElementsByTagName("application")
    if (apps.length != 1) throw PatchException("Visual Customizer: expected one application")
    val app = apps.item(0) as Element
    val activities = app.getElementsByTagName("activity")
    for (i in 0 until activities.length) {
        val existing = activities.item(i) as Element
        if (existing.getAttributeNS(ANDROID_URI, "name") == VISUAL_ACTIVITY) return 0
    }

    val activity = document.createElement("activity")
    activity.setAttributeNS(ANDROID_URI, "android:name", VISUAL_ACTIVITY)
    activity.setAttributeNS(ANDROID_URI, "android:exported", "true")
    activity.setAttributeNS(ANDROID_URI, "android:label", "Personalización visual")
    activity.setAttributeNS(
        ANDROID_URI, "android:theme", "@style/Theme.GoogleMaterial3.Dialer.DayNight.NoActionBar",
    )
    val filter = document.createElement("intent-filter")
    val action = document.createElement("action")
    action.setAttributeNS(ANDROID_URI, "android:name", VISUAL_ACTION)
    val category = document.createElement("category")
    category.setAttributeNS(ANDROID_URI, "android:name", "android.intent.category.DEFAULT")
    filter.appendChild(action)
    filter.appendChild(category)
    activity.appendChild(filter)
    app.appendChild(activity)
    return 1
}

private val visualCustomizerResourcesPatch = resourcePatch {
    execute {
        if (!isSupportedVisualCustomizerApp(packageMetadata.packageName, packageMetadata.versionName)) {
            throw PatchException(
                "Visual Customizer: unsupported Google Phone APK " +
                    "(package=${packageMetadata.packageName}, " +
                    "version=${packageMetadata.versionName}, " +
                    "versionCode=${packageMetadata.versionCode})",
            )
        }
        document("res/xml/display_options_settings.xml").use { settings ->
            if (addVisualCustomizationPreference(settings) != 1) {
                throw PatchException("Visual Customizer: duplicate appearance preference")
            }
        }
        document("AndroidManifest.xml").use { manifest ->
            if (registerVisualCustomizerActivity(manifest) != 1) {
                throw PatchException("Visual Customizer: duplicate manifest activity")
            }
        }
    }
}

/**
 * The bytecode path is anchored to the exact user-inspected class and method.
 * onLayout has 0 locals and 6 parameter registers (p0..p5), so an invoke-static
 * using p0 is 4-bit-safe and doesn't modify application registers.
 */
@Suppress("unused")
val dialerVisualCustomizerPatch = bytecodePatch(
    name = "Dialer Visual Customizer",
    description = "Modular visual customization: AMOLED dialpad themes, opt-in in-call media/controls, and verified call-history row colors. Spam detection and actual history filtering remain native pending model validation.",
    default = false,
) {
    compatibleWith(
        Compatibility(
            name = "Google Phone",
            packageName = "com.google.android.dialer",
            appIconColor = 0x1A73E8,
            targets = listOf(
                AppTarget(
                    version = "241.0.991235506",
                    description = "Other version codes require matching resource and DEX structure.",
                ),
            ),
        ),
    )
    dependsOn(visualCustomizerResourcesPatch)
    extendWith("extensions/visual-customizer.mpe")

    execute {
        val dialpadClass = classDefBy(DIALPAD)
        val layout = dialpadClass.methods.singleOrNull { method ->
            method.name == "onLayout" && method.returnType == "V" &&
                method.parameterTypes.map { it.toString() } == listOf("Z", "I", "I", "I", "I")
        } ?: throw PatchException("Visual Customizer: missing or ambiguous Dialpad.onLayout")
        val implementation = layout.implementation
            ?: throw PatchException("Visual Customizer: Dialpad.onLayout has no body")
        val opcodes = implementation.instructions.toList()
        if (implementation.registerCount != 6 ||
            opcodes.firstOrNull()?.opcode != Opcode.INVOKE_SUPER_RANGE ||
            opcodes.lastOrNull()?.opcode != Opcode.RETURN_VOID ||
            dialpadClass.methods.any { it.name == "onWindowFocusChanged" && it.parameterTypes.size == 1 }
        ) {
            throw PatchException("Visual Customizer: Dialpad bytecode differs from inspected Google Phone 241")
        }
        val mutable = mutableClassDefBy(dialpadClass)
        val onLayout = mutable.methods.single { it.name == "onLayout" && it.parameterTypes.size == 5 }
        onLayout.addInstruction(1, "invoke-static { p0 }, $VISUAL_HOOK")

        // The existing View remains alive while the settings Activity is on top.
        // Refresh on focus return, even when Android doesn't perform a new layout.
        val focus = ImmutableMethod(
            DIALPAD,
            "onWindowFocusChanged",
            listOf(ImmutableMethodParameter("Z", null, null)),
            "V",
            AccessFlags.PUBLIC.value,
            null,
            null,
            MutableMethodImplementation(2),
        ).toMutable()
        focus.addInstructions(0, """
            invoke-super { p0, p1 }, Landroid/widget/FrameLayout;->onWindowFocusChanged(Z)V
            if-eqz p1, :visual_done
            invoke-static { p0 }, $VISUAL_HOOK
            :visual_done
            return-void
        """.trimIndent())
        if (!mutable.methods.add(focus)) {
            throw PatchException("Visual Customizer: failed to install focus refresh method")
        }

        // InCallActivity is directly verified in the user's Phone 241 DEX.
        // A single activity hook observes fragment layouts in the UI process,
        // without intercepting Telecom callbacks, answer/reject/end handlers,
        // InCallServiceImpl, or Android's ringtone engine.
        val inCall = classDefBy(IN_CALL_ACTIVITY)
        fun lifecycleMethod(name: String, registers: Int): Method {
            val method = inCall.methods.singleOrNull { candidate ->
                candidate.name == name && candidate.returnType == "V" &&
                    candidate.parameterTypes.isEmpty()
            } ?: throw PatchException("Visual Customizer: unverified InCallActivity.$name")
            val body = method.implementation
                ?: throw PatchException("Visual Customizer: missing InCallActivity.$name body")
            val superCall = body.instructions.filterIsInstance<ReferenceInstruction>()
                .mapNotNull { it.reference as? MethodReference }
                .count {
                    it.definingClass == "Loqs;" && it.name == name &&
                        it.parameterTypes.isEmpty() && it.returnType == "V"
                }
            if (body.registerCount != registers || superCall != 1 ||
                body.instructions.count { it.opcode == Opcode.RETURN_VOID } != 1
            ) {
                throw PatchException("Visual Customizer: inspected InCallActivity.$name contract changed")
            }
            return method
        }
        // .locals 1 + p0; onPostResume returns after closing its native scope.
        // .locals 6 + p0; onPause is invoked before the original body begins.
        lifecycleMethod("onPostResume", 2)
        lifecycleMethod("onPause", 7)
        val inCallMutable = mutableClassDefBy(inCall)
        val postResume = inCallMutable.methods.single {
            it.name == "onPostResume" && it.parameterTypes.isEmpty()
        }
        val postReturn = postResume.implementation!!.instructions.indexOfFirst {
            it.opcode == Opcode.RETURN_VOID
        }
        if (postReturn < 0) throw PatchException("Visual Customizer: missing onPostResume return")
        postResume.addInstruction(postReturn, "invoke-static { p0 }, $IN_CALL_RESUME")
        val pause = inCallMutable.methods.single {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        pause.addInstruction(0, "invoke-static { p0 }, $IN_CALL_PAUSE")

        // Google Phone 241: audited CallLogAdapter.kt binder
        // Lkit;->E(Laegg;Lkaw;Llb;I)V, .locals 11 (16 total registers),
        // precisely one return and no try blocks. The binder executes Lkjq.G()
        // and ends by handing Llb.a back to the RecyclerView. Native model
        // Lkjq.E() returns Lkgn, whose q:I is the existing call-type value.
        // Never classify spam by name, avatar or screen text.
        val adapterClass = classDefBy(CALL_LOG_ADAPTER)
        val bind = adapterClass.methods.singleOrNull { candidate ->
            candidate.name == "E" &&
                candidate.returnType == "V" &&
                candidate.parameterTypes.map { it.toString() } == listOf(
                    "Laegg;", "Lkaw;", "Llb;", "I",
                )
        } ?: throw PatchException("Visual Customizer: missing audited history row binder")
        val bindBody = bind.implementation
            ?: throw PatchException("Visual Customizer: history binder has no implementation")
        val nativeRowRefresh = bindBody.instructions.filterIsInstance<ReferenceInstruction>()
            .mapNotNull { it.reference as? MethodReference }
            .count {
                it.definingClass == "Lkjq;" && it.name == "G" &&
                    it.returnType == "V" && it.parameterTypes.isEmpty()
            }
        if (bindBody.registerCount != 16 ||
            bindBody.tryBlocks.isNotEmpty() ||
            bindBody.instructions.count { it.opcode == Opcode.RETURN_VOID } != 1 ||
            bindBody.instructions.lastOrNull()?.opcode != Opcode.RETURN_VOID ||
            nativeRowRefresh != 1
        ) {
            throw PatchException("Visual Customizer: history binder differs from audited DEX")
        }
        val rowHolder = classDefBy("Lkjq;")
        val rowModelMethod = rowHolder.methods.singleOrNull {
            it.name == "E" && it.returnType == "Lkgn;" && it.parameterTypes.isEmpty()
        } ?: throw PatchException("Visual Customizer: audited row model accessor missing")
        if (rowModelMethod.implementation == null ||
            classDefBy("Lkgn;").fields.none { it.name == "q" && it.type == "I" } ||
            classDefBy("Llb;").fields.none {
                it.name == "a" && it.type == "Landroid/view/View;"
            }
        ) {
            throw PatchException("Visual Customizer: call-type or row-root contract changed")
        }

        val mutableAdapter = mutableClassDefBy(adapterClass)
        val rowBind = mutableAdapter.methods.single {
            it.name == "E" && it.parameterTypes.map { p -> p.toString() } ==
                listOf("Laegg;", "Lkaw;", "Llb;", "I")
        }
        val returnIndex = rowBind.implementation!!.instructions.indexOfLast {
            it.opcode == Opcode.RETURN_VOID
        }
        // v0/v1 are local registers and dead at return. p3 is the verified
        // holder argument; no register expansion and no Telecom logic changes.
        rowBind.addInstructions(returnIndex, """
            iget-object v0, p3, Llb;->a:Landroid/view/View;
            move-object v1, p3
            check-cast v1, Lkjq;
            invoke-virtual {v1}, Lkjq;->E()Lkgn;
            move-result-object v1
            iget v1, v1, Lkgn;->q:I
            invoke-static {v0, v1}, $CALL_HISTORY_AFTER
        """.trimIndent())
        // Restore any previous custom tint BEFORE the stock adapter rebinds
        // the recycled holder. Otherwise color could leak between callers.
        rowBind.addInstructions(0, """
            iget-object v0, p3, Llb;->a:Landroid/view/View;
            invoke-static {v0}, $CALL_HISTORY_BEFORE
        """.trimIndent())
    }
}
