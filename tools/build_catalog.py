"""Build the ACN Store catalog from the real APKs of the workspace.

Reads catalog/apps.json (curated metadata), inspects every APK with aapt
(package id, versionCode, versionName, minSdk), computes its SHA-256 and size,
extracts its launcher icon, then writes:
  - catalog/catalog.json            (published to GitHub, fetched by the store)
  - catalog/icons/<package>.png
  - app/src/main/assets/catalog.json (offline fallback bundled in the store APK)

Usage: python tools/build_catalog.py
"""
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import unicodedata
import zipfile
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image

STORE_DIR = Path(__file__).resolve().parent.parent
WORKSPACE = STORE_DIR.parent
CATALOG_DIR = STORE_DIR / "catalog"
ICONS_DIR = CATALOG_DIR / "icons"
SCREENSHOTS_DIR = CATALOG_DIR / "screenshots"
ASSET_CATALOG = STORE_DIR / "app" / "src" / "main" / "assets" / "catalog.json"
ASSET_ICONS = STORE_DIR / "app" / "src" / "main" / "assets" / "icons"
ASSET_SCREENSHOTS = STORE_DIR / "app" / "src" / "main" / "assets" / "screenshots"
SDK = Path(os.environ.get("ANDROID_HOME", Path.home() / "AppData/Local/Android/Sdk"))


def find_aapt() -> Path:
    tools = sorted((SDK / "build-tools").glob("*/aapt.exe")) or sorted((SDK / "build-tools").glob("*/aapt"))
    if not tools:
        sys.exit("[Catalog] aapt not found in Android SDK build-tools")
    return tools[-1]


