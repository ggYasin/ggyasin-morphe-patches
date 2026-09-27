#!/usr/bin/env python3
"""Inspect a REAL rebuilt APK, then execute the patched ARM blocks with Unity calls stubbed.

Requires androguard==4.1.4 and unicorn==2.1.4 in a separate Python environment.
Usage: python verify_offlinegames.py ORIGINAL.apks PATCHED.apk
Run with all three Offline Games patches enabled.
"""
import hashlib
import io
import struct
import sys
import zipfile

from loguru import logger
logger.disable("androguard")
from androguard.core.dex import DEX
from unicorn import Uc, UC_ARCH_ARM, UC_MODE_ARM, UC_HOOK_CODE
from unicorn.arm_const import UC_ARM_REG_PC, UC_ARM_REG_LR
from unicorn.arm_const import UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R4, UC_ARM_REG_R9

LIBRARY = "lib/armeabi-v7a/libil2cpp.so"
MANIFEST = "assets/patchlab/offlinegames-native.properties"
ORIGINAL_HASH = "dd619f322538d339137e30a8c53e913ddecb59e78296ba86a79f058853ba0512"


def original_library(path):
    candidates = []
    with zipfile.ZipFile(path) as archive:
        if LIBRARY in archive.namelist():
            candidates.append(archive.read(LIBRARY))
        for name in archive.namelist():
            if name.endswith(".apk"):
                with zipfile.ZipFile(io.BytesIO(archive.read(name))) as split:
                    if LIBRARY in split.namelist():
                        data = split.read(LIBRARY)
                        candidates.append(data)
                        print("INPUT", name, hashlib.sha256(data).hexdigest())
    # The supplied APKS has a legacy-patched copy in base.apk AND a stock copy
    # in the ARM split. Inspect every entry instead of silently choosing the first.
    for data in candidates:
        if hashlib.sha256(data).hexdigest() == ORIGINAL_HASH:
            return data
    raise AssertionError("Input has no verified original ARMv7 libil2cpp.so")


def check(condition, message):
    assert condition, message
    print("PASS", message)


def execute_ui_blocks(library):
    """Native instructions execute in Unicorn; engine boundaries are deterministic stubs."""
    u = Uc(UC_ARCH_ARM, UC_MODE_ARM)
    u.mem_map(0, 0x4800000)
    u.mem_write(0, library)
    u.mem_map(0x10000000, 0x10000)
    base, wrapper, button, callback, state = [0x10000000 + n * 0x100 for n in range(5)]
    def word(address, value):
        u.mem_write(address, struct.pack('<I', value))
    calls = []
    def hook(uc, address, size, _):
        if address == 0x21A27DC:  # GameObject.SetActive
            calls.append((uc.reg_read(UC_ARM_REG_R0), uc.reg_read(UC_ARM_REG_R1)))
            uc.reg_write(UC_ARM_REG_PC, uc.reg_read(UC_ARM_REG_LR))
        elif address == 0x11031B8:
            raise AssertionError("Unexpected null dereference")
    u.hook_add(UC_HOOK_CODE, hook)
    word(base + 0x38, wrapper)
    word(base + 0x40, button)
    # Exercise opening even if another caller supplied a nonzero duration.
    for counter in (0, 1, 3, 15, 60):
        word(base + 0x4c, counter)
        u.reg_write(UC_ARM_REG_R9, base)
        calls.clear()
        u.emu_start(0x15B6E94, 0x15B6EE8, count=100)
        check(calls == [(wrapper, 0), (button, 1)], f"Open(counter={counter}) hides countdown and shows close")

    # The callback that writes the incoming countdown initializes it as complete.
    word(callback + 8, 15)
    u.reg_write(UC_ARM_REG_R0, callback)
    u.emu_start(0x15B78AC, 0x15B78B0, count=1)
    u.reg_write(UC_ARM_REG_R4, base + 0x50)
    u.emu_start(0x15B78D0, 0x15B78D4, count=1)
    check(struct.unpack('<I', u.mem_read(base + 0x4c, 4))[0] == 0, "Open callback writes counter=0")

    # Fresh coroutine now reaches its normal finished block without the countdown wait.
    word(state + 8, 0)
    word(state + 0x10, base)
    u.reg_write(UC_ARM_REG_R4, state)
    u.emu_start(0x15B7918, 0x15B7A28, count=40)
    check(u.reg_read(UC_ARM_REG_PC) == 0x15B7A28, "Fresh countdown reaches normal completion block")

    u.reg_write(UC_ARM_REG_LR, 0x10008000)
    u.emu_start(0x15B77AC, 0x10008000, count=1)
    check(u.reg_read(UC_ARM_REG_PC) == 0x10008000, "OpenStorePage returns before URL resolution")
    u.reg_write(UC_ARM_REG_LR, 0x10008000)
    u.emu_start(0x12A22F8, 0x10008000, count=1)
    check(u.reg_read(UC_ARM_REG_PC) == 0x10008000, "Rewarded download adapter returns before SDK call")
    u.emu_start(0x16F47E8, 0x16F4924, count=1)
    check(u.reg_read(UC_ARM_REG_PC) == 0x16F4924, "Rewarded decision enters existing house-ad path")


