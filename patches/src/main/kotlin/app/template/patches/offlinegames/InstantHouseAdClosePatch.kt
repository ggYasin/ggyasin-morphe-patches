package app.template.patches.offlinegames

import app.morphe.patcher.patch.rawResourcePatch

/**
 * Removes the wait three independent ways at once, so the in-house ad's close
 * button becomes usable straight away. The three edits are kept together
 * because none of them changes the popup's layout, its label or its close
 * button, and having all three applied means a single missed edit cannot leave
 * the ad unusable.
 *
 * The counter itself is deserialized from the popup prefab, so it cannot be
 * reduced from code. Instead the wait is removed: the loop is skipped, the
 * per-tick subtraction covers the whole counter, and the per-tick wait is
 * shortened. The close control is still enabled by the game's own finish block.
 */
@Suppress("unused")
val instantHouseAdClosePatch = rawResourcePatch(
    name = "Instant in-house ad close",
    description = "Removes the in-house ad countdown so its close button is usable " +
        "straight away. Applies three independent bypasses of the wait: skipping " +
        "the loop, subtracting the whole counter each tick, and shortening the " +
        "tick. Also shortens the in-house ad timer to one second.",
    default = false,
) {
    compatibleWith(OFFLINE_GAMES_COMPATIBILITY)

    execute {
        patchIl2CppLibrary(
            this[LIBRARY_PATH],
            HouseAdCountdown.all + houseAdTimerLimit,
        )
    }
}
