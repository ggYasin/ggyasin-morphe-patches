# 9GAG 8.29.03 compatibility

## Input provenance

Original APK: `com.ninegag.android.app`, version `8.29.03`, code `80290300`,
minimum SDK 27, target SDK 36, single APK (all four ABIs). Fetched from Google
Play on 2026-11-04.

SHA-256: `8988ffa39121c90914859fe4a0df9eb8f919b087b37e30da24677b2aa9bc2d0e`.
`apksigner verify --print-certs` (build-tools 35.0.1) verified the v2 signature.
Publisher certificate SHA-256:
`85581f5f4501c97a2c8bfa6f8942cfa829b0fc2d531288d6d77ce16ff30e4219` (unchanged).

Both 8.26.1 and 8.29.03 were decoded with apktool and JADX. Each target was found
by strings and method structure, then compared to 8.26.1 after normalizing
obfuscated names. Raw DEX descriptors, not names, drive the patch.

## Mapping from 8.26.1

The table lives in `ninegag/shared/Symbols.kt` and is copied in
`scripts/verify_ninegag.py`. A unit test keeps the two copies in sync.

| Role | 8.26.1 | 8.29.03 | Evidence |
|---|---|---|---|
| Feed DAO / display query `j(ILString;Z)List` | `Ld44;` | `Lha4;` | Same 18-method layout. `j` and the shared `d(String,int,int,int,boolean)` builder are identical apart from renames. Promoted selector 2 still means `Promoted <> TRUE`. Shape is 10 registers and 30 instructions. |
| greenDAO QueryBuilder | `Lqy7;` | `Ljb8;` | Return type of `d` |
| Banner initializer `f(FrameLayout, Function0)V` | `Lg3b;` / `Li04;` | `Lj62;` / `Lk64;` | Only method using `adview_banner_binding` outside the binding class. Identical after renames. Shape is 11 registers and 54 instructions. Same three callers: HomeActivity, StandaloneHomeContainerActivity, SwipeablePostCommentsActivity. |
| R.id / banner binding | `Le38;` / `Lkl0;` | `Lxf8;` / `Lem0;` | Same |
| Base ad policy `b(Z, paid, entitlement)` | `Lgb;` | `Ljb;` | Whole class identical apart from renames, except one unrelated null check in `e(String,String)`. `b` is 3 registers and 22 instructions, and ends `const/4 0; return-object` (null means ads ENABLED). |
| Block-reason enum | `Lfa;` | `Lja;` | `<clinit>` is identical: `a`=NotInitialized, `b`=ConsentUnresolved, `c`=PaidAccessUnresolved, `d`=PaidAccess, `e`=Nsfw, `f`=Subscription |
| Paid access / AdEntitlement | `Lo57;` / `Lib;` | `Lah7;` / `Llb;` | `Llb;` keeps boolean fields `a` and `b` |
| Reactive gate | `Lja;->invokeSuspend` | `Lma;->invokeSuspend` | Identical after renames. It still calls `Ljb;->a`, which calls `b`. |

Layouts: the four `adview_adhesion_banner_container` layouts are byte-identical
to 8.26.1. The manifest still has exactly one `<application>`, so
DeactivateAnalyticsPatch needs no change.

The patch now also confirms from the enum's `<clinit>` that the `NotInitialized`
string is stored in field `a` before it injects `sget-object <enum>->a`.

## New in 8.29.03, reviewed

- **App-owned interstitials** (`Lw95;`, "InterstitialViewModel",
  `under9_interstitial_history`). Preload (`o`) and show both require
  `w95.r() == null`. `r()` falls back to `w95.c`, which is `Ly05` case 6:
  `jb.c()?.name()`. `jb.c()` goes through `a` to `b`. A second callback, case 7,
  calls `b` directly. The patched `b` always returns NotInitialized, so
  interstitials stay blocked by the same seam. Their combined flow also reads
  `jb.k`, the same nullable blocker.
- **New direct `b` consumer** `Ldx5;->invoke()`, the LiveRamp ATS request config
  (it was `gz4` in 8.26.1). A non-null result clears its "ads allowed" flag. This
  is privacy-positive.
- **PubMatic OpenWrap SDK** and its AdMob mediation adapter. These load through
  MobileAds, which the startup policy (`b`) keeps uninitialized. Hosts added:
  `ads.pubmatic.com`, `ow.pubmatic.com`, `owsdk.pubmatic.com`. The identifier-only
  constants `pubmatic.com` and `clicktrack.pubmatic.com` (used in `contains()`
  checks) are deliberately not listed.
- No listed host disappeared. The other new URL host is a placeholder
  (`obplaceholder.click.com`).

Entitlement, billing, consent and paid-access state are not changed.

## Verification

The verifier now picks descriptors from the original APK's versionName. The
Java extractor takes a target-method file. Switch payloads are now compared by
(key, offset) values; previously they were compared by identity `toString()`.
That bug made any target containing a switch compare unequal across separate
snapshots. 8.29.03 also checks `Ldx5;->invoke` stays unchanged.

A local, non-CI bundle was compiled from these sources (Kotlin 2.3.21, D8,
Morphe Desktop 1.19.1, `--validate --bytecode-mode STRIP_FAST`, no `--force`)
and signed with a throwaway key:

- 8.29.03 main + analytics: 39/39 PASS (187 host references rewritten).
- 8.29.03 main only: 39/39 PASS.
- 8.26.1 main only: 39/39 PASS (173 host references, unchanged).
- 8.23.0 main + analytics: patches applied, no failures. The verifier does not
  cover 8.23.0.
- RED: the unpatched 8.29.03 and 8.26.1 each fail exactly the 9 patch-effect
  checks.

The CI-built bundle is still the publication path. Re-run
`scripts/verify_ninegag.py` against it.

**No device or emulator was used.** Interstitial suppression, feed paging, login
and startup still need on-device confirmation.
