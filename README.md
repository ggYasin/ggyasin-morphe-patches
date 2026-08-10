# PatchLab ZenSMS Patches

Local [Morphe](https://github.com/MorpheApp) patches for ZenSMS.

## Compatibility

- App: ZenSMS
- Package: `com.zensms.app`
- Version: `1.2.04` (`141`)
- Input format: XAPK

## Patches

### Enable premium state

Makes ZenSMS's synchronous and reactive premium-state checks report enabled.

### Expanded OTP detection

Improves the OTP bubble with a body-only, OTPHelper-compatible detector. The
decision order is:

1. Messages matching an ignored phrase do not show an OTP bubble.
2. OTPHelper-compatible detection wins when it finds a code.
3. Otherwise ZenSMS's original detector runs unchanged as the fallback.

The detector recognizes numeric and alphanumeric codes of four or more
characters, including spaced or hyphenated codes and Arabic-Indic or Persian
digits. Its keyword, cleanup, currency, skip, and ignore rules are built into
the patch and require no app setting or background initialization.

Morphe Expert mode exposes two optional patch-time regex lists:

- **Additional OTP phrases** extends the contexts that can introduce a code.
- **Additional ignored phrases** suppresses known false positives.

The lists are validated and embedded while patching. Changing either one
requires producing and installing a newly patched app.

### RTL SMS lists

Adds one switch at the bottom of **Settings → Appearance**:

- **RTL conversation list** mirrors conversation rows and previews while keeping
  contact names and phone numbers left to right and physically right-aligned.

The patch is selected by default. Its in-app switch defaults to off and stores
its state in app-private local preferences.

All three patches are selected by default when the bundle is loaded.

## Build

Run the **Build MPP** GitHub Actions workflow, or build locally with:

```shell
./gradlew :extensions:extension:testDebugUnitTest :patches:buildAndroid
```

The bundle is written under `patches/build/libs/`. See [LAB_GUIDE.md](LAB_GUIDE.md)
for local import and test-device instructions.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