def main():
    original = original_library(sys.argv[1])
    check(hashlib.sha256(original).hexdigest() == ORIGINAL_HASH, "Original input library verified")
    with zipfile.ZipFile(sys.argv[2]) as apk:
        library = apk.read(LIBRARY)
        manifest = dict(line.split('=', 1) for line in apk.read(MANIFEST).decode().splitlines() if '=' in line)
        check(manifest['format'] == '1', "Native loader manifest present")
        for name in ('libmain.so', 'libunity.so', 'libil2cpp.so'):
            check(hashlib.sha256(apk.read('lib/armeabi-v7a/' + name)).hexdigest() == manifest[name],
                  f"Final APK {name} matches loader manifest")

        expected = bytearray(original)
        for offset, replacement in {
            0x16F47E8: '4d0000ea', 0x12A22F8: '1eff2fe1',
            0x15B6EB0: '0010a0e3', 0x15B6EDC: '0110a0e3',
            0x15B78AC: '0060a0e3', 0x15B77AC: '1eff2fe1',
        }.items():
            expected[offset:offset+4] = bytes.fromhex(replacement)
        check(library == bytes(expected), "Final library contains exactly six intended edits; obsolete edits restored")
        check(library[0x15B7750:0x15B77AC] == original[0x15B7750:0x15B77AC],
              "ClosePressed reward callback is byte-for-byte unchanged")

        classes = {}
        for name in apk.namelist():
            if name.startswith('classes') and name.endswith('.dex'):
                for cls in DEX(apk.read(name)).get_classes():
                    check(cls.get_name() not in classes, "No duplicate Unity/extension class") if (
                        cls.get_name().startswith(('Lcom/unity3d/player/UnityPlayer;',
                                                   'Lapp/patchlab/extension/offlinegames/'))) else None
                    classes[cls.get_name()] = cls
        player = classes['Lcom/unity3d/player/UnityPlayer;']
        method = next(m for m in player.get_methods() if m.get_name() == 'getUnityNativeLibraryPath')
        instructions = list(method.get_instructions())
        check([i.get_name() for i in instructions] == ['invoke-static', 'move-result-object', 'return-object'],
              "Actual output DEX replaces Unity's nativeLibraryDir lookup")
        check('NativeLibraries;->directory' in instructions[0].get_output(), "Resolver calls mounted-APK helper")
        for name in ('NativeLibraries', 'NativeLibraryStore'):
            check('Lapp/patchlab/extension/offlinegames/' + name + ';' in classes, f"{name} extension present")
    execute_ui_blocks(library)
    print('OUTPUT SHA-256', hashlib.sha256(library).hexdigest())
    print('Engine calls were stubbed; Android linker and full UI still require device verification.')


if __name__ == '__main__':
    main()