def badging(aapt: Path, apk: Path) -> dict:
    out = subprocess.run([str(aapt), "dump", "badging", str(apk)], capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
    pkg = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", out)
    if not pkg:
        raise RuntimeError(f"cannot read badging of {apk}")
    min_sdk = re.search(r"sdkVersion:'(\d+)'", out)
    icons = re.findall(r"application-icon-(\d+):'([^']+)'", out)
    return {
        "package": pkg.group(1),
        "version_code": int(pkg.group(2)),
        "version_name": pkg.group(3),
        "min_sdk": int(min_sdk.group(1)) if min_sdk else 21,
        "icons": sorted(((int(d), p) for d, p in icons), reverse=True),
    }


def extract_icon(apk: Path, icons: list, dest: Path) -> bool:
    """Save the best bitmap launcher icon of the APK as a 192px PNG."""
    with zipfile.ZipFile(apk) as z:
        names = set(z.namelist())
        candidates = [p for _, p in icons if not p.endswith(".xml") and p in names]
        if not candidates:
            # Adaptive icons point to XML: fall back to the densest bitmap launcher in the archive
            pattern = re.compile(r"res/mipmap-(xxxhdpi|xxhdpi|xhdpi|hdpi)[^/]*/ic_launcher(_round)?\.(png|webp)$")
            order = ["xxxhdpi", "xxhdpi", "xhdpi", "hdpi"]
            found = [n for n in names if pattern.match(n) and "_round" not in n]
            found.sort(key=lambda n: order.index(pattern.match(n).group(1)))
            candidates = found
        for path in candidates:
            try:
                img = Image.open(io.BytesIO(z.read(path))).convert("RGBA")
                img = img.resize((192, 192), Image.LANCZOS)
                img.save(dest, "PNG", optimize=True)
                return True
            except Exception:
                continue
    return False


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def top_changelog(path: Path) -> str:
    """First release section of a CHANGELOG.md, trimmed for the store page."""
    if not path.exists():
        return ""
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    section, started = [], False
    for line in lines:
        if line.startswith("## "):
            if started:
                break
            started = True
            continue
        if started and line.strip():
            text = re.sub(r"\*\*|__|`", "", line.strip().lstrip("-* #").strip())
            if text:
                section.append(text)
    return "\n".join(f"• {l}" for l in section[:8])


def main():
    config = json.loads((CATALOG_DIR / "apps.json").read_text(encoding="utf-8"))
    store = config["store"]
    aapt = find_aapt()
    ICONS_DIR.mkdir(parents=True, exist_ok=True)
    apps = []

    for entry in config["apps"]:
        apk = WORKSPACE / entry["apk"]
        if not apk.exists():
            print(f"[Catalog] SKIP {entry['name']}: {apk} missing")
            continue
        info = badging(aapt, apk)
        pkg = info["package"]
        version = info["version_name"]
        ascii_name = unicodedata.normalize("NFKD", entry["name"]).encode("ascii", "ignore").decode()
        file_name = f"{re.sub(r'[^A-Za-z0-9]+', '-', ascii_name).strip('-')}-{version}.apk"
        default_url = f"https://github.com/{store['release_repo']}/releases/download/{pkg}-v{version}/{file_name}"
        url = entry.get("download_url", default_url).replace("{version}", version)

        icon_dest = ICONS_DIR / f"{pkg}.png"
        if entry.get("icon"):
            # Vector-only launchers: use the project's source bitmap instead
            Image.open(WORKSPACE / entry["icon"]).convert("RGBA").resize((192, 192), Image.LANCZOS).save(icon_dest, "PNG", optimize=True)
            has_icon = True
        else:
            has_icon = extract_icon(apk, info["icons"], icon_dest) or icon_dest.exists()
        changelog = top_changelog(WORKSPACE / entry["changelog_file"]) if entry.get("changelog_file") else ""

        apps.append({
            "id": pkg,
            "name": entry["name"],
            "developer": entry.get("developer", "ACN Development"),
            "category": entry["category"],
            "tagline": entry.get("tagline", ""),
            "description": entry["description"],
            "version_name": version,
            "version_code": info["version_code"],
            "min_sdk": info["min_sdk"],
            "size_bytes": apk.stat().st_size,
            "sha256": sha256(apk),
            "download_url": url,
            "asset_name": url.rsplit("/", 1)[-1],
            "release_tag": f"{pkg}-v{version}" if "download_url" not in entry else None,
            "local_apk": entry["apk"],
            "icon_url": store["icon_base_url"] + f"{pkg}.png" if has_icon else None,
            "screenshots": entry.get("screenshots") or [
                f"{store.get('screenshots_base_url', 'https://raw.githubusercontent.com/acnesia/acn-store/main/screenshots/')}{pkg}_1.png",
                f"{store.get('screenshots_base_url', 'https://raw.githubusercontent.com/acnesia/acn-store/main/screenshots/')}{pkg}_2.png"
            ],
            "changelog": changelog or entry.get("changelog", ""),
            "featured": bool(entry.get("featured")),
            "updated_at": datetime.fromtimestamp(apk.stat().st_mtime, timezone.utc).strftime("%Y-%m-%d"),
        })
        print(f"[Catalog] {entry['name']:<18} {pkg:<28} v{version} ({info['version_code']}) icon={'yes' if has_icon else 'no'}")

    catalog = {
        "schema": 1,
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "apps": apps,
    }
    # Build-only fields (local paths, release tags) go to publish_plan.json, never to the public catalog
    build_only = ("local_apk", "release_tag")
    plan = [{"id": a["id"], "local_apk": a["local_apk"], "release_tag": a["release_tag"], "asset_name": a["asset_name"]} for a in apps]
    (CATALOG_DIR / "publish_plan.json").write_text(json.dumps(plan, indent=2), encoding="utf-8")
    slim = {**catalog, "apps": [{k: v for k, v in a.items() if k not in build_only} for a in apps]}
    data = json.dumps(slim, ensure_ascii=False, indent=2)
    (CATALOG_DIR / "catalog.json").write_text(data, encoding="utf-8")
    ASSET_CATALOG.parent.mkdir(parents=True, exist_ok=True)
    ASSET_CATALOG.write_text(data, encoding="utf-8")
    # Icons and Screenshots are bundled too
    ASSET_ICONS.mkdir(parents=True, exist_ok=True)
    for icon in ICONS_DIR.glob("*.png"):
        (ASSET_ICONS / icon.name).write_bytes(icon.read_bytes())
    ASSET_SCREENSHOTS.mkdir(parents=True, exist_ok=True)
    for shot in SCREENSHOTS_DIR.glob("*.png"):
        (ASSET_SCREENSHOTS / shot.name).write_bytes(shot.read_bytes())
    print(f"[Catalog] {len(apps)} apps -> {CATALOG_DIR / 'catalog.json'}")


if __name__ == "__main__":
    main()
