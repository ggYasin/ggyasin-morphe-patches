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

- App: 9GAG
- Package: `com.ninegag.android.app`
- Version: `8.23.0` (`80230000`)
- Input format: APK

## Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.4.0-dev.2](https://github.com/ggYasin/ggyasin-morphe-patches/releases/tag/v1.4.0-dev.2)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;7 patches total
<details open>
<summary>📦 9GAG&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 8.23.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Deactivate Firebase Analytics (9GAG 8.23.0)](#deactivate-firebase-analytics-9gag-8-23-0) | Optional: disables Firebase Analytics collection using its documented manifest setting. Does not remove Firebase services. |  |
| [Remove 9GAG ads, promoted posts and trackers (8.23.0)](#remove-9gag-ads-promoted-posts-and-trackers-8-23-0) | Disables ad gates and bottom-banner initialization, filters promoted feed posts, and blocks listed ad/tracking hosts. |  |

</details>

<details open>
<summary>📦 ZenSMS&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 1.2.04 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Expanded OTP detection](#expanded-otp-detection) | Extends ZenSMS's original OTP extractor with universal and Persian patterns. |  |
| [RTL SMS lists](#rtl-sms-lists) | Adds RTL conversation rows while keeping conversation titles left to right. |  |
| [enable premium state](#enable-premium-state) | Makes synchronous and reactive premium checks report true. |  |

</details>

<details open>
<summary>📦 Offline Games&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 3.14.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [In-house ad only](#in-house-ad-only) | Stops Offline Games from requesting rewarded ads, so the game always falls back to its own in-house ad. Banners and interstitials are untouched. |  |
| [Instant in-house ad close](#instant-in-house-ad-close) | Removes the in-house ad countdown so its close button is usable straight away, and shortens the in-house ad timer to one second. |  |

</details>

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

### Remove 9GAG ads, promoted posts and trackers (8.23.0)

The main 9GAG patch, selected by default. Derived from the Adobo patch project
under the same GPLv3 licence; see [NOTICE](NOTICE) for attribution and the
upstream baseline commit.

It does four things:

- **Disables the ad gate.** The boolean state-flow gate and its
  `invokeSuspend` continuation both return `false`.
- **Filters promoted feed posts.** It forces the existing hide-promoted argument
  of the display query `Lhx3;->j(ILjava/lang/String;Z)Ljava/util/List;` to true,
  reusing 9GAG's own filter rather than editing the underlying `d()` query
  builder, whose other callers perform database maintenance.
- **Stops the bottom banner.** The dedicated bottom-adhesion initializer
  `Lo02;->i(Landroid/widget/FrameLayout;Lmc;)V` is made to set its supplied
  frame to `GONE`, clear its children and return, after which four compiled
  layouts also set the banner container to zero height and `gone`.
- **Blocks ad and tracking hosts.** A fixed list of ad, analytics and attribution
  hosts is rewritten to `0.0.0.0` in both string constants and bytecode string
  references. This is a finite list, not proof that every tracker is blocked.

Every fingerprint is validated against the exact instruction shape before it is
edited, and each edit is required to match exactly one method, so a changed
build is rejected rather than patched blindly.

### Deactivate Firebase Analytics (9GAG 8.23.0)

Optional and disabled by default. Sets the SDK's documented
`firebase_analytics_collection_deactivated` manifest flag. Firebase
initialization, services, messaging and Remote Config stay intact, and no
Firebase service is removed.

## Build

Run the **Build MPP** GitHub Actions workflow, or build locally with:

```shell
./gradlew :extensions:extension:testDebugUnitTest :patches:buildAndroid
```

The bundle is written under `patches/build/libs/`. See [LAB_GUIDE.md](LAB_GUIDE.md)
for local import and test-device instructions.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
