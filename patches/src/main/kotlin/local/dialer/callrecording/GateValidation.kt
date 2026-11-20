package local.dialer.callrecording

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal const val COUNTRY_DISABLED = "Call recording is disabled in the current country"
internal const val FEATURE_ENABLED = "Call recording is enabled by call_recording_audio system feature"
internal const val FLAG_DISABLED = "Call recording is disabled by the call recording flag"
internal const val AUDIO_FEATURE = "com.google.android.apps.dialer.call_recording_audio"
internal val GATE_PROVENANCE = setOf(
    "com/android/dialer/callrecording/impl/canrecord/CanRecord", "CanRecord.java", "canRecordCall",
)

internal fun Method.signature() = "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"
internal fun Instruction.stringValue() = ((this as? ReferenceInstruction)?.reference as? StringReference)?.string
internal fun Method.exactStrings() = implementation?.instructions?.mapNotNull { it.stringValue() }?.toSet().orEmpty()

internal fun selectUniqueTarget(strategies: List<Pair<String, List<String>>>): String {
    strategies.forEach { (strategy, candidates) ->
        if (candidates.size > 1) fail("strategy '$strategy' matched ${candidates.size} methods: ${candidates.joinToString()}")
    }
    val targets = strategies.flatMap { it.second }.distinct()
    if (targets.size != 1) fail(
        if (targets.isEmpty()) "no reliable target; diagnostic strings or gate structure changed"
        else "fallbacks disagree: ${targets.joinToString()}",
    )
    return targets.single()
}

