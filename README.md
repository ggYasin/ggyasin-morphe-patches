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

### RTL SMS lists

Adds one switch at the bottom of **Settings → Appearance**:

- **RTL conversation list** mirrors conversation rows and previews while keeping
  contact names and phone numbers left to right and physically right-aligned.

The switch defaults to off and stores its state in app-private local preferences.

## Build

Run the **Build MPP** GitHub Actions workflow, or build locally with:

```shell
./gradlew :patches:buildAndroid
```

The bundle is written under `patches/build/libs/`. See [LAB_GUIDE.md](LAB_GUIDE.md)
for local import and test-device instructions.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
