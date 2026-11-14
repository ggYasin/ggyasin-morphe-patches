package app.template.patches.ninegag.ad

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.template.patches.ninegag.util.returnBoxedBooleanEarly
import app.template.patches.ninegag.util.returnEarly
import app.template.patches.ninegag.hosts.HostsBlocker
import app.template.patches.ninegag.hosts.HostsBlockerConfig
import app.template.patches.ninegag.hosts.baseHostsBlockerPatch
import app.template.patches.ninegag.shared.COMPATIBILITY_NINEGAG
import app.template.patches.ninegag.shared.NINEGAG_SYMBOLS
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove 9GAG ads, promoted posts and trackers",
    description = "Disables ad gates and bottom-banner initialization, filters promoted feed posts, and blocks listed ad/tracking hosts."
) {
    compatibleWith(COMPATIBILITY_NINEGAG)

    dependsOn(
        baseHostsBlockerPatch {
            HostsBlockerConfig(
                hostsBlocker = HostsBlocker.fromString(AD_HOSTS)
            )
        },
        hideAdContainersPatch,
        hidePromotedPostsPatch,
        hideBottomBannerPatch
    )

    execute {
        val symbols = NINEGAG_SYMBOLS[packageMetadata.versionName]
        if (symbols != null) {
            // 8.26.1+ uses a nullable block reason: null ENABLES ads. The shared
            // base policy feeds startup, synchronous checks and the runtime flow.
            // Return its real NotInitialized blocker; never forge entitlement.
            val reason = symbols.blockReason
            val policyClass = classDefBy(symbols.adPolicyClass)
            val policy = policyClass.methods.single {
                it.name == "b" && it.returnType == reason &&
                    it.parameterTypes.map { type -> type.toString() } ==
                    listOf("Z", symbols.paidAccess, symbols.adEntitlement) &&
                    it.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.FINAL.value)
            }
            val body = checkNotNull(policy.implementation)
            val instructions = body.instructions.toList()
            val fields = instructions.mapNotNull {
                (it as? ReferenceInstruction)?.reference as? FieldReference
            }
            val enumClass = classDefBy(reason)
            val enumStrings = enumClass.methods.flatMap { method ->
                method.implementation?.instructions?.mapNotNull {
                    ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
                } ?: emptyList()
            }
            check(body.registerCount == 3 && instructions.size == 22 &&
                instructions[20].opcode == Opcode.CONST_4 &&
                instructions[21].opcode == Opcode.RETURN_OBJECT &&
                setOf("NotInitialized", "ConsentUnresolved", "PaidAccessUnresolved", "PaidAccess", "Nsfw", "Subscription")
                    .all { it in enumStrings } &&
                fields.filter { it.definingClass == symbols.adEntitlement && it.type == "Z" }
                    .map { it.name }.toSet() == setOf("a", "b") &&
                fields.filter { it.definingClass == reason && it.type == reason }
                    .map { it.name }.toSet() == setOf("b", "c", "d", "f") &&
                enumClass.fields.any { it.name == "a" && it.type == reason } &&
                notInitializedIsFieldA(enumClass, reason)
            ) { "9GAG ${packageMetadata.versionName} ad-block policy no longer has its validated structure" }
            mutableClassDefBy(policyClass).methods.single {
                it.name == policy.name && it.parameterTypes == policy.parameterTypes &&
                    it.returnType == policy.returnType
            }.addInstructions(0, """
                sget-object p0, $reason->a:$reason
                return-object p0
            """)
        } else {
            AdGateFingerprint.method.returnEarly(false)
            RuntimeAdGateFingerprint.method.returnBoxedBooleanEarly(false)
        }
    }
}

/**
 * Confirms the enum's static initializer stores the "NotInitialized" constant
 * in field a, so the injected return is that blocker and never a guessed one.
 */
private fun notInitializedIsFieldA(enumClass: ClassDef, reason: String): Boolean {
    val instructions = enumClass.methods.singleOrNull { it.name == "<clinit>" }
        ?.implementation?.instructions?.toList() ?: return false
    val nameIndex = instructions.indexOfFirst {
        ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "NotInitialized"
    }
    if (nameIndex < 0) return false
    val store = instructions.drop(nameIndex).firstOrNull { it.opcode == Opcode.SPUT_OBJECT }
        ?: return false
    val field = (store as ReferenceInstruction).reference as? FieldReference ?: return false
    return field.definingClass == reason && field.name == "a" && field.type == reason
}
