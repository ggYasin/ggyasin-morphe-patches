# ggyasin-morphe-patches

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

Keeps ZenSMS's original OTP handler and extends only its existing extractor.
Three stock regex slots gain universal one-time-password wording plus Persian
`کد`, `رمز`, and `رمز پویا` contexts, including Persian and Arabic-Indic digits.
The stock candidate validator remains in control, with a maximum length of ten
characters and `تخفیف` added to its nearby-context ignore terms.

The patch has no OTP runtime extension, custom handler, configuration option,
or whole-message interception.

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
