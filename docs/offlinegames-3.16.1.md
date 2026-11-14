# Offline Games 3.16.1 ARM64 port

Input: Google Play bundle fetched 2026-11-04, package `com.JindoBlu.OfflineGames`,
version code `3418`, minimum API 23, target API 36: `base.apk` plus
`config.arm64_v8a` split (native libraries are only in the split).

## Native identity

- `libil2cpp.so`: 97,686,832 bytes; SHA-256
  `859a5941bf4fa850835ae664bda95641f3e080dcd2663b5be7caa4dce702ac48`.
- `libmain.so`: unchanged from 3.15.3 (SHA-256 `e6ee6840…`), so the strict
  `libmain` loader edit applies as-is.
- `libunity.so`: SHA-256 `9a22e584e3e3eef9632f844a07f727b0ddddf42bcc7f12872a3c555d4c8ef681`.
- IL2CPP metadata v31; executable virtual address = file offset + `0x4000`, as in 3.15.3.

## How the sites were found

Each 3.15.3 site was matched in 3.16.1 by a code signature of the surrounding
instructions, with relocation-dependent fields masked out (B/BL targets, ADRP
pages, load/store and ADD immediates). Only unique matches were accepted, and
every hit was then disassembled side by side with 3.15.3.

- The house-ad popup sites (`Open`, the counter copy and `OpenStorePage`) matched
  unchanged. The field offsets `+0x70`, `+0x80` and `+0x98` were checked
  explicitly.
- The rewarded decision `tbz` matched. The `showHouseAd` call block now sits at
  `+0xFC` instead of `+0xF8`, because one extra `mov x2, xzr` was added to an
  earlier call. The branch was recomputed to `b +0xFC`, and its `bl` target is
  the remapped `showHouseAd` closure.
- `InitializeFirebaseCo` matched unchanged.
- `LoaderView.LoadCo` was rebuilt in 3.16.1 (new virtual-call slots), but the
  country-wait `tbz` and the parallel-ads `tbz w21` keep their structure. Both
  targets were confirmed: the missing-country path is the same target as the
  following `b.pl`, and the serial path is the same `mov w8,#0xa` block.
- `ApplovinRewardedAd.LoadMaxSdkAd` is a generic wrapper, so its bytes alone are
  not unique. It was located through its IL2CPP method-pointer slot (an
  `R_AARCH64_RELATIVE` relocation). All 12 neighbouring methods of the class
  match in length and masked bytes.

| Purpose | File offset | Original → replacement |
|---|---|---|
| Shared rewarded decision | `0x2d8ee78` | `tbz w0,0,…` → `b 0x2d92f74` |
| Rewarded download adapter | `0x2891344` | entry → `ret` |
| Hide seconds wrapper | `0x2bb5150` | `cset w1,gt` → `mov w1,wzr` |
| Show close button | `0x2bb516c` | `cset w1,eq` → `mov w1,1` |
| Initialize counter | `0x2bb596c` | `ldr w8,[x0,0x10]` → `mov w8,wzr` |
| Store-click handler | `0x2bb5880` | entry → `ret` |
| Firebase startup wait | `0x2876e28` | `b.ge` → `b 0x287ae48` |
| Country lookup wait | `0x2877764` | `tbz` → `b 0x287bc30` |
| Parallel ad initialization | `0x2877f44` | `tbz w21,0,…` → `nop` |

## Java loader hook

`UnityPlayer.getUnityNativeLibraryPath` is unchanged. In `loadNative`, only the
`libmain.so` path concatenation changed: it now goes through a helper rather
than `StringBuilder`. The `System.load` / `System.loadLibrary("main")` calls,
`NativeLoader.load` → `move-result p0` → `if-eqz`, and `.locals 2` (3 registers)
all match what `NativeLibraryLoaderPatch` checks.

## Launch toast removed

`finishLoad` no longer shows `PatchLab: patched native code loaded`. Success is
logged to `PatchLabOfflineGames` only. Failures still show a toast, Unity's error
dialog and `patchlab-native-load-error.txt`.

Patch the original Play bundle. If you repatch an older patched output, the
existing `strictv2` classes may be kept by extension merging, and the toast
would then remain.

## Validation

`scripts/verify_offlinegames_arm64.py` now holds one profile per version. With
all nine edits applied to the original 3.16.1 library, Unicorn ran every
modified block and all checks passed:

- Popup visibility for counters 0/1/3/15/60.
- Counter zeroing and coroutine completion.
- Both `ret` stubs.
- The rewarded path for both readiness values.
- All 16 NZCV states at the Firebase wait, and the Firebase continuation.
- The country skip.
- Parallel dispatch with the correct arguments.

The same harness on 3.15.3 reproduces the documented output SHA-256 `8712fcd8…`.
The expected all-patch 3.16.1 `libil2cpp.so` SHA-256 is
`8a336654ae5f652f5ad3c1b977791dbac3dc120912e9a1101dfcc79520a9c869`.

These are static and isolated-instruction tests, not a device run.
