#!/usr/bin/env python3
"""Build a Horizon engine bundle: the game library and what prepares its files,
as one downloadable, verifiable zip per ABI.

The bundle is what a fixed launcher shell (a launcher APK that outlives many
engine builds) downloads and installs: libhorizon_android.so, the java/javac
launcher programs the shell must ship in its own APK (Android runs programs
only from its library folder), the prepare kit the shell extracts beside the
library, and a manifest naming versions, pins and checksums. Nothing of
Minecraft is packaged; the resources come from the player's own copy.

    python3 packaging/android/build_engine_bundle.py --abi arm64-v8a
    python3 packaging/android/build_engine_bundle.py --abi arm64-v8a --no-build
    python3 packaging/android/build_engine_bundle.py --selftest   # no NDK, cargo or JDK needed

Reuses build_apk.py's build steps and pins, so an engine bundle and an APK of
the same tree are built alike. Needs the same toolchain (see build_apk.py),
plus nothing new: the exporters bundle is built by tools/build_android_exporters.py.
"""

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_apk import (  # noqa: E402
    LIBRARY, MIN_SDK, NDK, PREPARE_KIT, ROOT, TARGETS,
    build_launchers, build_library, ndk_tool, run, sdk_dir, zip_folder,
)

OUT_DIR = ROOT / "target" / "android"
# Bumped when the manifest's fields change meaning; a shell that reads a higher
# schema than it knows must refuse the bundle, not guess.
SCHEMA = 1
# No shell has shipped yet; a released shell sets its own floor in its updater.
MIN_SHELL_VERSION = "0.0.0"


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def engine_version():
    """The engine's version name, matching the game's nightly convention
    (nightly-<date>-<commit>); HORIZON_ENGINE_VERSION overrides."""
    override = os.environ.get("HORIZON_ENGINE_VERSION")
    if override:
        return override
    try:
        date, commit = subprocess.check_output(
            ["git", "log", "-1", "--format=%cs %h"], cwd=ROOT, text=True,
            stderr=subprocess.DEVNULL).split()
        return f"nightly-{date.replace('-', '')}-{commit}"
    except (OSError, subprocess.CalledProcessError, ValueError):
        return "dev-unknown"


def output_version():
    """horizon-prepare's OUTPUT_VERSION, read from its source so the manifest
    can never drift from the tree the bundle was built from."""
    for source in sorted((ROOT / "crates" / "horizon-prepare" / "src").glob("*.rs")):
        found = re.search(r"pub const OUTPUT_VERSION: u32 = (\d+)", source.read_text(encoding="utf-8"))
        if found:
            return int(found.group(1))
    sys.exit("build_engine_bundle: OUTPUT_VERSION not found in crates/horizon-prepare")


def mc_version():
    """The Minecraft version the engine plays, pinned in research-lock.json."""
    lock = json.loads((ROOT / "research-lock.json").read_text(encoding="utf-8"))
    version = lock.get("minecraft", {}).get("version")
    if not version:
        sys.exit("build_engine_bundle: minecraft.version not found in research-lock.json")
    return version


def jre_pins():
    """The Android OpenJDK 25 runtimes horizon-prepare runs on the phone, read
    from starling-account's pinned table (url, sha256, size per architecture),
    so a shell can pre-download what the engine would otherwise fetch itself.
    x86 (32-bit) has no build and no javac module: prepare happens on a PC."""
    text = (ROOT / "crates" / "starling-account" / "src" / "java.rs").read_text(encoding="utf-8")
    pins = {}
    for match in re.finditer(r'url:\s*"([^"]+)"', text):
        # The struct is url, sha256, size; each pin is found in the window after its url.
        window = text[match.start():match.start() + 800]
        digest = re.search(r'sha256:\s*"([0-9a-f]{64})"', window)
        size = re.search(r'size:\s*(\d[\d_]*)', window)
        if not digest:
            continue
        name = match.group(1).rsplit("/", 1)[-1]
        arch = ("arm64" if "arm64" in name
                else "armeabi-v7a" if "-arm-" in name
                else "x86_64" if "x86_64" in name else None)
        if arch:
            pins[arch] = {"url": match.group(1), "sha256": digest.group(1),
                          "size_bytes": int((size.group(1) or "0").replace("_", "")) if size else 0}
    pins.setdefault("x86", None)
    return pins


def assemble_prepare_kit(kit_path, skip_exporters):
    """assets/prepare-kit.zip's contents (see build_apk.py), built standalone:
    the pins, original exporter sources and verified exporter classes."""
    if not skip_exporters:
        run(sys.executable, ROOT / "tools" / "build_android_exporters.py")
    bundle = ROOT / "exporters"
    manifest = json.loads((bundle / "manifest.json").read_text(encoding="utf-8"))
    with zipfile.ZipFile(kit_path, "w", zipfile.ZIP_DEFLATED) as kit:
        for entry in PREPARE_KIT:
            path = ROOT / entry
            files = sorted(p for p in path.rglob("*.java")) if path.is_dir() else [path]
            for file in files:
                kit.write(file, file.relative_to(ROOT).as_posix())
        for name in ["manifest.json", *sorted(manifest["classes"])]:
            kit.write(bundle / name, "exporters/" + name)


