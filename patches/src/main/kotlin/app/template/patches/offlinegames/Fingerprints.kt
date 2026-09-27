package app.template.patches.offlinegames

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.SupportedAbi
import java.io.File
import java.security.MessageDigest
import java.util.logging.Logger

internal const val LIBRARY_PATH = "lib/armeabi-v7a/libil2cpp.so"
private const val LIBRARY_SIZE = 73_310_748
private const val ORIGINAL_SHA256 = "dd619f322538d339137e30a8c53e913ddecb59e78296ba86a79f058853ba0512"

internal val OFFLINE_GAMES_COMPATIBILITY = Compatibility(
    name = "Offline Games",
    packageName = "com.JindoBlu.OfflineGames",
    apkFileType = ApkFileType.XAPK_REQUIRED,
    appIconColor = 0x263238,
    targets = listOf(AppTarget(version = "3.14.1", versionCodes = mapOf(SupportedAbi.ARMEABI_V7A to 3204))),
)

/** Offsets are file offsets, resolved independently using IL2CPP method-token RIDs. */
internal class NativeEdit(val name: String, val offset: Int, original: String, replacement: String) {
    val original = hex(original)
    val replacement = hex(replacement)
    init {
        require(this.original.size == this.replacement.size)
    }
}

// In ShowRewardedAd, the closure and its callback are already initialized. Route both
// ready/not-ready results to the existing showHouseAd closure (0x17efa6c), via 0x16f4924.
internal val rewardedAdFallback = NativeEdit(
    "GameViewExt.ShowRewardedAd -> showHouseAd", 0x16F47E8,
    "06 00 00 0a", "4d 00 00 ea",
)
// Dedicated rewarded adapter; banners/interstitial adapters use other implementations.
internal val rewardedAdDownload = NativeEdit(
    "ApplovinRewardedAd.LoadMaxSdkAd", 0x12A22F8,
    "30 48 2d e9", "1e ff 2f e1",
)

// HouseAdPopupView.Open: SetActive(counter > 0) on secondsTextWrapper (+0x38).
internal val houseAdHideCounter = NativeEdit(
    "HouseAdPopupView.Open hide seconds wrapper", 0x15B6EB0,
    "01 10 00 c3", "00 10 a0 e3",
)
// HouseAdPopupView.Open: SetActive(counter == 0) on closeButton (+0x40).
internal val houseAdShowClose = NativeEdit(
    "HouseAdPopupView.Open show closeButton", 0x15B6EDC,
    "a0 12 a0 e1", "01 10 a0 e3",
)
// <>c__DisplayClass15_0.<Open>b__0 copies duration into counter (+0x4c).
// Zeroing only that argument preserves the completion callback and product selection.
internal val houseAdCounter = NativeEdit(
    "HouseAdPopupView.Open initialize counter", 0x15B78AC,
    "08 60 90 e5", "00 60 a0 e3",
)
// Token 0x060008bf: the real click handler. ClosePressed (0x15b7750) is preserved.
internal val houseAdStoreRedirect = NativeEdit(
    "HouseAdPopupView.OpenStorePage", 0x15B77AC,
    "30 48 2d e9", "1e ff 2f e1",
)

private val currentEdits = listOf(
    rewardedAdFallback, rewardedAdDownload, houseAdHideCounter,
    houseAdShowClose, houseAdCounter, houseAdStoreRedirect,
)

// Restore obsolete changes when upgrading an output from 1.2.x–1.4.1. In particular,
// 0x15b7aa4 is IEnumerator.Reset (not OpenStorePage), and 0x17efe70 belongs to the
// house-ad completion callback (not the rewarded request decision).
private val retiredEdits = listOf(
    NativeEdit("legacy completion callback", 0x17EFE70, "12 00 00 0a", "12 00 00 ea"),
    NativeEdit("legacy debug duration", 0x17EFD0C, "03 10 a0 e3", "01 10 a0 e3"),
    NativeEdit("legacy normal duration", 0x17EFD18, "0f 10 00 03", "01 10 00 03"),
    NativeEdit("legacy subtraction", 0x15B7998, "01 00 40 e2", "0f 00 40 e2"),
    NativeEdit("legacy coroutine branch", 0x15B79C4, "05 00 00 ca", "17 00 00 ea"),
    NativeEdit("legacy tick", 0x15B79F0, "fe 15 a0 e3", "3b 14 a0 e3"),
    NativeEdit("legacy IEnumerator.Reset", 0x15B7AA4, "10 40 2d e9", "1e ff 2f e1"),
)

/** Accept only the original binary plus precisely the edits this repository has shipped. */
internal fun patchIl2CppLibrary(library: File, selected: List<NativeEdit>) {
    if (!library.isFile) throw PatchException("Missing $LIBRARY_PATH. Select the complete ARMv7 XAPK/APKS.")
    val bytes = library.readBytes()
    if (bytes.size != LIBRARY_SIZE) throw PatchException("Unsupported ARMv7 library size: ${bytes.size}.")
    val normalized = bytes.copyOf()
    (currentEdits + retiredEdits).forEach { edit ->
        if (!bytes.matchesAt(edit.offset, edit.original) && !bytes.matchesAt(edit.offset, edit.replacement)) {
            throw PatchException("Unexpected bytes at ${edit.name} (0x${edit.offset.toString(16)}). Use the original 3.14.1 ARMv7 bundle.")
        }
        edit.original.copyInto(normalized, edit.offset)
    }
    if (normalized.sha256() != ORIGINAL_SHA256) {
        throw PatchException("Unknown libil2cpp.so modifications outside supported edits. SHA-256: ${bytes.sha256()}")
    }
    retiredEdits.forEach { it.original.copyInto(bytes, it.offset) }
    selected.forEach { it.replacement.copyInto(bytes, it.offset) }
    library.writeBytes(bytes)
    check(library.readBytes().contentEquals(bytes)) { "Native library read-back failed" }
    Logger.getLogger("PatchLabOfflineGames").info("Verified ARMv7 native output SHA-256: ${bytes.sha256()}")
}

private fun ByteArray.matchesAt(offset: Int, expected: ByteArray) =
    expected.indices.all { this[offset + it] == expected[it] }

private fun hex(text: String) = text.split(' ').map { it.toInt(16).toByte() }.toByteArray()

private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
