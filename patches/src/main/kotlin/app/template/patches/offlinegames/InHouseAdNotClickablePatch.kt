package app.template.patches.offlinegames

import app.morphe.patcher.patch.rawResourcePatch

/**
 * Makes the in-house ad inert to taps. The ad's own click handler builds an
 * Intent around a store URI and starts it, so a stray tap sends the player to
 * the Play Store. Returning from that handler immediately leaves the tap with
 * nothing to do.
 *
 * Only the redirect is removed. The ad still shows, the countdown still runs,
 * and the close button is untouched.
 */
@Suppress("unused")
val inHouseAdNotClickablePatch = rawResourcePatch(
    name = "In-house ad not clickable",
    description = "Stops the in-house ad from opening the Play Store when tapped, so " +
        "an accidental click does not leave the game. The ad and its close button " +
        "are otherwise unchanged.",
    default = false,
) {
    compatibleWith(OFFLINE_GAMES_COMPATIBILITY)

    execute {
        patchIl2CppLibrary(this[LIBRARY_PATH], listOf(houseAdStoreRedirect))
    }
}
