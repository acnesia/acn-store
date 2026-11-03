"""Publish the ACN Store catalog and APKs to the public GitHub repo acnesia/acn-store.

  python tools/publish_store.py          -> dry run, prints what would be done
  python tools/publish_store.py --yes    -> creates the repo if needed, uploads releases, catalog and icons

Run tools/build_catalog.py first. Requires an authenticated `gh` CLI.
"""
import base64
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

STORE_DIR = Path(__file__).resolve().parent.parent
WORKSPACE = STORE_DIR.parent
CATALOG_DIR = STORE_DIR / "catalog"
APPLY = "--yes" in sys.argv


def gh(*args, check=True, input_data=None):
    res = subprocess.run(["gh", *args], capture_output=True, text=True, encoding="utf-8", input=input_data)
    if check and res.returncode != 0:
        raise RuntimeError(f"gh {' '.join(args[:3])} failed: {res.stderr.strip()}")
    return res


def step(msg):
    print(("[Publish] " if APPLY else "[DryRun] ") + msg)


def put_file(repo, path, data: bytes, message):
    existing = gh("api", f"repos/{repo}/contents/{path}", check=False)
    sha = json.loads(existing.stdout).get("sha") if existing.returncode == 0 else None
    if sha:
        current = json.loads(existing.stdout).get("content", "").replace("\n", "")
        if base64.b64decode(current) == data:
            return
    step(f"upload {path}")
    if not APPLY:
        return
    body = {"message": message, "content": base64.b64encode(data).decode()}
    if sha:
        body["sha"] = sha
    gh("api", "-X", "PUT", f"repos/{repo}/contents/{path}", "--input", "-", input_data=json.dumps(body))


def main():
    config = json.loads((CATALOG_DIR / "apps.json").read_text(encoding="utf-8"))
    repo = config["store"]["release_repo"]
    catalog = json.loads((CATALOG_DIR / "catalog.json").read_text(encoding="utf-8"))
    plan = json.loads((CATALOG_DIR / "publish_plan.json").read_text(encoding="utf-8"))
    by_id = {a["id"]: a for a in catalog["apps"]}

    if gh("repo", "view", repo, check=False).returncode != 0:
        step(f"create public repo {repo}")
        if APPLY:
            gh("repo", "create", repo, "--public", "--description", "ACN Store — catalogue public et APK des applications ACN Development")

    # Releases need at least one commit in the repo
    readme = (
        "# ACN Store\n\nCatalogue public des applications ACN Development.\n\n"
        "- `catalog.json` : catalogue lu par l'app ACN Store (versions, tailles, empreintes SHA-256)\n"
        "- `icons/` : icônes des applications\n"
        "- Releases : un tag `<package>-v<version>` par APK\n"
    )
    put_file(repo, "README.md", readme.encode("utf-8"), "docs: readme")

    # 1. Releases first: the catalog must never point to an APK that is not online yet
    with tempfile.TemporaryDirectory() as tmp:
        for entry in plan:
            tag = entry["release_tag"]
            if not tag:
                continue
            app = by_id[entry["id"]]
            view = gh("release", "view", tag, "-R", repo, "--json", "assets", check=False)
            assets = [a["name"] for a in json.loads(view.stdout)["assets"]] if view.returncode == 0 else None
            if assets is not None and entry["asset_name"] in assets:
                continue
            src = WORKSPACE / entry["local_apk"]
            staged = Path(tmp) / entry["asset_name"]
            shutil.copyfile(src, staged)
            if assets is None:
                step(f"create release {tag} with {entry['asset_name']}")
                if APPLY:
                    notes = f"{app['name']} {app['version_name']}\n\nSHA-256 : `{app['sha256']}`"
                    gh("release", "create", tag, str(staged), "-R", repo, "--title", f"{app['name']} v{app['version_name']}", "--notes", notes)
            else:
                step(f"upload {entry['asset_name']} to existing release {tag}")
                if APPLY:
                    gh("release", "upload", tag, str(staged), "-R", repo, "--clobber")

    # 2. Icons, Screenshots, then the catalog itself
    for icon in sorted((CATALOG_DIR / "icons").glob("*.png")):
        put_file(repo, f"icons/{icon.name}", icon.read_bytes(), f"icons: {icon.stem}")
    for shot in sorted((CATALOG_DIR / "screenshots").glob("*.png")):
        put_file(repo, f"screenshots/{shot.name}", shot.read_bytes(), f"screenshots: {shot.stem}")
    put_file(repo, "catalog.json", (CATALOG_DIR / "catalog.json").read_bytes(), f"catalog: {catalog['generated_at']}")
    step("done" if APPLY else "dry run finished, re-run with --yes to publish")


if __name__ == "__main__":
    main()
