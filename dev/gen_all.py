"""Regenerates the assets and data for every target and sorts them into the source folders.

python dev/gen_all.py <art folder>

Runs dev/gen_resources.py once per file in targets/, against that target's Minecraft jar from the Loom cache (build the
target once first so Loom has downloaded it). Files that come out the same for every target go in
common/src/main/resources; the rest go in each target's common/src/compat/<target>/resources. dev/generated.txt lists
what was written, so files that are no longer made get removed next time.
"""
import filecmp
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOOM = Path.home() / ".gradle/caches/fabric-loom"
ART = Path(sys.argv[1]).resolve()
MANIFEST = ROOT / "dev/generated.txt"
MAIN = "common/src/main/resources"


def properties(path):
    out = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            out[key.strip()] = value.strip()
    return out


def programmer_art(version):
    index = next((LOOM / "assets/indexes").glob(f"{version}-*.json"))
    digest = json.loads(index.read_text())["objects"]["minecraft/resourcepacks/programmer_art.zip"]["hash"]
    return LOOM / "assets/objects" / digest[:2] / digest


targets = {}
for path in sorted((ROOT / "targets").glob("*.properties")):
    props = properties(path)
    targets[path.stem] = (props["minecraft_version"], props["minecraft_range"].split("-")[0])

work = Path(tempfile.mkdtemp(prefix="bluestone-gen-"))
trees = {}
for name, (version, first) in targets.items():
    out = work / name
    args = [sys.executable, str(ROOT / "dev/gen_resources.py"), version, str(LOOM / version / "minecraft-client.jar"), str(out), str(ART),
            str(programmer_art(version))]
    # The jar of the first version the target covers, for the formats that changed within its range.
    if first != version:
        args.append(str(LOOM / first / "minecraft-client.jar"))
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL)
    trees[name] = {p.relative_to(out).as_posix(): p for p in out.rglob("*") if p.is_file()}
    print(f"{name} ({first}-{version}): {len(trees[name])} files")

written = set()
for path in sorted(set().union(*trees.values())):
    holders = [name for name in trees if path in trees[name]]
    first = trees[holders[0]][path]
    shared = len(holders) == len(trees) and all(filecmp.cmp(first, trees[name][path], shallow=False) for name in holders)
    for name in (holders[:1] if shared else holders):
        dest = f"{MAIN}/{path}" if shared else f"common/src/compat/{name}/resources/{path}"
        (ROOT / dest).parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(trees[name][path], ROOT / dest)
        written.add(dest)

old = set(MANIFEST.read_text(encoding="utf-8").split()) if MANIFEST.exists() else set()
for stale in sorted(old - written):
    (ROOT / stale).unlink(missing_ok=True)
MANIFEST.write_text("\n".join(sorted(written)) + "\n", encoding="utf-8")
shutil.rmtree(work)
print(f"{len(written)} files written, {len(old - written)} removed")
