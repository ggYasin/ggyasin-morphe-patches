# ggyasin-morphe-patches build and import

This repository builds one local Morphe patch bundle containing the ZenSMS
patches documented in the README. Versioned bundles are also published as
GitHub Release assets. The repository does not register a remote patch source
with Morphe.

## Build with GitHub Actions

1. Open the repository on GitHub.
2. Select **Actions**.
3. Select **Build MPP**.
4. Select **Run workflow**.
5. Open the completed workflow run.
6. Download the `ggyasin-morphe-patches` artifact.
7. Extract the downloaded ZIP to obtain the `.mpp` file.

## Import the local bundle into Morphe Manager

1. Copy the `.mpp` file to the Android device.
2. Open Morphe Manager.
3. Open **Patch sources**.
4. Select **Add patch source**.
5. Select **Select patch source file**.
6. Choose the `.mpp` file from device storage.

Alternatively, open the `.mpp` from an Android file manager and select Morphe
Manager as the app to handle it.

## Patch the test XAPK

1. In Morphe Manager, select ZenSMS.
2. Use Expert mode so patch selection is visible.
3. Choose `ZenSMS_1.2.04.xapk` when prompted for the original app bundle.
4. Enable the patches you want to test:
   - **Enable premium state** changes the existing premium-state checks.
   - **Expanded OTP detection** keeps ZenSMS's original handler and adds
     universal and Persian patterns directly to its stock extractor.
   - **RTL SMS lists** adds a conversation-list switch under
     **Settings → Appearance**. It mirrors the rows while keeping contact
     names and phone numbers left to right and right-aligned.
5. Patch and save the output.
6. Install only on an emulator or disposable test device.

OTP rules are embedded in the patch. Changing them requires updating and
rebuilding the patch bundle.

The patched APK set is signed with Morphe's configured key. It cannot update an
official ZenSMS installation signed by the publisher unless that installation
is removed first. Removing an installed app deletes its local application data.

## Patch Offline Games 3.14.1

1. Select `offline_games.xapk` as the original app bundle.
2. In Expert mode, enable the Offline Games patches you want:
   - **In-house ad only** stops the game from requesting rewarded ads, so its
     own cross-promotion is used every time.
   - **Instant in-house ad close** enables the in-house ad's close button
     immediately, without waiting out its countdown.
3. Patch and save the output.
4. Install on an emulator or a disposable test device. The patched set is signed
   with Morphe's key, so an existing official install has to be removed first.
5. Trigger a rewarded ad, for example an extra life, and confirm the in-house
   popup appears and can be closed straight away.

Both patches require the exact ARMv7 `3.14.1` (`3204`) XAPK. They leave the
library unchanged when its verified native signature or instruction windows do
not match, and the reported message names what did not match.