/** Conservative constant propagation over the original gate's control flow. */
internal fun validateManualGate(method: Method) {
    val body = method.implementation ?: fail("target has no implementation")
    if (method.returnType != "Z" || method.parameterTypes.isNotEmpty()) fail("unexpected signature ${method.signature()}")
    val forbiddenFlags = listOf(AccessFlags.STATIC, AccessFlags.ABSTRACT, AccessFlags.NATIVE,
        AccessFlags.SYNCHRONIZED, AccessFlags.DECLARED_SYNCHRONIZED)
    if (forbiddenFlags.any { it.isSet(method.accessFlags) }) fail("expected an ordinary instance method")
    if (body.registerCount < 2) fail("no local register available without overwriting the receiver")
    if (body.tryBlocks.isNotEmpty()) fail("exception regions need a new analysis")
    val instructions = body.instructions.toList()
    if (instructions.isEmpty()) fail("empty gate")
    if (!method.exactStrings().containsAll(GATE_PROVENANCE + AUDIO_FEATURE + FLAG_DISABLED)) fail("missing gate provenance or feature/flag anchors")

    val featureIndex = instructions.indexOfFirst { it.stringValue() == AUDIO_FEATURE }
    val featureRegister = (instructions[featureIndex] as? OneRegisterInstruction)?.registerA
    val featureCall = instructions.getOrNull(featureIndex + 1)
    val reference = (featureCall as? ReferenceInstruction)?.reference as? MethodReference
    if (reference?.definingClass != "Landroid/content/pm/PackageManager;" ||
        reference.name != "hasSystemFeature" || reference.returnType != "Z" ||
        reference.parameterTypes.map { it.toString() } != listOf("Ljava/lang/String;") ||
        featureCall !is FiveRegisterInstruction || featureCall.registerCount != 2 ||
        featureCall.registerD != featureRegister
    ) fail("audio feature is not passed to PackageManager.hasSystemFeature as expected")
    val result = instructions.getOrNull(featureIndex + 2)
    val branch = instructions.getOrNull(featureIndex + 3)
    if (result?.opcode != Opcode.MOVE_RESULT ||
        branch?.opcode !in setOf(Opcode.IF_EQZ, Opcode.IF_NEZ) ||
        (result as? OneRegisterInstruction)?.registerA != (branch as? OneRegisterInstruction)?.registerA
    ) fail("system feature result does not control a boolean branch")

    val addressToIndex = mutableMapOf<Int, Int>()
    val addresses = IntArray(instructions.size)
    var address = 0
    instructions.forEachIndexed { index, instruction ->
        addresses[index] = address
        addressToIndex[address] = index
        address += instruction.codeUnits
    }
    val successors = instructions.mapIndexed { index, instruction ->
        val opcode = instruction.opcode
        if (opcode.name.contains("SWITCH") || opcode.name.contains("PAYLOAD") ||
            opcode.name.startsWith("IPUT") || opcode.name.startsWith("SPUT") ||
            opcode == Opcode.MONITOR_ENTER || opcode == Opcode.MONITOR_EXIT
        ) fail("unexpected state changes or control flow: $opcode")
        when {
            opcode == Opcode.RETURN -> emptyList()
            opcode.name.startsWith("RETURN") || opcode == Opcode.THROW -> fail("unexpected exit: $opcode")
            instruction is OffsetInstruction -> {
                val target = addressToIndex[addresses[index] + instruction.codeOffset] ?: fail("invalid branch target")
                if (target <= index) fail("backward branches require re-analysis")
                when (opcode) {
                    Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32 -> listOf(target)
                    Opcode.IF_EQ, Opcode.IF_NE, Opcode.IF_LT, Opcode.IF_GE,
                    Opcode.IF_GT, Opcode.IF_LE, Opcode.IF_EQZ, Opcode.IF_NEZ,
                    Opcode.IF_LTZ, Opcode.IF_GEZ, Opcode.IF_GTZ, Opcode.IF_LEZ ->
                        listOf(index + 1, target)
                    else -> fail("unexpected branch opcode $opcode")
                }
            }
            else -> listOf(index + 1)
        }.also { next -> if (next.any { it !in instructions.indices }) fail("control flow falls outside the method") }
    }

    // A shared RETURN may receive both 0 and 1 from disjoint predecessors.
    // Preserve each feasible forward path and its diagnostic provenance rather
    // than discarding the value when two paths join at a common instruction.
    data class PathState(val index: Int, val registers: List<Long?>, val diagnostics: Set<Int>)

    val expectations = mapOf(COUNTRY_DISABLED to 0L, FLAG_DISABLED to 0L, FEATURE_ENABLED to 1L,
        "Call recording is enabled by OEM override" to 1L, "Call recording is supported" to 1L,
        "Call recording disabled by fermat" to 0L)
    val diagnosticExpectations = instructions.indices.mapNotNull { index ->
        expectations[instructions[index].stringValue()]?.let { index to it }
    }.toMap()
    val observedDiagnostics = mutableSetOf<Int>()
    val returnValues = mutableSetOf<Long>()
    val pending = ArrayDeque<PathState>().apply {
        add(PathState(0, List(body.registerCount) { null }, emptySet()))
    }
    val visited = mutableSetOf<PathState>()

    while (pending.isNotEmpty()) {
        val path = pending.removeFirst()
        if (!visited.add(path)) continue
        if (visited.size > 16384) fail("gate control flow exceeds path-analysis limit")

        val index = path.index
        val instruction = instructions[index]
        val diagnostics = if (index in diagnosticExpectations) {
            observedDiagnostics.add(index)
            path.diagnostics + index
        } else path.diagnostics

        if (instruction.opcode == Opcode.RETURN) {
            val register = (instruction as OneRegisterInstruction).registerA
            val value = path.registers.getOrNull(register)
                ?.takeIf { it == 0L || it == 1L }
                ?: fail("return value is not a proven boolean constant")
            diagnostics.forEach { diagnosticIndex ->
                if (value != diagnosticExpectations.getValue(diagnosticIndex)) {
                    fail(
                        "diagnostic '${instructions[diagnosticIndex].stringValue()}' has unexpected return semantics " +
                            "(expected=${diagnosticExpectations.getValue(diagnosticIndex)}, actual=$value, " +
                            "returnRegister=$register, registers=${path.registers}, " +
                            "diagnostics=${diagnostics.map { instructions[it].stringValue() }})",
                    )
                }
            }
            returnValues.add(value)
            continue
        }

        val registers = path.registers.toMutableList()
        if (instruction.opcode.setsRegister()) {
            val register = (instruction as? OneRegisterInstruction)?.registerA
                ?: fail("unsupported register write")
            if (register !in registers.indices) fail("invalid register")
            registers[register] = when {
                instruction.opcode in setOf(Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST) ->
                    (instruction as WideLiteralInstruction).wideLiteral
                instruction.opcode in setOf(Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16) -> {
                    val source = (instruction as TwoRegisterInstruction).registerB
                    if (source !in registers.indices) fail("invalid source register")
                    registers[source]
                }
                else -> null
            }
            if (instruction.opcode.setsWideRegister()) {
                if (register + 1 !in registers.indices) fail("invalid wide register")
                registers[register + 1] = null
            }
        }
        val nextRegisters = registers.toList()
        successors[index].forEach { next ->
            if (pending.size >= 16384) fail("gate path queue exceeds analysis limit")
            pending.add(PathState(next, nextRegisters, diagnostics))
        }
    }
    if (returnValues != setOf(0L, 1L)) fail("expected both disabled and enabled return paths")
    if (observedDiagnostics != diagnosticExpectations.keys) fail("unreachable recording diagnostic")
}

private fun fail(reason: String): Nothing = throw PatchException("CanRecord: $reason; refusing to guess")
