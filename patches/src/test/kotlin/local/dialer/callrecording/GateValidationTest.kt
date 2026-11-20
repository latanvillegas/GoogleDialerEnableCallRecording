package local.dialer.callrecording

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import kotlin.test.*
import org.junit.jupiter.api.Assumptions.assumeTrue

class GateValidationTest {
    private fun gate(): com.android.tools.smali.dexlib2.iface.Method {
        val apk = java.io.File(System.getProperty("originalApk"))
        assumeTrue(apk.isFile, "The original Google Phone APK is intentionally excluded from GitHub")
        return readMethods(apk).values.single { COUNTRY_DISABLED in it.exactStrings() }
    }

    @Test fun `actual APK validates with obfuscated identifiers changed`() {
        val renamed = gate().toMutable().apply {
            setDefiningClass("Ltest/arbitrary/Type;")
            setName("arbitraryName")
        }
        validateManualGate(renamed)
    }

    @Test fun `country log that now returns true is rejected`() {
        val changed = gate().toMutable()
        changed.addInstructions(changed.implementation!!.instructions.size - 1, "const/4 v0, 0x1")
        val error = assertFailsWith<PatchException> { validateManualGate(changed) }
        assertTrue(error.message!!.contains("unexpected return semantics"))
    }

    @Test fun `receiver cannot be used as a local register`() {
        val original = gate()
        val changed = ImmutableMethod(original.definingClass, original.name, original.parameters,
            original.returnType, original.accessFlags, original.annotations, original.hiddenApiRestrictions,
            MutableMethodImplementation(1))
        assertFailsWith<PatchException> { validateManualGate(changed) }
    }

    @Test fun `synchronized methods are rejected`() {
        val changed = gate().toMutable().apply {
            setAccessFlags(accessFlags or AccessFlags.SYNCHRONIZED.value)
        }
        assertFailsWith<PatchException> { validateManualGate(changed) }
    }

    @Test fun `fallbacks must agree even after a strong match`() {
        assertFailsWith<PatchException> {
            selectUniqueTarget(listOf("strong" to listOf("La;->a()Z"), "fallback" to listOf("Lb;->b()Z")))
        }
    }

    @Test fun `ambiguous or absent matches are rejected`() {
        assertFailsWith<PatchException> { selectUniqueTarget(listOf("ambiguous" to listOf("a", "b"))) }
        assertFailsWith<PatchException> { selectUniqueTarget(listOf("absent" to emptyList())) }
        assertEquals("a", selectUniqueTarget(listOf("strong" to listOf("a"), "fallback" to listOf("a"))))
    }

    /** v247 can have one shared return v0 with 0/1 proven on separate predecessors. */
    private fun sharedReturnGate(disabledPath: String) = ImmutableMethod(
        "Ltest/SyntheticCanRecord;", "a", emptyList(), "Z", AccessFlags.PUBLIC.value,
        null, null, MutableMethodImplementation(4),
    ).toMutable().apply {
        addInstructions(0, """
            const-string v1, "CanRecord.java"
            const-string v1, "com/android/dialer/callrecording/impl/canrecord/CanRecord"
            const-string v1, "canRecordCall"
            const/4 v0, 0x0
            const-string v1, "$AUDIO_FEATURE"
            invoke-virtual {v2, v1}, Landroid/content/pm/PackageManager;->hasSystemFeature(Ljava/lang/String;)Z
            move-result v2
            if-eqz v2, :disabled
            const/4 v2, 0x1
            const-string v1, "$FEATURE_ENABLED"
            move v0, v2
            goto :done
            :disabled
            const-string v1, "$FLAG_DISABLED"
            $disabledPath
            :done
            return v0
        """.trimIndent())
    }

    @org.junit.jupiter.api.TestFactory
    fun `shared exit validates independent constant returns`() = listOf(
        org.junit.jupiter.api.DynamicTest.dynamicTest(
            runCatching { validateManualGate(sharedReturnGate("const/4 v0, 0x0")) }
                .exceptionOrNull()?.message ?: "shared exit validated",
        ) {
            validateManualGate(sharedReturnGate("const/4 v0, 0x0"))
        },
    )

    @Test fun `shared exit rejects unknown return value`() {
        val method = sharedReturnGate("invoke-static {}, Ltest/Probe;->unknown()I\nmove-result v0")
        assertFailsWith<PatchException> { validateManualGate(method) }
    }

    @Test fun `shared exit rejects contradictory diagnostic`() {
        val method = sharedReturnGate("const/4 v0, 0x1")
        val failure = assertFailsWith<PatchException> { validateManualGate(method) }
        assertTrue(failure.message!!.contains("unexpected return semantics"))
    }
}
