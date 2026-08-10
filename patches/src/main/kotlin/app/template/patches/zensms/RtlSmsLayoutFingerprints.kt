package app.template.patches.zensms

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/** Appearance section which owns ZenSMS' built-in display settings. */
object RtlAppearanceSettingsFingerprint : Fingerprint(
    definingClass =
        "Lcom/zensms/app/ui/settings/SettingsScreenKt\$SettingsScreen\$24\$1\$4;",
    name = "invoke",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf(
        "Ljava/lang/Object;",
        "Ljava/lang/Object;",
        "Ljava/lang/Object;",
    ),
    strings = listOf(
        "\$this\$SettingsSection",
        "24-hour time format",
        "Haptic feedback",
        "Text Size",
    ),
)

/** A single row in the main conversation list. */
object RtlConversationItemFingerprint : Fingerprint(
    definingClass = "Lcom/zensms/app/ui/conversations/b;",
    name = "f",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "Z",
        "Lcom/zensms/app/data/db/ConversationEntity;",
        "Lcom/zensms/app/data/contacts/a;",
        "Z",
        "Z",
        "Z",
        "Z",
        "Lcom/zensms/app/data/sim/SimInfo;",
        "Z",
        "Z",
        "Lz/fk;",
        "Lz/fk;",
        "Landroidx/compose/runtime/Composer;",
        "I",
        "I",
    ),
    strings = listOf(
        "com.zensms.app.ui.conversations.ConversationItem (ConversationsScreen.kt:1809)",
    ),
)

/** Content hosted inside a message bubble, excluding its outer placement and shape. */
object RtlMessageBubbleContentFingerprint : Fingerprint(
    definingClass =
        "Lcom/zensms/app/ui/thread/ThreadScreenKt\$MessageBubbleSurface\$3;",
    name = "invoke",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf(
        "Ljava/lang/Object;",
        "Ljava/lang/Object;",
    ),
    strings = listOf(
        "com.zensms.app.ui.thread.MessageBubbleSurface.<anonymous> " +
            "(ThreadScreen.kt:3341)",
    ),
)

/** Timestamp, SIM and delivery-state line rendered below a message bubble. */
object RtlMessageMetadataFingerprint : Fingerprint(
    definingClass = "Lcom/zensms/app/ui/thread/f;",
    name = "a",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "Lcom/zensms/app/data/db/MessageEntity;",
        "Z",
        "Z",
        "Z",
        "Z",
        "Lcom/zensms/app/data/sim/SimInfo;",
        "Z",
        "Lz/fk;",
        "Lz/fk;",
        "Landroidx/compose/runtime/Composer;",
        "I",
    ),
    strings = listOf(
        "com.zensms.app.ui.thread.MessageBubbleMetaRow (ThreadScreen.kt:3520)",
    ),
)
