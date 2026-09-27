package app.template.patches.offlinegames

import app.morphe.patcher.patch.rawResourcePatch

@Suppress("unused")
val instantHouseAdClosePatch = rawResourcePatch(
    name = "Instant in-house ad close",
    description = "Removes the in-house ad countdown so its close button is usable " +
        "straight away, and shortens the in-house ad timer to one second.",
    default = false,
) {
    compatibleWith(OFFLINE_GAMES_COMPATIBILITY)

    execute {
        patchIl2CppLibrary(
            this[LIBRARY_PATH],
            listOf(houseAdCountdownEntry, houseAdTimerLimit),
        )
    }
}
