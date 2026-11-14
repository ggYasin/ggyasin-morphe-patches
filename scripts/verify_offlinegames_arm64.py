"""3.15.3 / 3.16.1 ARM64 output verification. Invoked by verify_offlinegames.py.

Independent expected offsets and ARM64 execution; ELF virtual addresses are NOT file offsets.
"""
import hashlib
import struct
import zipfile
from androguard.core.dex import DEX
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE
from unicorn.arm64_const import (UC_ARM64_REG_PC, UC_ARM64_REG_LR, UC_ARM64_REG_SP,
    UC_ARM64_REG_X0, UC_ARM64_REG_X1, UC_ARM64_REG_X8, UC_ARM64_REG_X19,
    UC_ARM64_REG_X20, UC_ARM64_REG_X21, UC_ARM64_REG_X24, UC_ARM64_REG_NZCV)
from verify_offlinegames import original_library, check, MANIFEST

LIBRARY = 'lib/arm64-v8a/libil2cpp.so'

# File-offset edits plus the virtual addresses (file offset + 0x4000) used by execute().
PROFILES = {
    '3.15.3': dict(
        hash='80dbeb4bd8f5cd8e1f5c2590c410ac4a49defd56a5cd64a11dc455c18947d74e', memory=0x5900000,
        edits={0x2AA08A0: '3e000014', 0x258DDDC: 'c0035fd6', 0x28EC630: 'e1031f2a',
               0x28EC64C: '21008052', 0x28ECE4C: 'e8031f2a', 0x28ECD60: 'c0035fd6'},
        startup={0x257631C: '08000014', 0x2576C48: 'd6000014', 0x2577360: '1f2003d5'},
        close=(0x28ECD18, 0x28ECD60),
        va=dict(set_active=0x4D52870, log_time=0x25783E0, null_ref=0x23C096C,
                open=(0x28F061C, 0x28F0654), counter=0x28F0E4C, counter_store=0x28F0E5C,
                coroutine=(0x28F0EAC, 0x28F0F68), store=0x28F0D60, download=0x2591DDC,
                rewarded=(0x2AA48A0, 0x2AA4998), firebase=(0x257A31C, 0x257A33C),
                firebase_done=0x257A288, country=(0x257AC48, 0x257AFA0),
                parallel=0x257B360, start_coroutine=0x4D5396C)),
    '3.16.1': dict(
        hash='859a5941bf4fa850835ae664bda95641f3e080dcd2663b5be7caa4dce702ac48', memory=0x6000000,
        edits={0x2D8EE78: '3f000014', 0x2891344: 'c0035fd6', 0x2BB5150: 'e1031f2a',
               0x2BB516C: '21008052', 0x2BB596C: 'e8031f2a', 0x2BB5880: 'c0035fd6'},
        startup={0x2876E28: '08000014', 0x2877764: '33010014', 0x2877F44: '1f2003d5'},
        close=(0x2BB5838, 0x2BB5880),
        va=dict(set_active=0x54023E4, log_time=0x2878824, null_ref=0x268FB00,
                open=(0x2BB913C, 0x2BB9174), counter=0x2BB996C, counter_store=0x2BB997C,
                coroutine=(0x2BB99CC, 0x2BB9A88), store=0x2BB9880, download=0x2895344,
                rewarded=(0x2D92E78, 0x2D92F74), firebase=(0x287AE28, 0x287AE48),
                firebase_done=0x287AD94, country=(0x287B764, 0x287BC30),
                parallel=0x287BF44, start_coroutine=0x54034E0)),
}


