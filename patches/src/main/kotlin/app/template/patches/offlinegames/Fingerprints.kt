package app.template.patches.offlinegames

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.SupportedAbi
import java.io.File
import java.security.MessageDigest

internal const val LIBRARY_PATH = "lib/armeabi-v7a/libil2cpp.so"

private const val EXPECTED_LIBRARY_SIZE = 73_310_748
private const val EXPECTED_LIBRARY_SHA256 =
    "dd619f322538d339137e30a8c53e913ddecb59e78296ba86a79f058853ba0512"

internal val OFFLINE_GAMES_COMPATIBILITY = Compatibility(
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

/**
 * An ARM32 instruction window this project rewrites, with the file offset it is
 * expected at and why the window is unique.
 *
 * The window has to occur exactly once and at [expectedOffset], so a changed or
 * repacked library is rejected instead of being patched blindly.
 */
internal class InstructionPatch(
    val name: String,
    val expectedOffset: Int,
    val original: ByteArray,
    val replacement: ByteArray,
) {
    init {
        require(original.size == replacement.size) {
            "$name must not change the instruction size"
        }
    }
}

/**
 * Rewarded-ad request gate in `GameViewExt.ShowRewardedAd`.
 *
 *     cmp   r0, #0            ; result of the ad-availability check
 *     beq   finish            ; nothing to request, go to the shared tail call
 *     ldrb  r0, [r6]          ; one-shot metadata initialisation
 *     ...
 *     blx   r2                ; the ad network's ShowRewardedAd
 *   finish:
 *     ...
 *
 * Turning `beq` into an unconditional branch keeps the shared tail call and drops
 * the only call into the ad network, so no rewarded ad is ever requested and the
 * game's own in-house fallback always runs. The now-unreachable block and the
 * preceding check are left as they are.
 */
internal val rewardedAdRequestGate = InstructionPatch(
    name = "rewarded-ad request gate",
    expectedOffset = 0x17EFE6C,
    original = hex("00 00 50 e3 12 00 00 0a 00 00 d6 e5 00 00 50 e3 04 00 00 1a"),
    replacement = hex("00 00 50 e3 12 00 00 ea 00 00 d6 e5 00 00 50 e3 04 00 00 1a"),
)

/**
 * House-ad countdown entry, the first block of the counter coroutine that
 * `HouseAdPopupView` runs. The counter at `+0x4C` is deserialized from the popup
 * prefab, so its value cannot be reduced here; it only has to be stepped past.
 *
 *     ldr   r0, [r5, #0x4c]   ; countdown in seconds
 *     cmp   r0, #0
 *     bgt   wait_one_second   ; loop body, one 1.0 s tween per second
 *     b     finished          ; counter already spent
 *   ...
 *   finished:
 *     ldr   r5, [r5, #0x40]   ; the close control, enabled as soon as the
 *     ...                     ; countdown ends
 *
 * Branching straight to `finished` enables the close control immediately, so the
 * in-house ad can be skipped before the countdown would have run out. The null
 * check on the popup view and the `finished` block itself are untouched.
 */
internal val houseAdCountdownEntry = InstructionPatch(
    name = "house-ad countdown entry",
    expectedOffset = 0x15B79BC,
    original = hex("4c 00 95 e5 00 00 50 e3 05 00 00 ca 16 00 00 ea"),
    replacement = hex("4c 00 95 e5 00 00 50 e3 17 00 00 ea 16 00 00 ea"),
)

/**
 * House-ad timer limit selection, which reads `GlobalDebugTools.FasterHouseAds`
 * and picks 15 seconds normally or 3 seconds when that developer flag is on.
 *
 *     mov    r1, #3           ; limit used when the flag is set
 *     cmp    r4, #0
 *     mov    r2, r7
 *     moveq  r1, #0xf         ; limit used when the flag is clear
 *
 * Both immediates become 1, so the limit no longer depends on the flag.
 */
internal val houseAdTimerLimit = InstructionPatch(
    name = "house-ad timer limit",
    expectedOffset = 0x17EFD0C,
    original = hex("03 10 a0 e3 00 00 54 e3 07 20 a0 e1 0f 10 00 03"),
    replacement = hex("01 10 a0 e3 00 00 54 e3 07 20 a0 e1 01 10 00 03"),
)

/**
 * Rewrites [patches] into [library], which is [LIBRARY_PATH] of the ARMv7
 * Offline Games 3.14.1 XAPK. That library lives in the XAPK's
 * `config.armeabi_v7a.apk` split, which the patcher resolves for us.
 *
 * The library is only written once every fingerprint has been matched, and the
 * write is read back, so an unexpected binary is never left half patched.
 */
internal fun patchIl2CppLibrary(library: File, patches: List<InstructionPatch>) {
    if (!library.isFile) {
        throw PatchException(
            "Could not find $LIBRARY_PATH. This patch requires the ARMv7 " +
                "Offline Games 3.14.1 XAPK.",
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

    patches.forEach { patch ->
        val matches = bytes.findMatches(patch.original)
        if (matches.size != 1) {
            val found = if (matches.isEmpty()) "found none" else "found 2 or more"
            throw PatchException(
                "Expected exactly one ${patch.name} signature, but $found. " +
                    "The library was left unchanged.",
            )
        }

        val offset = matches.single()
        if (offset != patch.expectedOffset) {
            throw PatchException(
                "${patch.name} moved unexpectedly: expected 0x" +
                    patch.expectedOffset.toString(16) +
                    ", found 0x" + offset.toString(16) + ".",
            )
        }

        patch.replacement.copyInto(bytes, offset)
    }

    if (!patches.all { bytes.matchesAt(it.expectedOffset, it.replacement) }) {
        throw PatchException("Could not write the patched instructions.")
    }

    library.writeBytes(bytes)
}

private fun ByteArray.findMatches(pattern: ByteArray, limit: Int = 2): List<Int> {
    if (pattern.isEmpty() || pattern.size > size) return emptyList()

    val matches = mutableListOf<Int>()
    val lastStart = size - pattern.size
    for (start in 0..lastStart) {
        if (this[start] != pattern[0]) continue
        if (!matchesAt(start, pattern)) continue

        matches += start
        if (matches.size >= limit) break
    }
    return matches
}

private fun ByteArray.matchesAt(offset: Int, expected: ByteArray): Boolean {
    for (index in expected.indices) {
        if (this[offset + index] != expected[index]) return false
    }
    return true
}

internal fun hex(value: String): ByteArray {
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
