package app.template.patches.zensms

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.ZEN_SMS_COMPATIBILITY
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val RTL_EXTENSION = "Lapp/patchlab/extension/rtl/RtlSmsLayout;"
private const val COMPOSER = "Landroidx/compose/runtime/Composer;"

@Suppress("unused")
val rtlZenSmsLayoutPatch = bytecodePatch(
    name = "RTL SMS lists",
    description =
        "Adds independent RTL layout switches for conversation rows and messages.",
    default = false,
) {
    compatibleWith(ZEN_SMS_COMPATIBILITY)
    extendWith("extensions/extension.mpe")

    execute {
        addSettingsSwitches()
        wrapConversationRows()
        wrapMessageBubbleContent()
        wrapMessageMetadata()
    }
}

context(_: BytecodePatchContext)
private fun addSettingsSwitches() {
    val method = RtlAppearanceSettingsFingerprint.method
    val textSizeItemIndex = method.instructions.indexOfLast { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        reference?.definingClass == "Lcom/zensms/app/ui/settings/p;" &&
            reference.name == "k"
    }
    check(textSizeItemIndex >= 0) { "Could not find the Text Size settings item" }

    // v5 is the active Composer at the final item in this exact 1.2.04 lambda.
    method.addInstruction(
        textSizeItemIndex + 1,
        "invoke-static {v5}, $RTL_EXTENSION->renderSettings(Ljava/lang/Object;)V",
    )
}

context(_: BytecodePatchContext)
private fun wrapConversationRows() {
    val method = RtlConversationItemFingerprint.method
    val beginIndex = method.instructions.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        reference?.definingClass == "Lcom/zensms/app/ui/components/w;" &&
            reference.name == "a" &&
            reference.parameterTypes.singleOrNull().toString() == COMPOSER
    }
    check(beginIndex >= 0) { "Could not find the conversation-row content anchor" }

    val endIndex = method.instructions.indexOfLast { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        instruction.opcode == Opcode.INVOKE_INTERFACE &&
            reference?.definingClass == COMPOSER &&
            reference.name == "endNode"
    }
    check(endIndex > beginIndex) { "Could not find the conversation-row closing anchor" }

    // Add the closing hook first so the earlier insertion cannot shift its index.
    method.addInstruction(
        endIndex + 1,
        "invoke-static {v12}, $RTL_EXTENSION->endDirectionProvider(Ljava/lang/Object;)V",
    )
    method.addInstruction(
        beginIndex,
        "invoke-static {v12}, $RTL_EXTENSION->beginConversationList(Ljava/lang/Object;)V",
    )
}

context(_: BytecodePatchContext)
private fun wrapMessageBubbleContent() {
    val method = RtlMessageBubbleContentFingerprint.method
    val beginIndex = method.instructions.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
        instruction.opcode == Opcode.SGET_OBJECT &&
            reference?.definingClass == "Landroidx/compose/ui/Modifier;" &&
            reference.name == "Companion"
    }
    check(beginIndex >= 0) { "Could not find the message-content opening anchor" }

    val traceCloseIndex = method.instructions.indexOfLast { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        reference?.definingClass == "Lz/k2;" &&
            reference.name == "j" &&
            reference.returnType == "Z"
    }
    check(traceCloseIndex > beginIndex) { "Could not find the message-content closing anchor" }

    // The instruction after k2.j is its move-result; close immediately after it.
    method.addInstruction(
        traceCloseIndex + 2,
        "invoke-static {v6}, $RTL_EXTENSION->endDirectionProvider(Ljava/lang/Object;)V",
    )
    method.addInstruction(
        beginIndex,
        "invoke-static {v6}, $RTL_EXTENSION->beginConversationMessages(Ljava/lang/Object;)V",
    )
}

context(_: BytecodePatchContext)
private fun wrapMessageMetadata() {
    val method = RtlMessageMetadataFingerprint.method
    val beginIndex = method.instructions.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
        instruction.opcode == Opcode.SGET_OBJECT &&
            reference?.definingClass == "Landroidx/compose/ui/Modifier;" &&
            reference.name == "Companion"
    }
    check(beginIndex >= 0) { "Could not find the message-metadata opening anchor" }

    val traceCloseIndex = method.instructions.indexOfLast { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        reference?.definingClass == "Lz/k2;" &&
            reference.name == "j" &&
            reference.returnType == "Z"
    }
    check(traceCloseIndex > beginIndex) { "Could not find the message-metadata closing anchor" }

    method.addInstruction(
        traceCloseIndex + 2,
        "invoke-static {v15}, $RTL_EXTENSION->endDirectionProvider(Ljava/lang/Object;)V",
    )
    method.addInstruction(
        beginIndex,
        "invoke-static {v15}, $RTL_EXTENSION->beginConversationMessages(Ljava/lang/Object;)V",
    )
}