def emulator(data, memory):
    """Map ELF PT_LOAD segments and R_AARCH64_RELATIVE relocations at bias zero."""
    u = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
    u.mem_map(0, memory)
    phoff = struct.unpack_from('<Q', data, 32)[0]
    entsize, count = struct.unpack_from('<HH', data, 54)
    segments = [struct.unpack_from('<IIQQQQQQ', data, phoff+i*entsize) for i in range(count)]
    image = bytearray(data)
    shoff = struct.unpack_from('<Q', data, 40)[0]
    shentsize, shcount = struct.unpack_from('<HH', data, 58)
    def file_offset(address):
        for typ, _, off, va, _, size, _, _ in segments:
            if typ == 1 and va <= address < va+size:
                return off+address-va
        return None
    for i in range(shcount):
        s = struct.unpack_from('<IIQQQQIIQQ', data, shoff+i*shentsize)
        if s[1] != 4: continue
        for off in range(s[4], s[4]+s[5], s[9]):
            address, info, addend = struct.unpack_from('<QQq', data, off)
            if info & 0xffffffff == 1027:
                offset = file_offset(address)
                if offset is not None: struct.pack_into('<Q', image, offset, addend)
    for typ, _, off, va, _, size, _, _ in segments:
        if typ == 1: u.mem_write(va, bytes(image[off:off+size]))
    u.mem_map(0x10000000, 0x10000)
    u.reg_write(UC_ARM64_REG_SP, 0x10007000)
    return u


def execute(profile, patched, startup):
    v = profile['va']
    u = emulator(patched, profile['memory'])
    base, wrapper, button, callback, state = [0x10000000+i*0x100 for i in range(5)]
    def word(address, value): u.mem_write(address, struct.pack('<I', value))
    def pointer(address, value): u.mem_write(address, struct.pack('<Q', value))
    calls, logs = [], []
    def hook(uc, address, size, _):
        if address == v['set_active']:
            calls.append((uc.reg_read(UC_ARM64_REG_X0), uc.reg_read(UC_ARM64_REG_X1)))
            uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_LR))
        elif address == v['log_time']:
            logs.append(address)
            uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_LR))
        elif address == v['null_ref']:
            raise AssertionError('Unexpected null-reference path')
    u.hook_add(UC_HOOK_CODE, hook)
    pointer(base+0x70, wrapper)
    pointer(base+0x80, button)
    for n in (0, 1, 3, 15, 60):
        word(base+0x98, n)
        u.reg_write(UC_ARM64_REG_X19, base)
        calls.clear()
        u.emu_start(*v['open'], count=100)
        check(calls == [(wrapper, 0), (button, 1)], f'ARM64 Open(counter={n}): close visible, timer hidden')
    word(callback+0x10, 15)
    u.reg_write(UC_ARM64_REG_X0, callback)
    u.emu_start(v['counter'], v['counter'] + 4, count=1)
    u.reg_write(UC_ARM64_REG_X19, base+0xa0)
    u.emu_start(v['counter_store'], v['counter_store'] + 4, count=1)
    check(struct.unpack('<I', u.mem_read(base+0x98, 4))[0] == 0, 'ARM64 counter initialization is zero')
    word(state+0x10, 0)
    pointer(state+0x20, base)
    u.reg_write(UC_ARM64_REG_X19, state)
    u.emu_start(*v['coroutine'], count=40)
    check(u.reg_read(UC_ARM64_REG_PC) == v['coroutine'][1], 'ARM64 normal coroutine reaches finished block')
    for address in (v['store'], v['download']):
        u.reg_write(UC_ARM64_REG_LR, 0x10008000)
        u.emu_start(address, 0x10008000, count=1)
        check(u.reg_read(UC_ARM64_REG_PC) == 0x10008000, f'ARM64 {address:#x} returns before side effects')
    for ready in (0, 1):
        u.reg_write(UC_ARM64_REG_X0, ready)
        u.emu_start(*v['rewarded'], count=1)
        check(u.reg_read(UC_ARM64_REG_PC) == v['rewarded'][1], f'ARM64 rewarded decision takes house-ad path (ready={ready})')
    if not startup: return
    for flags in range(16):
        u.reg_write(UC_ARM64_REG_NZCV, flags << 28)
        u.emu_start(*v['firebase'], count=1)
        check(u.reg_read(UC_ARM64_REG_PC) == v['firebase'][1], f'ARM64 Firebase wait continuation NZCV={flags:x}')
    for missing in (0, 1):
        u.reg_write(UC_ARM64_REG_X0, missing)
        u.emu_start(*v['country'], count=1)
        check(u.reg_read(UC_ARM64_REG_PC) == v['country'][1], f'ARM64 country wait skipped (missing={missing})')
    # Real timeout continuation goes through LogTime then returns false/completed.
    u.reg_write(UC_ARM64_REG_X24, base)
    u.emu_start(v['firebase'][1], v['firebase_done'], count=40)
    check(u.reg_read(UC_ARM64_REG_X0) == 0 and len(logs) == 1, 'ARM64 Firebase continuation completes without yielding')
    for sequential in (0, 1):
        u.reg_write(UC_ARM64_REG_X21, sequential)
        u.reg_write(UC_ARM64_REG_X20, base)
        u.reg_write(UC_ARM64_REG_X1, state)
        u.emu_start(v['parallel'], v['start_coroutine'], count=10)
        check(u.reg_read(UC_ARM64_REG_X0) == base and u.reg_read(UC_ARM64_REG_X1) == state,
              f'ARM64 ad coroutine dispatched in parallel with correct arguments ({sequential})')


