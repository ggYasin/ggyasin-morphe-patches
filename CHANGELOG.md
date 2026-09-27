## [1.3.0](https://github.com/ggYasin/ggyasin-morphe-patches/compare/v1.2.4...v1.3.0) (2026-09-27)

### 🐛 Bug Fixes

* use one hex string per fingerprint ([0a33385](https://github.com/ggYasin/ggyasin-morphe-patches/commit/0a333855f24e7a5462dad8406f9a29252e973e40))

### ✨ New Features

* add Offline Games in-house ad patches ([a364456](https://github.com/ggYasin/ggyasin-morphe-patches/commit/a3644569b757de50357e1f12587fdb736ff0f3d3))

## [1.3.0-dev.1](https://github.com/ggYasin/ggyasin-morphe-patches/compare/v1.2.4...v1.3.0-dev.1) (2026-09-27)

### 🐛 Bug Fixes

* use one hex string per fingerprint ([0a33385](https://github.com/ggYasin/ggyasin-morphe-patches/commit/0a333855f24e7a5462dad8406f9a29252e973e40))

### ✨ New Features

* add Offline Games in-house ad patches ([a364456](https://github.com/ggYasin/ggyasin-morphe-patches/commit/a3644569b757de50357e1f12587fdb736ff0f3d3))

# 1.2.4

- Make each of the three expanded OTP regexes reject messages containing `تخفیف` anywhere in the SMS body.
- Remove the separate nearby-context validator injection while preserving ZenSMS's stock handler and validator.

# 1.2.2

- Fix ZenSMS SMS reception by removing the unsupported Java `UNICODE_CHARACTER_CLASS` regex flag; Android boundaries are already Unicode-aware.

# 1.2.1

- Fix ZenSMS startup verification by preserving the stock validator's live boolean register when adding the Persian ignore term.

# 1.2.0

- Add an opt-in Offline Games 3.14.1 ARMv7 patch that changes the built-in house-ad countdown from 15 seconds to 1.
- Verify the exact native library and instruction signature before modifying `libil2cpp.so`.

# 1.1.2

- Restore ZenSMS's original OTP handler and remove the custom runtime extractor.
- Add only universal and Persian OTP patterns directly to the stock extractor.
- Preserve the stock candidate validator and extend its nearby-context ignore terms with `تخفیف`.

# 1.1.1

- Fix on-device OTP hook application by emitting branch instructions directly.

# 1.1.0

- Add OTPHelper-first OTP detection with the original ZenSMS detector as fallback.
- Add hard-ignore handling and optional patch-time phrase lists.
- Select the RTL patch by default and share one extension payload between patches.
