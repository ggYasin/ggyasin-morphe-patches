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

/**
 * Libraries this project has produced before, kept only to name the input in an
 * error message. The library is *not* gated on its hash: each edit already has
 * to match an exact instruction window at an exact offset, which is a stricter
 * and more precise check than a whole-file digest, and a hash gate would
 * reject an app that an earlier bundle of this project had already patched.
 */
private val KNOWN_LIBRARY_HASHES = mapOf(
    "dd619f322538d339137e30a8c53e913ddecb59e78296ba86a79f058853ba0512" to
        "the original 3.14.1 library",
    "cb47f0498be4c13765e6364ed99fae6c889339d07ddc5a37b1790228cb0c272f" to
        "a library already patched by ggyasin-morphe-patches 1.2.x",
    "f1d538cd1ba2b612165c152a4c894693d495753b9da34bcbf25b2e049c0996a0" to
        "a library already patched by In-house ad only",
    "ab3dab0e67b04d11ce4b0a63c9aa75f3b929d8e546a4112c0ceb5bba3ccbab84" to
        "a library already patched by Instant in-house ad close",
    "2a11f4585a436a38e98237ecfc6aae749dcd2be42913ddf0aebcda45d755eb45" to
        "a library already patched by both Offline Games patches",
)

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
 * One instruction rewritten inside an [InstructionPatch]'s window.
 */
internal class InstructionEdit(
    val offsetInWindow: Int,
    val original: ByteArray,
    val replacement: ByteArray,
) {
    init {
        require(original.size == replacement.size) {
            "An edit must not change the instruction size"
        }
    }
}

/**
 * An ARM32 instruction window this project rewrites, with the file offset it is
 * expected at and the edits applied inside it.
 *
 * The window identifies the code being changed: it has to appear exactly once
 * in the library, at [expectedOffset]. Edits are tracked individually so a
 * window that is already partly rewritten, because an earlier bundle of this
 * project touched only part of it, is still recognised as the same code and only
 * the missing edits are applied.
 */
