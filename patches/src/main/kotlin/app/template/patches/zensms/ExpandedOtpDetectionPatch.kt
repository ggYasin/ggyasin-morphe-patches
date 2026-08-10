package app.template.patches.zensms

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.newLabel
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringsOption
import app.template.patches.shared.Constants.ZEN_SMS_COMPATIBILITY
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

private const val SENSITIVE_MARKER = "__PATCHLAB_ADDITIONAL_SENSITIVE_PHRASES__"
private const val IGNORED_MARKER = "__PATCHLAB_ADDITIONAL_IGNORED_PHRASES__"

@Suppress("unused")
val expandedOtpDetectionPatch = bytecodePatch(
    name = "Expanded OTP detection",
    description = "Uses OTPHelper-compatible detection first, then falls back to ZenSMS.",
    default = true,
) {
    compatibleWith(ZEN_SMS_COMPATIBILITY)
    dependsOn(sharedZenSmsExtensionPatch())

    val additionalSensitivePhrases by stringsOption(
        key = "additionalSensitivePhrases",
        default = emptyList(),
        title = "Additional OTP phrases",
        description = "Regex fragments that identify OTP context. Applied when repatching.",
        validator = { phrases -> phrases.isValidRegexList(disallowCapturingGroups = true) },
    )
    val additionalIgnoredPhrases by stringsOption(
        key = "additionalIgnoredPhrases",
        default = emptyList(),
        title = "Additional ignored phrases",
        description = "Regex fragments that suppress OTP detection. Applied when repatching.",
        validator = { phrases -> phrases.isValidRegexList(disallowCapturingGroups = false) },
    )

    execute {
        replaceExtensionPhrases(
            methodName = "additionalSensitivePhrases",
            marker = SENSITIVE_MARKER,
            phrases = additionalSensitivePhrases.orEmpty(),
        )
        replaceExtensionPhrases(
            methodName = "additionalIgnoredPhrases",
            marker = IGNORED_MARKER,
            phrases = additionalIgnoredPhrases.orEmpty(),
        )
        hookZenSmsExtractor()
    }
}

private fun List<String>?.isValidRegexList(disallowCapturingGroups: Boolean): Boolean {
    if (this == null) return true

    return all { phrase ->
        if (phrase.isBlank() || '\n' in phrase || '\r' in phrase) {
            return@all false
        }
        try {
            val compiled = Pattern.compile("(?:$phrase)")
            !disallowCapturingGroups || compiled.matcher("").groupCount() == 0
        } catch (_: PatternSyntaxException) {
            false
        }
    }
}

context(context: BytecodePatchContext)
private fun replaceExtensionPhrases(
    methodName: String,
    marker: String,
    phrases: List<String>,
) {
    val extensionClass = context.mutableClassDefBy(OTP_EXTENSION_CLASS)
    val method = extensionClass.methods.single { candidate ->
        candidate.name == methodName &&
            candidate.parameterTypes.isEmpty() &&
            candidate.returnType == "Ljava/lang/String;"
    }
    val markerIndex = method.instructions.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? StringReference
        reference?.string == marker
    }
    check(markerIndex >= 0) { "Could not find OTP extension marker for $methodName" }

    val register = (method.instructions[markerIndex] as OneRegisterInstruction).registerA
    method.replaceInstruction(
        markerIndex,
        "const-string v$register, \"${phrases.joinToString("\n").toSmaliString()}\"",
    )
}

context(_: BytecodePatchContext)
private fun hookZenSmsExtractor() {
    val method = ZenSmsOtpExtractorFingerprint.method
    val implementation = checkNotNull(method.implementation) {
        "ZenSMS OTP extractor has no implementation"
    }
    val parameterWidth = method.parameters.sumOf { parameter ->
        if (parameter.type == "J" || parameter.type == "D") 2 else 1
    }
    check(implementation.registerCount - parameterWidth >= 1) {
        "ZenSMS OTP extractor has no local register for the guard"
    }

    val bodyRegister = implementation.registerCount - parameterWidth
    val shouldIgnoreReference = ImmutableMethodReference(
        OTP_EXTENSION_CLASS,
        "shouldIgnore",
        listOf("Ljava/lang/String;"),
        "Z",
    )
    val extractReference = ImmutableMethodReference(
        OTP_EXTENSION_CLASS,
        "extract",
        listOf("Ljava/lang/String;"),
        "Ljava/lang/String;",
    )

    // Build the branches directly instead of using Morphe's inline-smali
    // compiler. Its synthetic method always returns void, which can discard a
    // block containing return-object and produce "Collection is empty".
    val stockExtractorLabel = method.newLabel(0)
    implementation.addInstructions(
        0,
        listOf(
            BuilderInstruction3rc(
                Opcode.INVOKE_STATIC_RANGE,
                bodyRegister,
                1,
                extractReference,
            ),
            BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0),
            BuilderInstruction21t(Opcode.IF_EQZ, 0, stockExtractorLabel),
            BuilderInstruction11x(Opcode.RETURN_OBJECT, 0),
        ),
    )

    val tryExtractLabel = method.newLabel(0)
    implementation.addInstructions(
        0,
        listOf(
            BuilderInstruction3rc(
                Opcode.INVOKE_STATIC_RANGE,
                bodyRegister,
                1,
                shouldIgnoreReference,
            ),
            BuilderInstruction11x(Opcode.MOVE_RESULT, 0),
            BuilderInstruction21t(Opcode.IF_EQZ, 0, tryExtractLabel),
            BuilderInstruction11n(Opcode.CONST_4, 0, 0),
            BuilderInstruction11x(Opcode.RETURN_OBJECT, 0),
        ),
    )
}

private fun String.toSmaliString() = buildString(length) {
    this@toSmaliString.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
}