def write_manifest(stage, bundle_version, abi, files):
    manifest = {
        "schema": SCHEMA,
        "engine_version": bundle_version,
        "mc_version": mc_version(),
        "kit_output_version": output_version(),
        "min_shell_version": MIN_SHELL_VERSION,
        "abi": abi,
        "java_launchers": ["libstarling_java.so", "libstarling_javac.so"],
        "jre": jre_pins(),
        "sha256": {name: sha256(stage / name) for name in files},
    }
    (stage / "manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return manifest


def write_sums(stage, exclude=("SHA256SUMS",)):
    names = sorted(p.relative_to(stage).as_posix() for p in stage.rglob("*") if p.is_file())
    lines = "".join(f"{sha256(stage / name)}  {name}\n"
                    for name in names if name not in exclude)
    (stage / "SHA256SUMS").write_text(lines, encoding="utf-8")


def bundle(abi, bundle_version, stage, out):
    """Zip the staged bundle the way build_apk.py zips folders: same bytes for
    the same files, so a shell can tell builds apart by their checksums."""
    archive = out / f"engine-{bundle_version}-{abi}.zip"
    zip_folder(stage, archive)
    return archive


def selftest():
    """The manifest, checksums and zip layout, on dummy artifacts: the parts a
    shell depends on, checked without any Android toolchain."""
    print("selftest: fabricating dummy artifacts")
    stage = OUT_DIR / "engine-selftest"
    shutil.rmtree(stage, ignore_errors=True)
    (stage / "java-launchers").mkdir(parents=True)
    (stage / "libhorizon_android.so").write_bytes(b"\x7fELF" + b"\0" * 4096)
    for name in ("libstarling_java.so", "libstarling_javac.so"):
        (stage / "java-launchers" / name).write_bytes(b"\x7fELF" + b"\0" * 128)
    kit = stage / "prepare-kit.zip"
    with zipfile.ZipFile(kit, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("research-lock.json", (ROOT / "research-lock.json").read_text(encoding="utf-8"))
        z.writestr("exporters/manifest.json", '{"classes": []}')

    files = ["libhorizon_android.so", "java-launchers/libstarling_java.so",
             "java-launchers/libstarling_javac.so", "prepare-kit.zip"]
    manifest = write_manifest(stage, engine_version(), "arm64-v8a", files)
    write_sums(stage)
    print(json.dumps(manifest, indent=2, sort_keys=True))
    archive = bundle("arm64-v8a", "selftest-" + engine_version(), stage, OUT_DIR)
    # Verify the sums the way a shell would, from inside the zip.
    with zipfile.ZipFile(archive) as z:
        for line in z.read("SHA256SUMS").decode("utf-8").splitlines():
            digest, name = line.split("  ", 1)
            actual = hashlib.sha256(z.read(name)).hexdigest()
            assert digest == actual, f"selftest: {name} checksum mismatch"
        assert json.loads(z.read("manifest.json"))["mc_version"] == mc_version()
    print(f"selftest OK: {archive} ({archive.stat().st_size} bytes), sums verified")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--abi", choices=TARGETS, default="arm64-v8a",
                        help="Android ABI (default: arm64-v8a)")
    parser.add_argument("--profile", default="quick",
                        help="Cargo profile (default: quick, for iteration; fast to ship)")
    parser.add_argument("--no-build", action="store_true", help="package the library built last")
    parser.add_argument("--skip-exporters", action="store_true",
                        help="reuse the exporters bundle built last")
    parser.add_argument("--keep-symbols", action="store_true",
                        help="ship the library unstripped (backtraces)")
    parser.add_argument("--selftest", action="store_true", help="verify on dummy artifacts, no toolchain")
    args = parser.parse_args()
    if args.selftest:
        selftest()
        return
    triple, _, _ = TARGETS[args.abi]

    sdk = sdk_dir()
    ndk = Path(os.environ.get("ANDROID_NDK_HOME") or sdk / "ndk" / NDK)
    if not ndk.exists() and not args.no_build:
        sys.exit(f"build_engine_bundle: missing {ndk}")

    if not args.no_build:
        build_library(args.profile, ndk, sdk, args.abi)
    built = ROOT / "target" / triple / args.profile / f"lib{LIBRARY}.so"
    if not built.is_file():
        sys.exit(f"build_engine_bundle: {built} not built")

    bundle_version = engine_version()
    stage = OUT_DIR / f"engine-{bundle_version}-{args.abi}"
    shutil.rmtree(stage, ignore_errors=True)
    (stage / "java-launchers").mkdir(parents=True)
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    library = stage / f"lib{LIBRARY}.so"
    if args.keep_symbols:
        shutil.copyfile(built, library)
    else:
        run(ndk_tool(ndk, "llvm-strip"), "--strip-unneeded", "-o", library, built)
        shutil.copyfile(built, OUT_DIR / f"lib{LIBRARY}-{bundle_version}-{args.abi}-symbols.so")

    launchers = build_launchers(ndk, stage / "java-launchers", args.abi)
    shutil.copyfile(launchers["libc++_shared.so"], stage / "java-launchers" / "libc++_shared.so")

    kit = stage / "prepare-kit.zip"
    assemble_prepare_kit(kit, args.skip_exporters)

    files = [f"lib{LIBRARY}.so", "java-launchers/libstarling_java.so",
             "java-launchers/libstarling_javac.so", "java-launchers/libc++_shared.so",
             "prepare-kit.zip"]
    write_manifest(stage, bundle_version, args.abi, files)
    write_sums(stage)
    archive = bundle(args.abi, bundle_version, stage, OUT_DIR)
    print(f"engine bundle: {archive} ({archive.stat().st_size} bytes), "
          f"engine {bundle_version}, abi {args.abi}")


if __name__ == "__main__":
    main()
