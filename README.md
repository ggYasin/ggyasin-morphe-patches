# ggyasin-morphe-patches

Local [Morphe](https://github.com/MorpheApp) patches for Android apps.

## Add to Morphe

[Add this patch source to Morphe](https://morphe.software/add-source?github=ggYasin/ggyasin-morphe-patches)

Or in Morphe Manager open **Sources**, tap **+**, choose **Remote**, and enter
`github.com/ggYasin/ggyasin-morphe-patches`.

The source follows the standard release flow: stable patches come from `main`, and
turning on **Pre-release patches** in the source's options follows `dev`.

## Compatibility

- App: ZenSMS
- Package: `com.zensms.app`
- Version: `1.2.04` (`141`)
- Input format: XAPK

- App: Offline Games
- Package: `com.JindoBlu.OfflineGames`
- Version: `3.14.1` (`3204`)
- Architecture: `armeabi-v7a`
- Input format: XAPK

## Patches

<!-- PATCHES_START EXPANDED -->

<!-- Generated from patches-list.json on release. Do not edit by hand. -->

<!-- PATCHES_END -->

## Patch details

### Enable premium state

Makes ZenSMS's synchronous and reactive premium-state checks report enabled.

### Expanded OTP detection

Keeps ZenSMS's original OTP handler and extends only its existing extractor.
Three stock regex slots gain universal one-time-password wording plus Persian
`کد`, `رمز`, and `رمز پویا` contexts, including Persian and Arabic-Indic digits.
The stock candidate validator remains in control, with a maximum length of ten
characters. Each expanded regex rejects its match when `تخفیف` occurs anywhere
in the SMS body. ZenSMS's other stock regex and fallback paths are unchanged.

The patterns avoid Java's unsupported `UNICODE_CHARACTER_CLASS` flag and use
explicit Persian/Arabic character boundaries where needed.

The patch has no OTP runtime extension, custom handler, configuration option,
or injected validator instructions. It changes three existing regex constants
and the stock validator's maximum-length constant.

### RTL SMS lists

Adds one switch at the bottom of **Settings → Appearance**:

- **RTL conversation list** mirrors conversation rows and previews while keeping
  contact names and phone numbers left to right and physically right-aligned.

The patch is selected by default. Its in-app switch defaults to off and stores
its state in app-private local preferences.

### In-house ad only

Stops Offline Games from requesting rewarded ads, so the game always falls back
to its own in-house ad instead of the game's ad network. The request call is the
only thing removed; the surrounding flow and the code that runs afterwards are
untouched, and banners and interstitials keep working.

### Instant in-house ad close

Removes the in-house ad's countdown so its close button is usable straight away,
and shortens the in-house ad timer to one second. This is the game's own house
ad, the cross-promotion shown when a rewarded ad cannot be loaded, not a
third-party ad-provider timer.

The countdown value is deserialized from the popup prefab, so it is stepped past
rather than shortened: the counter coroutine branches straight to the block that
enables the close control, instead of running one one-second tween per remaining
second.

Both Offline Games patches are opt-in, and both support only `3.14.1` (`3204`)
for ARMv7. Each verifies the exact `libil2cpp.so` size and SHA-256, requires its
ARM instruction window to appear exactly once at the expected offset, and reads
the result back before writing. Any unexpected binary is rejected unchanged.

The three ZenSMS patches are selected by default. The Offline Games patches are
opt-in.

## Build

Run the **Build MPP** GitHub Actions workflow, or build locally with:

```shell
./gradlew :extensions:extension:testDebugUnitTest :patches:buildAndroid
```

The bundle is written under `patches/build/libs/`. See [LAB_GUIDE.md](LAB_GUIDE.md)
for local import and test-device instructions.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