internal class InstructionPatch(
    val name: String,
    val expectedOffset: Int,
    val window: ByteArray,
    val edits: List<InstructionEdit>,
) {
    init {
        edits.forEach { edit ->
            require(edit.offsetInWindow >= 0) { "$name has a negative edit offset" }
            require(edit.offsetInWindow + edit.original.size <= window.size) {
                "$name has an edit outside its window"
            }
        }
    }

    /**
     * Every byte pattern this window is allowed to look like: the original with
     * any subset of [edits] already applied. This is what makes a partly patched
     * library recognisable without weakening the check on a foreign one.
     *
     * Two edits that happened to write the same bytes would produce equal
     * patterns twice, which only costs a redundant comparison in the slow path.
     */
    val acceptableWindows: List<ByteArray> = buildList {
        for (mask in 0 until (1 shl edits.size)) {
            val variant = window.copyOf()
            edits.forEachIndexed { index, edit ->
                if (mask and (1 shl index) != 0) {
                    edit.replacement.copyInto(variant, edit.offsetInWindow)
                }
            }
            add(variant)
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
    window = hex("00 00 50 e3 12 00 00 0a 00 00 d6 e5 00 00 50 e3 04 00 00 1a"),
    edits = listOf(
        InstructionEdit(
            offsetInWindow = 4,
            original = hex("12 00 00 0a"), // beq  -> unconditional b
            replacement = hex("12 00 00 ea"),
        ),
    ),
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
 * Both immediates become 1, so the limit no longer depends on the flag. They are
 * separate edits because the 1.2.x patch series only ever changed the `moveq`
 * one, and an app carrying that change must still be recognised here.
 */
internal val houseAdTimerLimit = InstructionPatch(
    name = "house-ad timer limit",
    expectedOffset = 0x17EFD0C,
    window = hex("03 10 a0 e3 00 00 54 e3 07 20 a0 e1 0f 10 00 03"),
    edits = listOf(
        InstructionEdit(
            offsetInWindow = 0,
            original = hex("03 10 a0 e3"), // mov r1, #3
            replacement = hex("01 10 a0 e3"),
        ),
        InstructionEdit(
            offsetInWindow = 12,
            original = hex("0f 10 00 03"), // moveq r1, #15
            replacement = hex("01 10 00 03"),
        ),
    ),
)

/**
 * The house-ad countdown, as a coroutine in the popup view.
 *
 * The counter is a serialized field at `+0x4C` of the popup, and the countdown
 * label is the UI object at `+0x3C`. Every second the coroutine decrements the
 * counter, writes the new value into the label, and waits again. When the
 * counter reaches zero it runs a finish block that enables the close control at
 * `+0x40`, which is what the user sees as "can close".
 *
 * Because the counter is deserialized from the prefab, its value cannot be
 * changed from code. The three edits below therefore each remove the wait a
 * different way, so a single missed one does not leave the ad unusable.
 */
internal object HouseAdCountdown {
    /**
     * Subtract 15 per tick, so the counter reaches zero on the first tick and
     * the close control appears after one second. The countdown still runs and
     * updates its label; it just does not linger.
     */
    val perTickSubtraction = InstructionPatch(
        name = "house-ad countdown, 15 per tick",
        expectedOffset = 0x15B7990,
        window = hex(
            "4c 00 95 e5 a6 62 a0 e1 01 00 40 e2 4c 00 85 e5 05 00 a0 e1",
        ),
        edits = listOf(
            InstructionEdit(
                offsetInWindow = 8,
                original = hex("01 00 40 e2"), // sub r0, r0, #1
                replacement = hex("0f 00 40 e2"),
            ),
        ),
    )

    /**
     * Skip the wait loop altogether. The first check branches straight to the
     * finish block, so the close control is enabled on the same frame the popup
     * opens. This is the strongest of the three.
     */
    val skipWaitLoop = InstructionPatch(
        name = "house-ad countdown, no wait loop",
        expectedOffset = 0x15B79BC,
        window = hex("4c 00 95 e5 00 00 50 e3 05 00 00 ca 16 00 00 ea"),
        edits = listOf(
            InstructionEdit(
                offsetInWindow = 8,
                original = hex("05 00 00 ca"), // bgt wait_one_second
                replacement = hex("17 00 00 ea"), // b finished
            ),
        ),
    )

    /**
     * Shrink the per-tick wait from one second to about two milliseconds, so
     * the whole countdown runs inside a single frame even though the loop
     * itself is left intact. Independent of the other two: it changes the wait,
     * not the loop test.
     *
     * Only powers of two are encodable as an ARM immediate, hence 0x3B000000
     * rather than a rounder 0.01.
     */
    val fastTick = InstructionPatch(
        name = "house-ad countdown, fast tick",
        expectedOffset = 0x15B79EC,
        window = hex("ee 2d ed eb fe 15 a0 e3 00 20 a0 e3 00 50 a0 e1"),
        edits = listOf(
            InstructionEdit(
                offsetInWindow = 4,
                original = hex("fe 15 a0 e3"), // mov r1, #1.0f
                replacement = hex("3b 14 a0 e3"), // mov r1, #0.001953125f
            ),
        ),
    )

    val all = listOf(skipWaitLoop, perTickSubtraction, fastTick)
}

/**
 * The in-house ad's store redirect. It builds an Intent around a store URI and
 * starts it, so a stray tap on the ad drops the player in the Play Store.
 * Returning immediately leaves the tap with nothing to do, and does not touch
 * the ad's own layout or the close button.
 */
internal val houseAdStoreRedirect = InstructionPatch(
    name = "in-house ad store redirect",
    expectedOffset = 0x15B7AA4,
    window = hex(
        "10 40 2d e9 30 00 9f e5 00 00 9f e7 27 2d ed eb bc 2d ed eb" +
            "00 10 a0 e3 00 40 a0 e1 d3 82 77 eb 18 00 9f e5 00 00 9f e7" +
            "20 2d ed eb 00 10 a0 e1 04 00 a0 e1 62 2d ed eb 52 2c ed eb",
    ),
    edits = listOf(
        InstructionEdit(
            offsetInWindow = 0,
            original = hex("10 40 2d e9"), // push {r4, lr}
            replacement = hex("1e ff 2f e1"), // bx lr
        ),
    ),
)

/**
 * Rewrites [patches] into [library], which is [LIBRARY_PATH] of the ARMv7
 * Offline Games 3.14.1 bundle. That library lives in the XAPK's
 * `config.armeabi_v7a.apk` split or the APKS `split_config.armeabi_v7a.apk`
 * split, which the patcher resolves for us.
 *
 * Each edit is skipped when its replacement is already in place, so an app that
 * an earlier bundle of this project already patched can be patched again instead
 * of being rejected as an unknown binary. Anything whose code does not match a
 * known window is refused, and the library is only written once every edit has
 * been applied and read back, so an unexpected binary is never left half
 * patched.
 */
internal fun patchIl2CppLibrary(library: File, patches: List<InstructionPatch>) {
    if (!library.isFile) {
        throw PatchException(
            "Could not find $LIBRARY_PATH. This patch requires the ARMv7 " +
                "Offline Games 3.14.1 XAPK or APKS.",
        )
    }

    val bytes = library.readBytes()

    if (bytes.size != EXPECTED_LIBRARY_SIZE) {
        throw PatchException(
            "Unsupported libil2cpp.so size. Expected $EXPECTED_LIBRARY_SIZE bytes, " +
                "but found ${bytes.size}. This patch only supports the ARMv7 build.",
        )
    }

    val libraryHash = bytes.sha256()

    patches.forEach { patch ->
        // Fast path: the window is already where it belongs, possibly with some
        // of its edits applied by an earlier bundle of this project.
        if (patch.acceptableWindows.any { bytes.matchesAt(patch.expectedOffset, it) }) {
            patch.edits.forEach { edit ->
                val offset = patch.expectedOffset + edit.offsetInWindow
                if (!bytes.matchesAt(offset, edit.replacement)) {
                    edit.replacement.copyInto(bytes, offset)
                }
            }
            return@forEach
        }

        // Slow path: work out where the code went, so the error is actionable.
        val elsewhere = patch.acceptableWindows
            .flatMap { bytes.findMatches(it) }
            .distinct()
            .filter { it != patch.expectedOffset }

        val found = when {
            elsewhere.isEmpty() -> "it is not present anywhere in the library"
            elsewhere.size == 1 -> "it was found at 0x${elsewhere.single().toString(16)}"
            else -> "it was found at ${elsewhere.size} places"
        }

        throw PatchException(
            "Cannot patch ${patch.name}: expected its instructions at " +
                "0x${patch.expectedOffset.toString(16)}, but $found. The library was " +
                "left unchanged.\n" +
                "libil2cpp.so SHA-256 $libraryHash, which is " +
                "${KNOWN_LIBRARY_HASHES[libraryHash] ?: "not a library this project recognises"}.\n" +
                "This patch only supports Offline Games 3.14.1 for ARMv7. Use the " +
                "original, unpatched 3.14.1 XAPK or APKS.",
        )
    }

    val unwritten = patches.filter { patch ->
        patch.edits.any { edit ->
            !bytes.matchesAt(patch.expectedOffset + edit.offsetInWindow, edit.replacement)
        }
    }
    if (unwritten.isNotEmpty()) {
        throw PatchException(
            "Could not write the patched instructions for " +
                unwritten.joinToString { it.name } +
                " to libil2cpp.so (SHA-256 $libraryHash).",
        )
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
