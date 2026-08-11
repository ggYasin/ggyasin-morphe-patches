package app.template.patches.offlinegames

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.SupportedAbi
import app.morphe.patcher.patch.rawResourcePatch
import java.security.MessageDigest

private const val LIBRARY_PATH = "lib/armeabi-v7a/libil2cpp.so"
private const val EXPECTED_LIBRARY_SIZE = 73_310_748
private const val EXPECTED_PATTERN_OFFSET = 0x17EFD0C
private const val COUNTDOWN_INSTRUCTION_OFFSET = 12

private const val EXPECTED_LIBRARY_SHA256 =
    "dd619f322538d339137e30a8c53e913ddecb59e78296ba86a79f058853ba0512"
private const val EXPECTED_PATCHED_LIBRARY_SHA256 =
    "cb47f0498be4c13765e6364ed99fae6c889339d07ddc5a37b1790228cb0c272f"

/**
 * ARM32 instructions in GameViewExt.ShowRewardedAd's local house-ad path:
 *
 *     mov     r1, #3
 *     cmp     r4, #0
 *     mov     r2, r7
 *     movweq  r1, #15
 */
private val ORIGINAL_PATTERN = decodeHex(
    "03 10 A0 E3 00 00 54 E3 07 20 A0 E1 0F 10 00 03",
)

/** ARM32: movweq r1, #1. */
private val ONE_SECOND_INSTRUCTION = decodeHex("01 10 00 03")

private val OFFLINE_GAMES_COMPATIBILITY = Compatibility(
    name = "Offline Games",
    packageName = "com.JindoBlu.OfflineGames",
    apkFileType = ApkFileType.XAPK_REQUIRED,
    appIconColor = 0x263238,
    targets = listOf(
        AppTarget(
            version = "3.14.1",
            versionCodes = mapOf(SupportedAbi.ARMEABI_V7A to 3204),
        ),
    ),
)

@Suppress("unused")
val houseAdCountdownPatch = rawResourcePatch(
    name = "One-second house-ad countdown",
    description = "Changes Offline Games' built-in house-ad countdown from 15 seconds to 1.",
    default = false,
) {
    compatibleWith(OFFLINE_GAMES_COMPATIBILITY)

    execute {
        val library = this[LIBRARY_PATH]
        if (!library.isFile) {
            throw PatchException(
                "Could not find $LIBRARY_PATH. This patch requires the ARMv7 XAPK.",
            )
        }

        val bytes = library.readBytes()
        if (bytes.size != EXPECTED_LIBRARY_SIZE) {
            throw PatchException(
                "Unsupported libil2cpp.so size. Expected $EXPECTED_LIBRARY_SIZE bytes, " +
                    "but found ${bytes.size}.",
            )
        }

        val actualHash = bytes.sha256()
        if (actualHash != EXPECTED_LIBRARY_SHA256) {
            throw PatchException(
                "Unsupported libil2cpp.so. Expected SHA-256 $EXPECTED_LIBRARY_SHA256, " +
                    "but found $actualHash.",
            )
        }

        val matches = bytes.findPatternMatches(ORIGINAL_PATTERN, maximumMatches = 2)
        if (matches.size != 1) {
            throw PatchException(
                "Expected exactly one house-ad countdown signature, but found ${matches.size}.",
            )
        }

        val patternOffset = matches.single()
        if (patternOffset != EXPECTED_PATTERN_OFFSET) {
            throw PatchException(
                "House-ad signature moved unexpectedly: expected 0x" +
                    EXPECTED_PATTERN_OFFSET.toString(16) +
                    ", found 0x${patternOffset.toString(16)}.",
            )
        }

        ONE_SECOND_INSTRUCTION.copyInto(
            destination = bytes,
            destinationOffset = patternOffset + COUNTDOWN_INSTRUCTION_OFFSET,
        )

        val patchedHash = bytes.sha256()
        if (patchedHash != EXPECTED_PATCHED_LIBRARY_SHA256) {
            throw PatchException(
                "Patched libil2cpp.so failed verification. Expected SHA-256 " +
                    "$EXPECTED_PATCHED_LIBRARY_SHA256, but found $patchedHash.",
            )
        }

        library.writeBytes(bytes)
    }
}

private fun decodeHex(value: String): ByteArray {
    val normalized = value.filterNot(Char::isWhitespace)
    require(normalized.length % 2 == 0) { "Hex input must contain complete bytes" }

    return ByteArray(normalized.length / 2) { index ->
        normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }

private fun ByteArray.findPatternMatches(
    pattern: ByteArray,
    maximumMatches: Int,
): List<Int> {
    if (pattern.isEmpty() || pattern.size > size || maximumMatches < 1) return emptyList()

    val matches = mutableListOf<Int>()
    val lastStart = size - pattern.size
    for (start in 0..lastStart) {
        if (this[start] != pattern[0]) continue

        var index = 1
        while (index < pattern.size && this[start + index] == pattern[index]) index++
        if (index != pattern.size) continue

        matches += start
        if (matches.size >= maximumMatches) break
    }
    return matches
}