def verify(args):
    with zipfile.ZipFile(args.patched) as apk:
        manifest = dict(s.split('=',1) for s in apk.read(MANIFEST).decode().splitlines() if '=' in s)
    check(manifest['abi'] == 'arm64-v8a' and manifest['version'] in PROFILES, 'ARM64 manifest selects a verified version/ABI')
    profile = PROFILES[manifest['version']]
    original = original_library(args.original, LIBRARY, profile['hash'])
    with zipfile.ZipFile(args.patched) as apk:
        data = apk.read(LIBRARY)
        for name in ('libmain.so','libunity.so','libil2cpp.so'):
            check(hashlib.sha256(apk.read('lib/arm64-v8a/'+name)).hexdigest() == manifest[name], name+' matches final manifest')
        strict_loader = manifest.get('loader') == 'strict-v2'
        if strict_loader:
            from verify_strict_loader import verify as verify_loader
            verify_loader(apk.read('lib/arm64-v8a/libmain.so'), 'arm64-v8a')
        expected = bytearray(original)
        edits = profile['edits'] | (profile['startup'] if args.fast_startup else {})
        for offset, replacement in edits.items(): expected[offset:offset+4] = bytes.fromhex(replacement)
        check(data == bytes(expected), 'ARM64 final library contains exactly selected edits')
        close = slice(*profile['close'])
        check(data[close] == original[close], 'ClosePressed and reward callback unchanged')
        classes = {}
        for entry in apk.namelist():
            if not (entry.startswith('classes') and entry.endswith('.dex')): continue
            for cls in DEX(apk.read(entry)).get_classes():
                name = cls.get_name()
                if name.startswith(('Lcom/unity3d/player/UnityPlayer;', 'Lapp/patchlab/extension/offlinegames/')):
                    check(name not in classes, 'No duplicate '+name)
                    classes[name] = cls
        player = classes['Lcom/unity3d/player/UnityPlayer;']
        method = next(m for m in player.get_methods() if m.get_name() == 'getUnityNativeLibraryPath')
        instructions = list(method.get_instructions())
        check([i.get_name() for i in instructions] == ['invoke-static','move-result-object','return-object'] and
              'NativeLibraries;->directory' in instructions[0].get_output(), 'Output DEX uses patched native resolver')
        load = next(m for m in player.get_methods() if m.get_name() == 'loadNative')
        instructions = list(load.get_instructions())
        call = next(i for i, ins in enumerate(instructions) if 'NativeLoader;->load(' in ins.get_output())
        if strict_loader:
            from verify_strict_loader import verify_dex, verify_extension
            verify_dex(player)
            verify_extension(classes)
        else:
            check('NativeLibraries;->verifyLoaded()' in instructions[call+3].get_output(), 'Post-load diagnostic hook present')
        for name in ('NativeLibraryStore','NativeLibraries','NativeLoadStatus'):
            prefix='Lapp/patchlab/extension/offlinegames/' + ('strictv2/' if strict_loader else '')
            check(prefix+name+';' in classes, name+' present')
    execute(profile, data, args.fast_startup)
    print('OUTPUT SHA-256', hashlib.sha256(data).hexdigest())
    print('ARM64 instructions executed with engine calls stubbed; device behavior remains unverified.')
