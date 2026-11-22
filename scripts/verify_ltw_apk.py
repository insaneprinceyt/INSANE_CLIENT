#!/usr/bin/env python3
"""Check the shipped LTW renderer on every supported ABI (no device execution).

Usage: python3 scripts/verify_ltw_apk.py path/to/INSANE-CLIENT-android.apk
Requires GNU readelf or llvm-readelf. Fails on missing renderer libraries,
wrong architecture, unexpected shared dependencies, missing public entry points,
insufficient page alignment, or missing bundled license/source provenance.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
import tempfile
import zipfile

ABIS = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183), "x86_64": (2, 62)}
EXPORTS = {"eglGetProcAddress", "eglCreateContext", "eglDestroyContext",
           "eglMakeCurrent", "glXGetProcAddress", "glGetString"}
SYSTEM_LIBS = {"libc.so", "libm.so", "libdl.so", "liblog.so"}


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def inspect_library(data, abi, readelf, directory):
    elf_class, machine = ABIS[abi]
    require(data[:4] == b"\x7fELF" and data[4] == elf_class and data[5] == 1,
            f"Wrong ELF class/endian for {abi}")
    require(struct.unpack_from("<H", data, 18)[0] == machine, f"Wrong ELF machine for {abi}")
    if elf_class == 2:
        phoff = struct.unpack_from("<Q", data, 32)[0]
        phsize, phnum = struct.unpack_from("<HH", data, 54)
    else:
        phoff = struct.unpack_from("<I", data, 28)[0]
        phsize, phnum = struct.unpack_from("<HH", data, 42)
    load_segments = 0
    for index in range(phnum):
        offset = phoff + index * phsize
        if struct.unpack_from("<I", data, offset)[0] != 1:
            continue
        load_segments += 1
        if elf_class == 2:
            file_offset, address = struct.unpack_from("<QQ", data, offset + 8)
            alignment = struct.unpack_from("<Q", data, offset + 48)[0]
        else:
            file_offset, address = struct.unpack_from("<II", data, offset + 4)
            alignment = struct.unpack_from("<I", data, offset + 28)[0]
        require(alignment >= 16384 and file_offset % 16384 == address % 16384,
                f"LTW load segment is not 16 KB aligned for {abi}")
    require(load_segments > 0, f"No loadable segments for {abi}")
    library = directory / (abi + ".so")
    library.write_bytes(data)
    dynamic = subprocess.check_output([readelf, "-dW", str(library)], text=True)
    needed = set(re.findall(r"\(NEEDED\).*?\[([^]]+)\]", dynamic))
    require(needed <= SYSTEM_LIBS, f"Unexpected LTW dependencies for {abi}: {needed - SYSTEM_LIBS}")
    symbols = subprocess.check_output([readelf, "--dyn-syms", "--wide", str(library)], text=True)
    defined = set()
    for line in symbols.splitlines():
        fields = line.split()
        if len(fields) >= 8 and fields[0].endswith(":") and fields[6] != "UND":
            defined.add(fields[7].split("@")[0])
    require(EXPORTS <= defined, f"Missing LTW entry points for {abi}: {EXPORTS - defined}")
    return {"size": len(data), "sha256": hashlib.sha256(data).hexdigest(),
            "elf_machine": machine, "load_segments": load_segments,
            "alignment_16kb": "PASS", "required_exports": "PASS", "needed": sorted(needed)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--readelf", default=shutil.which("llvm-readelf") or shutil.which("readelf"))
    args = parser.parse_args()
    require(args.readelf, "Install readelf or pass --readelf /path/to/llvm-readelf")
    root = Path(__file__).resolve().parents[1]
    provenance = json.loads((root / "third_party/ltw/UPSTREAM.json").read_text())
    result = {"apk": args.apk.name, "upstream_commit": provenance["commit"], "abis": {}}
    with zipfile.ZipFile(args.apk) as apk, tempfile.TemporaryDirectory() as temporary:
        paths = {f"lib/{abi}/libltw.so" for abi in ABIS}
        present = {name for name in apk.namelist() if name.endswith("/libltw.so")}
        require(present == paths, f"Missing or unexpected LTW libraries: wanted {sorted(paths)}, got {sorted(present)}")
        for abi in ABIS:
            result["abis"][abi] = inspect_library(apk.read(f"lib/{abi}/libltw.so"), abi,
                                                   args.readelf, Path(temporary))
        for name, source in [("LICENSE.txt", "LICENSE"), ("UPSTREAM.json", "UPSTREAM.json")]:
            require(apk.read("assets/ltw/" + name) == (root / "third_party/ltw" / source).read_bytes(),
                    "Bundled LTW notice/provenance mismatch: " + name)
    result["notices_and_provenance"] = "PASS"
    result["device_rendering"] = "UNVERIFIED"
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
