"""Writes Bluestone's assets and data for one Minecraft version, copying the matching vanilla files with redstone swapped
for bluestone. dev/gen_all.py runs it for every target and sorts the results into the source folders.

python dev/gen_resources.py <version> <client jar> <output folder> <art folder> <Programmer Art zip> [<oldest client jar>]

The client jars are in the Loom cache (fabric-loom/<version>/minecraft-client.jar); the Programmer Art pack is
resourcepacks/programmer_art.zip in the version's asset index. The art folder holds default/ and programmer art/,
with one folder per thing (bluestone torch, purplestone converter, ...). Each of those has block/, item/ or palettes/
folders that say where a texture goes, with the mod's textures next to the redstone originals they were drawn from.
The oldest jar is that of the first version the target covers, when it differs: the 26.x pack gets an overlay with
26.1's formats, and a range that starts before a format change gets the older files too.
"""
import itertools
import json
import re
import shutil
import sys
import zipfile
from pathlib import Path

VERSION = sys.argv[1]
z = zipfile.ZipFile(sys.argv[2])
OUT = Path(sys.argv[3])
ART = Path(sys.argv[4])           # bluestone/textures (the user's art)
programmer_art_zip = zipfile.ZipFile(sys.argv[5])
oldest = zipfile.ZipFile(sys.argv[6]) if len(sys.argv) > 6 else z
OLDEST = json.loads(oldest.read("version.json"))["id"]


def at_least(version, than):
    return tuple(int(x) for x in version.split(".")) >= tuple(int(x) for x in than.split("."))


def since(version):
    return at_least(VERSION, version)


def oldest_since(version):
    return at_least(OLDEST, version)


# The 26.x pack has an overlay for 26.1 and 26.2, whose formats differ from 26.3's.
legacy = oldest if since("26.1") and oldest is not z else None


# Each format the pack is written in: the jar it is copied from and the folder it goes in (the overlay for 26.1).
FORMATS = [(z, "")] + ([(legacy, "mc26.1/")] if legacy else [])
# Data folders were plural before 1.21.
PLURAL = "" if since("1.21") else "s"
RECIPE, LOOT, ADVANCEMENT = "recipe" + PLURAL, "loot_table" + PLURAL, "advancement" + PLURAL
BLOCK_TAGS, ITEM_TAGS = "tags/block" + PLURAL, "tags/item" + PLURAL


def programmer_art(path):
    # The pack only has the models it changes (before 1.21 not the repeater's); the rest are the game's own.
    if path not in programmer_art_zip.namelist():
        return vanilla(path)
    return json.loads(programmer_art_zip.read(path))


def vanilla(path, jar=None):
    return json.loads((jar or z).read(path))


def write(rel, data):
    p = OUT / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def swap_models(data, mapping):
    text = json.dumps(data)
    for old, new in mapping:
        text = text.replace('"' + old, '"' + new)
    return json.loads(text)


A = "assets/bluestone/"
D = "data/bluestone/"

# Blockstates
write(A + "blockstates/bluestone_wire.json", vanilla("assets/minecraft/blockstates/redstone_wire.json"))
write(A + "blockstates/purplestone_wire.json", vanilla("assets/minecraft/blockstates/redstone_wire.json"))
for name in ["bluestone_block", "purplestone_block", "greenstone_block", "bluestone_ore", "deepslate_bluestone_ore"]:
    write(A + f"blockstates/{name}.json", {"variants": {"": {"model": f"bluestone:block/{name}"}}})
for name, src, prefix in [
    ("bluestone_torch", "redstone_torch", "redstone_torch"),
    ("bluestone_wall_torch", "redstone_wall_torch", "redstone_wall_torch"),
    ("purplestone_torch", "redstone_torch", "redstone_torch"),
    ("purplestone_wall_torch", "redstone_wall_torch", "redstone_wall_torch"),
    ("greenstone_torch", "redstone_torch", "redstone_torch"),
    ("greenstone_wall_torch", "redstone_wall_torch", "redstone_wall_torch"),
    ("bluestone_repeater", "repeater", "repeater"),
    ("bluestone_comparator", "comparator", "comparator"),
    ("bluestone_powered_rail", "powered_rail", "powered_rail"),
    ("bluestone_activator_rail", "activator_rail", "activator_rail"),
    ("bluestone_detector_rail", "detector_rail", "detector_rail"),
]:
    data = vanilla(f"assets/minecraft/blockstates/{src}.json")
    write(A + f"blockstates/{name}.json", swap_models(data, [("minecraft:block/" + prefix, "bluestone:block/" + name)]))

# Block models
for name in ["bluestone_block", "purplestone_block", "greenstone_block", "bluestone_ore", "deepslate_bluestone_ore"]:
    write(A + f"models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"bluestone:block/{name}"}})

# The converter box is built like a chiseled bookshelf: each face is nine flat pieces, 5x5 corners, 6x5 sides and a 6x6
# middle; the light at the middle of each edge lies in the side piece there, and the face's own light in the middle
# piece.
AXES = {"down": 1, "up": 1, "north": 2, "south": 2, "west": 0, "east": 0}
FAR = {"down": False, "up": True, "north": False, "south": True, "west": False, "east": True}
SPANS = [(0, 5), (5, 11), (11, 16)]


def face_piece(face, spans):
    """A flat piece of a face: spans maps the face's two other axes to (from, to)."""
    lo, hi = [0, 0, 0], [0, 0, 0]
    lo[AXES[face]] = hi[AXES[face]] = 16 if FAR[face] else 0
    for axis, (a, b) in spans.items():
        lo[axis], hi[axis] = a, b
    return lo, hi


pieces, frame = [], {face: [] for face in AXES}
for face in AXES:
    u, v = [axis for axis in range(3) if axis != AXES[face]]
    for su in SPANS:
        for sv in SPANS:
            lo, hi = face_piece(face, {u: su, v: sv})
            pieces.append({"from": lo, "to": hi, "faces": {face: {"texture": "#all", "cullface": face}}})
            if su != SPANS[1] and sv != SPANS[1]:
                frame[face].append({"from": lo, "to": hi, "faces": {face: {"texture": "#all"}}})
BOX_TEXTURES = {"all": "bluestone:block/converter_box", "particle": "#all"}
write(A + "models/block/converter_box.json", {"parent": "minecraft:block/block", "textures": BOX_TEXTURES, "elements": pieces})
write(A + "blockstates/converter_box.json", {"variants": {"": {"model": "bluestone:block/converter_box"}}})
for face in AXES:
    write(A + f"models/block/converter_box_frame_{face}.json", {"textures": BOX_TEXTURES, "elements": frame[face]})
write(A + "blockstates/converter_box_frame.json", {"variants": {f"face={face}": {"model": f"bluestone:block/converter_box_frame_{face}"} for face in AXES}})
LIGHT_TEXTURES = {"off": "bluestone:block/converter_box"}
LIGHT_TEXTURES.update({colour: f"bluestone:block/converter_box_{colour}_overlay" for colour in ["redstone", "bluestone", "purplestone", "greenstone"]})
light_parts = []
for face in AXES:
    for edge in AXES:
        if AXES[edge] == AXES[face]:
            continue
        third = 3 - AXES[face] - AXES[edge]
        lo, hi = face_piece(face, {AXES[edge]: SPANS[2] if FAR[edge] else SPANS[0], third: SPANS[1]})
        template = f"template_converter_box_light_{face}_{edge}"
        write(A + f"models/block/{template}.json", {"textures": {"particle": "#light"},
                                                    "elements": [{"from": lo, "to": hi, "faces": {face: {"texture": "#light"}}}]})
        for colour, texture in LIGHT_TEXTURES.items():
            name = f"converter_box_light_{face}_{edge}_{colour}"
            write(A + f"models/block/{name}.json", {"parent": f"bluestone:block/{template}", "textures": {"light": texture}})
            light_parts.append({"when": {"face": face, "edge": edge, "color": colour}, "apply": {"model": f"bluestone:block/{name}"}})
write(A + "blockstates/converter_box_light.json", {"multipart": light_parts})
center_parts = []
for face in AXES:
    u, v = [axis for axis in range(3) if axis != AXES[face]]
    lo, hi = face_piece(face, {u: SPANS[1], v: SPANS[1]})
    template = f"template_converter_box_center_{face}"
    write(A + f"models/block/{template}.json", {"textures": {"particle": "#light"},
                                                "elements": [{"from": lo, "to": hi, "faces": {face: {"texture": "#light"}}}]})
    for colour, texture in LIGHT_TEXTURES.items():
        name = f"converter_box_center_{face}_{colour}"
        write(A + f"models/block/{name}.json", {"parent": f"bluestone:block/{template}", "textures": {"light": texture}})
        center_parts.append({"when": {"face": face, "color": colour}, "apply": {"model": f"bluestone:block/{name}"}})
write(A + "blockstates/converter_box_center.json", {"multipart": center_parts})

# The game's redstone torch models with the mod's textures (their parents changed over the versions).
# Before 1.21.2 the mod's torches, also those on its repeaters, comparators and converter, are their Programmer Art
# versions on the game's shapes of then (copied under block/old/, as the normal textures stay for newer versions).
OLD_TORCH = "" if since("1.21.2") else "old/"


def torch_models(source, prefix, textures=""):
    for stone in ["bluestone", "purplestone", "greenstone"]:
        for theirs in ["redstone_torch", "redstone_torch_off", "redstone_wall_torch", "redstone_wall_torch_off"]:
            model = source(f"assets/minecraft/models/block/{theirs}.json")
            model["parent"] = "minecraft:" + model["parent"].removeprefix("minecraft:")
            model["textures"] = {k: v.removeprefix("minecraft:").replace("block/redstone_torch", f"bluestone:block/{textures}{stone}_torch") for k, v in model["textures"].items()}
            write(prefix + f"models/block/{theirs.replace('redstone', stone)}.json", model)


torch_models(vanilla, A, OLD_TORCH)

TORCH_TEXTURES = {"unlit": f"bluestone:block/{OLD_TORCH}bluestone_torch_off", "lit": f"bluestone:block/{OLD_TORCH}bluestone_torch"}


def diode_model(vanilla_name, ours, top, torches=TORCH_TEXTURES):
    parent = vanilla(f"assets/minecraft/models/block/{vanilla_name}.json")
    textures = {"particle": top, "top": top}
    for key, value in torches.items():
        if key in parent["textures"]:
            textures[key] = value
    write(A + f"models/block/{ours}.json", {"parent": f"minecraft:block/{vanilla_name}", "textures": textures})


for delay in range(1, 5):
    for on in ["", "_on"]:
        for locked in ["", "_locked"]:
            diode_model(f"repeater_{delay}tick{on}{locked}", f"bluestone_repeater_{delay}tick{on}{locked}", f"bluestone:block/bluestone_repeater{on}")
for variant in ["", "_on", "_subtract", "_on_subtract"]:
    on = "_on" if variant.startswith("_on") else ""
    diode_model(f"comparator{variant}", f"bluestone_comparator{variant}", f"bluestone:block/bluestone_comparator{on}")

for rail in ["powered_rail", "activator_rail", "detector_rail"]:
    for on in ["", "_on"]:
        for shape, parent in [("", "rail_flat"), ("_raised_ne", "template_rail_raised_ne"), ("_raised_sw", "template_rail_raised_sw")]:
            write(A + f"models/block/bluestone_{rail}{on}{shape}.json",
                  {"parent": f"minecraft:block/{parent}", "textures": {"rail": f"bluestone:block/bluestone_{rail}{on}"}})


# The purplestone converter: shaped like a four-tick repeater, with the input-end torch in the input colour and the
# output-end torch in the output colour.
TORCHES = {"red": ("minecraft:block/redstone_torch", "minecraft:block/redstone_torch_off"),
           "blue": (f"bluestone:block/{OLD_TORCH}bluestone_torch", f"bluestone:block/{OLD_TORCH}bluestone_torch_off")}
MODES = {"red_to_blue": ("red", "blue"), "blue_to_red": ("blue", "red")}


def torch_end(element):
    """Which torch an element of the repeater_4tick models belongs to: the output torch sits at z 2-4, the input one at z 12-14."""
    z = (element["from"][2] + element["to"][2]) / 2
    return "out" if abs(z - 3) < abs(z - 13) else "in"


def converter_model(parent):
    model = json.loads(json.dumps(parent))
    for element in model["elements"]:
        for face in element["faces"].values():
            if face["texture"] in ("#lit", "#unlit"):
                face["texture"] = "#" + torch_end(element) + "_" + face["texture"][1:]
    return model


def converter_models(source, prefix):
    for mode, (input_color, output_color) in MODES.items():
        for on in ["", "_on"]:
            for locked in ["", "_locked"]:
                model = converter_model(source("repeater_4tick" + on + locked))
                top = "bluestone:block/purplestone_converter" + on
                model["textures"] = {"particle": top, "slab": "minecraft:block/smooth_stone", "top": top, "lock": "minecraft:block/bedrock",
                                     "in_lit": TORCHES[input_color][0], "in_unlit": TORCHES[input_color][1],
                                     "out_lit": TORCHES[output_color][0], "out_unlit": TORCHES[output_color][1]}
                write(prefix + "models/block/purplestone_converter_" + mode + on + locked + ".json", model)


converter_models(lambda name: vanilla("assets/minecraft/models/block/" + name + ".json"), A)
converter_models(lambda name: programmer_art("assets/minecraft/models/block/" + name + ".json"), "programmer_art/" + A)
ROTATION = {"south": 0, "west": 90, "north": 180, "east": 270}
variants = {}
for facing, y in ROTATION.items():
    for mode in MODES:
        for powered in [False, True]:
            for locked in [False, True]:
                variant = {"model": "bluestone:block/purplestone_converter_" + mode + ("_on" if powered else "") + ("_locked" if locked else "")}
                if y:
                    variant["y"] = y
                key = "facing=%s,locked=%s,mode=%s,powered=%s" % (facing, str(locked).lower(), mode, str(powered).lower())
                variants[key] = variant
write(A + "blockstates/purplestone_converter.json", {"variants": variants})


# The greenstone repeater and comparator sit on any face (attached) and point any way along it (output).
ROTATIONS = {
    ("down", "east"): (0, 90, 0), ("down", "north"): (0, 0, 0), ("down", "south"): (0, 180, 0), ("down", "west"): (0, 270, 0),
    ("east", "down"): (90, 270, 0), ("east", "north"): (0, 0, 270), ("east", "south"): (0, 180, 270), ("east", "up"): (270, 90, 0),
    ("north", "down"): (90, 180, 0), ("north", "east"): (270, 0, 90), ("north", "up"): (270, 0, 0), ("north", "west"): (270, 0, 270),
    ("south", "down"): (90, 0, 0), ("south", "east"): (90, 0, 270), ("south", "up"): (270, 180, 0), ("south", "west"): (90, 0, 90),
    ("up", "east"): (180, 270, 0), ("up", "north"): (180, 180, 0), ("up", "south"): (180, 0, 0), ("up", "west"): (180, 90, 0),
    ("west", "down"): (90, 90, 0), ("west", "north"): (0, 0, 90), ("west", "south"): (0, 180, 90), ("west", "up"): (270, 270, 0),
}


AXES = {"down": "y", "up": "y", "north": "z", "south": "z", "east": "x", "west": "x"}


def rotated(model, attached, output):
    # States that point into their own face can't be placed; they get a model anyway so the game doesn't warn.
    if (attached, output) not in ROTATIONS:
        output = next(d for d in ["north", "south", "east", "west", "up", "down"] if AXES[d] != AXES[attached])
    variant = {"model": model}
    for axis, angle in zip("xyz", ROTATIONS[(attached, output)]):
        if angle:
            variant[axis] = angle
    return variant


ALL_PAIRS = [(a, b) for a in AXES for b in AXES]
# Greenstone repeaters and comparators have their own sides and back, greenstone_back, in place of smooth stone.
GREEN_TORCHES = {"unlit": f"bluestone:block/{OLD_TORCH}greenstone_torch_off", "lit": f"bluestone:block/{OLD_TORCH}greenstone_torch",
                 "slab": "bluestone:block/greenstone_back"}
variants = {}
for delay in range(1, 5):
    for on in ["", "_on"]:
        for locked in ["", "_locked"]:
            diode_model(f"repeater_{delay}tick{on}{locked}", f"greenstone_repeater_{delay}tick{on}{locked}", f"bluestone:block/greenstone_repeater{on}", GREEN_TORCHES)
            for attached, output in ALL_PAIRS:
                key = f"attached={attached},delay={delay},locked={str(bool(locked)).lower()},output={output},powered={str(bool(on)).lower()}"
                variants[key] = rotated(f"bluestone:block/greenstone_repeater_{delay}tick{on}{locked}", attached, output)
write(A + "blockstates/greenstone_repeater.json", {"variants": variants})
variants = {}
for variant in ["", "_on", "_subtract", "_on_subtract"]:
    on = "_on" if variant.startswith("_on") else ""
    diode_model(f"comparator{variant}", f"greenstone_comparator{variant}", f"bluestone:block/greenstone_comparator{on}", GREEN_TORCHES)
    for attached, output in ALL_PAIRS:
        key = f"attached={attached},mode={'subtract' if 'subtract' in variant else 'compare'},output={output},powered={str(bool(on)).lower()}"
        variants[key] = rotated(f"bluestone:block/greenstone_comparator{variant}", attached, output)
write(A + "blockstates/greenstone_comparator.json", {"variants": variants})


# Sticky rails and chain rails.
STICKY = {"sticky_rail": "rail", "sticky_powered_rail": "powered_rail", "sticky_bluestone_powered_rail": "powered_rail",
          "sticky_detector_rail": "detector_rail", "sticky_bluestone_detector_rail": "detector_rail",
          "sticky_activator_rail": "activator_rail", "sticky_bluestone_activator_rail": "activator_rail"}
for ours, theirs in STICKY.items():
    blockstate = vanilla(f"assets/minecraft/blockstates/{theirs}.json")
    for model_name in {v["model"].split("/")[-1] for v in blockstate["variants"].values()}:
        model = vanilla(f"assets/minecraft/models/block/{model_name}.json")
        model["textures"] = {k: v.replace("minecraft:block/" + theirs, "bluestone:block/" + ours) for k, v in model["textures"].items()}
        write(A + f"models/block/{model_name.replace(theirs, ours, 1)}.json", model)
    write(A + f"blockstates/{ours}.json", swap_models(blockstate, [("minecraft:block/" + theirs, "bluestone:block/" + ours)]))

# Chain rails have an arrow on their texture pointing the way they drive carts, towards the texture's top edge.
def flipped(model_name, textures, turned):
    model = vanilla(f"assets/minecraft/models/block/{model_name}.json")
    if turned:
        for element in model["elements"]:
            for face in element["faces"].values():
                face["rotation"] = (face.get("rotation", 0) + 180) % 360
    model["textures"] = dict(model.get("textures", {}), **textures)
    return model


TURN = {0: "south", 90: "west", 180: "north", 270: "east"}
CHAIN_RAILS = ["chain_rail", "bluestone_chain_rail", "sticky_chain_rail", "sticky_bluestone_chain_rail"]
for rail in CHAIN_RAILS:
    for on in ["", "_on"]:
        texture = {"rail": f"bluestone:block/{rail}{on}", "particle": f"bluestone:block/{rail}{on}"}
        for kind, template in [("flat", "rail_flat"), ("raised_ne", "template_rail_raised_ne"), ("raised_sw", "template_rail_raised_sw")]:
            write(A + f"models/block/{rail}_{kind}{on}.json", flipped(template, texture, True))
            write(A + f"models/block/{rail}_{kind}_reversed{on}.json", flipped(template, texture, False))
    variants = {}
    for key, variant in vanilla("assets/minecraft/blockstates/powered_rail.json")["variants"].items():
        props = dict(part.split("=") for part in key.split(","))
        shape = props["shape"]
        on = "_on" if props["powered"] == "true" else ""
        kind = "raised_ne" if "raised_ne" in variant["model"] else "raised_sw" if "raised_sw" in variant["model"] else "flat"
        y = variant.get("y", 0)
        for reversed_ in [False, True]:
            axis_z = shape in ("north_south", "ascending_north", "ascending_south")
            drive = ("north" if reversed_ else "south") if axis_z else ("west" if reversed_ else "east")
            out = {"model": f"bluestone:block/{rail}_{kind}" + ("" if TURN[y] == drive else "_reversed") + on}
            if y:
                out["y"] = y
            variants[f"powered={props['powered']},reversed={str(reversed_).lower()},shape={shape}"] = out
    # Chain rails are straight, so they never curve; the curved shapes get the flat model to keep the game quiet.
    for shape in ["south_east", "south_west", "north_west", "north_east"]:
        for reversed_ in ["false", "true"]:
            for powered in ["false", "true"]:
                variants[f"powered={powered},reversed={reversed_},shape={shape}"] = {"model": f"bluestone:block/{rail}_flat"}
    write(A + f"blockstates/{rail}.json", {"variants": variants})

# On walls and ceilings: the floor model turned onto the face it sits on, running the way it travels (a chain rail's
# arrow points that way), lit while powered.
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}


def plain_rail(flat, sloped):
    return lambda on, arrow_ahead, bent: sloped if bent else flat


def lit_rail(name):
    return lambda on, arrow_ahead, bent: f"bluestone:block/{name}{'_on' if on else ''}{'_raised_ne' if bent else ''}"


# A chain rail's arrow points down its slope model, so bent at the end it travels towards it needs the turned one.
def chain_rail(name):
    return lambda on, arrow_ahead, bent: (f"bluestone:block/{name}_raised_ne{'_reversed' if arrow_ahead else ''}{'_on' if on else ''}" if bent
                                          else f"bluestone:block/{name}_flat{'_on' if on else ''}")


ATTACHED_MODELS = {"sticky_rail": plain_rail("bluestone:block/sticky_rail", "bluestone:block/sticky_rail_raised_ne")}
for kind in ["powered", "bluestone_powered", "detector", "bluestone_detector", "activator", "bluestone_activator"]:
    ATTACHED_MODELS[f"sticky_{kind}_rail"] = lit_rail(f"sticky_{kind}_rail")
for rail in ["sticky_chain_rail", "sticky_bluestone_chain_rail"]:
    ATTACHED_MODELS[rail] = chain_rail(rail)


UNIT = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}


def cross(a, b):
    (ax, ay, az), (bx, by, bz) = UNIT[a], UNIT[b]
    return {v: k for k, v in UNIT.items()}.get((ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx))


# The plain sticky rail also curves (AttachedRailBlock.Curve): its other end is to the right of the way it travels
# (travel x the way out of the face) or to the left.
def curved(attached, travel, curve):
    right = cross(travel, OPPOSITE[attached])
    other = right if curve == "right" else OPPOSITE[right]
    north = OPPOSITE[travel] if cross(attached, OPPOSITE[travel]) == other else OPPOSITE[other]
    return rotated("bluestone:block/sticky_rail_corner", attached, north)


for rail, model in ATTACHED_MODELS.items():
    curves = rail == "sticky_rail"
    variants = {}
    for (attached, travel) in ALL_PAIRS:
        if attached == "down":
            continue
        straight = ",curve=none" if curves else ""
        for powered in ["false", "true"]:
            on = powered == "true"
            variants[f"attached={attached},bend=none{straight},powered={powered},travel={travel}"] = rotated(model(on, False, False), attached, OPPOSITE[travel])
            for bend, toward in [("ahead", travel), ("behind", OPPOSITE[travel])]:
                variants[f"attached={attached},bend={bend}{straight},powered={powered},travel={travel}"] = rotated(model(on, bend == "ahead", True), attached, toward)
            if curves:
                for curve in ["left", "right"]:
                    variants[f"attached={attached},curve={curve},powered={powered},travel={travel}"] = (
                        curved(attached, travel, curve) if AXES[travel] != AXES[attached] else rotated(model(on, False, False), attached, OPPOSITE[travel]))
    write(A + f"blockstates/attached_{rail}.json", {"variants": variants})
FLOOR_RAILS = CHAIN_RAILS[:2] + list(STICKY) + CHAIN_RAILS[2:]

# Greenstone paste is redstone dust on any face, placed one face at a time: the game's dust models with their own
# textures, and the game's dust blockstate once for each face, turned onto it.
def paste_sprite(value):
    if isinstance(value, dict):
        return dict(value, sprite=paste_sprite(value["sprite"]))
    name = value.split("/")[-1]
    return "bluestone:block/" + name.replace("redstone_dust", "greenstone_paste") if name.startswith("redstone_dust") else value


for name in ["dot", "side0", "side1", "side_alt0", "side_alt1"]:
    model = vanilla(f"assets/minecraft/models/block/redstone_dust_{name}.json")
    textures = {k: paste_sprite(v) for k, v in model.get("textures", {}).items()}
    if "parent" in model:
        textures.setdefault("particle", "bluestone:block/greenstone_paste_dot")
        # Texture objects with force_translucent are new in 26.1.
        textures.setdefault("overlay", {"sprite": "bluestone:block/greenstone_paste_overlay", "force_translucent": True} if since("26.1")
                            else "bluestone:block/greenstone_paste_overlay")
    model["textures"] = textures
    write(A + f"models/block/greenstone_paste_{name}.json", model)

VECTORS = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
NAMES = {v: k for k, v in VECTORS.items()}


def paste_frame(attached):
    north = "north" if attached in ("down", "up") else "up"
    (ax, ay, az), (nx, ny, nz) = VECTORS[attached], VECTORS[north]
    east = NAMES[(ay * nz - az * ny, az * nx - ax * nz, ax * ny - ay * nx)]
    return {"north": north, "east": east, "south": NAMES[tuple(-c for c in VECTORS[north])], "west": NAMES[tuple(-c for c in VECTORS[east])]}


# Where two faces both border a direction, "shared" says which draws its line: all, the first or the last of them in the
# order below (GreenstonePasteBlock.Shared).
ORDER = ["down", "up", "north", "south", "west", "east"]
AXIS = {"down": "y", "up": "y", "north": "z", "south": "z", "west": "x", "east": "x"}


def draws(face, way):
    """The conditions, any one of which makes a face draw its line towards way."""
    others = [g for g in ORDER if AXIS[g] != AXIS[way] and g != face]
    return [{face: "face", way: "face"},
            {face: "face", way: "line", "shared": "all"},
            dict({face: "face", way: "line", "shared": "first"}, **{g: "none|line" for g in others if ORDER.index(g) < ORDER.index(face)}),
            dict({face: "face", way: "line", "shared": "last"}, **{g: "none|line" for g in others if ORDER.index(g) > ORDER.index(face)})]


def merge(a, b):
    """Both conditions at once, or None if they can't both hold."""
    out = dict(a)
    for key, value in b.items():
        if key in out:
            both = [v for v in out[key].split("|") if v in value.split("|")]
            if not both:
                return None
            out[key] = "|".join(both)
        else:
            out[key] = value
    return out


paste_parts = []
for face in VECTORS:
    frame = paste_frame(face)
    for part in vanilla("assets/minecraft/blockstates/redstone_wire.json")["multipart"]:
        apply = part["apply"]
        if apply["model"].endswith("_up"):
            continue
        # A part turned on the floor first (its own y), then onto the face.
        turned = ["north", "east", "south", "west"][apply.get("y", 0) // 90]
        variant = rotated(apply["model"].replace("minecraft:block/redstone_dust_", "bluestone:block/greenstone_paste_"), face, frame[turned])
        when = part["when"]
        if "OR" in when:
            # The dot in the middle: on its own when a lone piece has been right-clicked into a dot, and where two lines
            # meet at a corner.
            groups = [dict({face: "face"}, **{frame[side]: "none" for side in ["north", "east", "south", "west"]})]
            for corner in when["OR"]:
                sides = [side for side, value in corner.items() if value != "none"]
                if len(sides) != 2:
                    continue
                for first in draws(face, frame[sides[0]]):
                    for second in draws(face, frame[sides[1]]):
                        both = merge(first, second)
                        if both is not None and both not in groups:
                            groups.append(both)
            when = {"OR": groups}
        else:
            (side, _), = when.items()
            when = {"OR": draws(face, frame[side])}
        paste_parts.append({"when": when, "apply": variant})
write(A + "blockstates/greenstone_paste.json", {"multipart": paste_parts})

# Item models and item definitions
GENERATED = {
    "bluestone": "bluestone:item/bluestone",
    "purplestone": "bluestone:item/purplestone",
    "purplestone_converter": "bluestone:item/purplestone_converter",
    "bluestone_torch": f"bluestone:block/{OLD_TORCH}bluestone_torch",
    "purplestone_torch": f"bluestone:block/{OLD_TORCH}purplestone_torch",
    "greenstone_paste": "bluestone:item/greenstone_paste",
    "greenstone_torch": f"bluestone:block/{OLD_TORCH}greenstone_torch",
    "greenstone_repeater": "bluestone:item/greenstone_repeater",
    "greenstone_comparator": "bluestone:item/greenstone_comparator",
    "bluestone_repeater": "bluestone:item/bluestone_repeater",
    "bluestone_comparator": "bluestone:item/bluestone_comparator",
    "bluestone_powered_rail": "bluestone:block/bluestone_powered_rail",
    "bluestone_activator_rail": "bluestone:block/bluestone_activator_rail",
    "bluestone_detector_rail": "bluestone:block/bluestone_detector_rail",
}
GENERATED.update({rail: f"bluestone:block/{rail}" for rail in FLOOR_RAILS})
# Item definitions (items/) are new in 1.21.4; before that the game reads models/item/<item>.json.
for name, texture in GENERATED.items():
    write(A + f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": texture}})
    if since("1.21.4"):
        write(A + f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"bluestone:item/{name}"}})
for name in ["bluestone_block", "purplestone_block", "greenstone_block", "converter_box", "bluestone_ore", "deepslate_bluestone_ore"]:
    if since("1.21.4"):
        write(A + f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"bluestone:block/{name}"}})
    if not oldest_since("1.21.4"):
        write(A + f"models/item/{name}.json", {"parent": f"bluestone:block/{name}"})

# Textures: the user's art, and the Programmer Art versions in the folder Fabric reads (NeoForge serves it as a pack).
OURS = re.compile(r"bluestone|purplestone|greenstone|converter|sticky|chain_rail")
for style, dst_dir in [("default", OUT / "assets/bluestone/textures"), ("programmer art", OUT / "programmer_art/assets/bluestone/textures")]:
    for png in sorted((ART / style).glob("*/*/*.png")):
        if not OURS.search(png.name):
            continue
        kind = png.parent.name
        # 26.3 reads trim palettes from palettes/trim/, 26.1 and 26.2 from trims/color_palettes/.
        for folder in ["palettes/trim", "trims/color_palettes"] if kind == "palettes" else [kind]:
            dst = dst_dir / folder / png.name
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(png, dst)
if OLD_TORCH:
    for stone in ["bluestone", "purplestone", "greenstone"]:
        for state in ["", "_off"]:
            dst = OUT / A / "textures/block/old" / f"{stone}_torch{state}.png"
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ART / "programmer art" / f"{stone} torch/block/{stone}_torch{state}.png", dst)
# Programmer Art puts the redstone torch on the old plain torch shape; the other torches follow it.
torch_models(programmer_art, "programmer_art/" + A)
# Pack formats are a range from 1.21.9 and a single number before.
PACK_VERSION = json.loads(z.read("version.json"))["pack_version"]
RESOURCE_FORMAT = PACK_VERSION.get("resource_major", PACK_VERSION.get("resource"))
pack_description = {"translate": "pack.bluestone.programmer_art.description"}
if legacy:
    write("programmer_art/pack.mcmeta", {"pack": {"description": pack_description, "min_format": 84, "max_format": 97}})
elif since("1.21.9"):
    write("programmer_art/pack.mcmeta", {"pack": {"description": pack_description, "min_format": RESOURCE_FORMAT, "max_format": RESOURCE_FORMAT}})
else:
    write("programmer_art/pack.mcmeta", {"pack": {"description": pack_description, "pack_format": RESOURCE_FORMAT}})
# NeoForge makes up a mod's own pack metadata only from 20.4 and Forge never does, so jars before 1.20.5 carry one. It
# serves the assets and the data of every version in the range.
if not since("1.20.5"):
    formats = [f for v in (PACK_VERSION, json.loads(oldest.read("version.json"))["pack_version"]) for f in (v["resource"], v["data"])]
    write("pack.mcmeta", {"pack": {"description": "Bluestone", "pack_format": PACK_VERSION["data"],
                                   "supported_formats": [min(formats), max(formats)]}})

# Language
LANG = {
    "item.bluestone.bluestone": "Bluestone Dust",
    "block.bluestone.bluestone_wire": "Bluestone Wire",
    "block.bluestone.bluestone_torch": "Bluestone Torch",
    "block.bluestone.bluestone_block": "Block of Bluestone",
    "block.bluestone.bluestone_ore": "Bluestone Ore",
    "block.bluestone.deepslate_bluestone_ore": "Deepslate Bluestone Ore",
    "block.bluestone.bluestone_repeater": "Bluestone Repeater",
    "block.bluestone.bluestone_comparator": "Bluestone Comparator",
    "block.bluestone.bluestone_powered_rail": "Bluestone Powered Rail",
    "block.bluestone.bluestone_activator_rail": "Bluestone Activator Rail",
    "block.bluestone.bluestone_detector_rail": "Bluestone Detector Rail",
    "itemGroup.bluestone.main": "Bluestone: Over-Powered",
    "item.bluestone.purplestone": "Purplestone Dust",
    "block.bluestone.purplestone_wire": "Purplestone Wire",
    "block.bluestone.purplestone_torch": "Purplestone Torch",
    "block.bluestone.purplestone_block": "Block of Purplestone",
    "block.bluestone.greenstone_paste": "Greenstone Paste",
    "block.bluestone.greenstone_torch": "Greenstone Torch",
    "block.bluestone.greenstone_block": "Block of Greenstone",
    "block.bluestone.greenstone_repeater": "Greenstone Repeater",
    "block.bluestone.greenstone_comparator": "Greenstone Comparator",
    "block.bluestone.converter_box": "Converter Box",
    "bluestone.options.title": "Bluestone: Over-Powered",
    "bluestone.options.first_person": "View Turns in First Person",
    "bluestone.options.third_person": "View Turns in Third Person",
    "bluestone.options.turn_speed": "Cart Turn Speed",
    "bluestone.options.turn_speed.slow": "Slow",
    "bluestone.options.turn_speed.normal": "Normal",
    "bluestone.options.turn_speed.fast": "Fast",
    "block.bluestone.chain_rail": "Redstone Chain Rail",
    "block.bluestone.bluestone_chain_rail": "Bluestone Chain Rail",
    "block.bluestone.sticky_rail": "Sticky Rail",
    "block.bluestone.sticky_powered_rail": "Sticky Redstone Powered Rail",
    "block.bluestone.sticky_bluestone_powered_rail": "Sticky Bluestone Powered Rail",
    "block.bluestone.sticky_detector_rail": "Sticky Redstone Detector Rail",
    "block.bluestone.sticky_bluestone_detector_rail": "Sticky Bluestone Detector Rail",
    "block.bluestone.sticky_activator_rail": "Sticky Redstone Activator Rail",
    "block.bluestone.sticky_bluestone_activator_rail": "Sticky Bluestone Activator Rail",
    "block.bluestone.sticky_chain_rail": "Sticky Redstone Chain Rail",
    "block.bluestone.sticky_bluestone_chain_rail": "Sticky Bluestone Chain Rail",
    "block.bluestone.purplestone_converter": "Purplestone Converter",
    "trim_material.bluestone.bluestone": "Bluestone Material",
    "trim_material.bluestone.purplestone": "Purplestone Material",
    "trim_material.bluestone.greenstone": "Greenstone Material",
    "pack.bluestone.programmer_art": "Bluestone: Over-Powered Programmer Art",
    "pack.bluestone.programmer_art.description": "Programmer Art textures for Bluestone: Over-Powered",
}
# Wall torches and rails on walls share their block's name from 1.21.2; before that they need their own.
for stone in ["bluestone", "purplestone", "greenstone"]:
    LANG[f"block.bluestone.{stone}_wall_torch"] = LANG[f"block.bluestone.{stone}_torch"].replace(" Torch", " Wall Torch")
for rail in FLOOR_RAILS:
    if f"block.bluestone.{rail}" in LANG and rail.startswith("sticky"):
        LANG[f"block.bluestone.attached_{rail}"] = LANG[f"block.bluestone.{rail}"]
write(A + "lang/en_us.json", LANG)
# Vanilla names are renamed from the minecraft namespace, since the game merges namespaces in no fixed order.
write("assets/minecraft/lang/en_us.json", {
    "block.minecraft.powered_rail": "Redstone Powered Rail",
    "block.minecraft.detector_rail": "Redstone Detector Rail",
    "block.minecraft.activator_rail": "Redstone Activator Rail",
})


# Loot tables
def swap_ids(data, pairs):
    text = json.dumps(data)
    for old, new in pairs:
        text = text.replace(f'"minecraft:{old}"', f'"bluestone:{new}"').replace(f'"minecraft:blocks/{old}"', f'"bluestone:blocks/{new}"')
    return json.loads(text)


ID_PAIRS = [
    ("redstone_wire", "bluestone_wire"), ("redstone_torch", "bluestone_torch"), ("redstone_block", "bluestone_block"),
    ("deepslate_redstone_ore", "deepslate_bluestone_ore"), ("redstone_ore", "bluestone_ore"), ("repeater", "bluestone_repeater"),
    ("comparator", "bluestone_comparator"), ("powered_rail", "bluestone_powered_rail"), ("activator_rail", "bluestone_activator_rail"),
    ("detector_rail", "bluestone_detector_rail"), ("redstone", "bluestone"),
]
# Minecraft 26.3 changed the loot table and advancement formats; mc26.1/ is a pack overlay with the old ones for 26.1
# and 26.2.
for jar, prefix in FORMATS:
    for old, new in ID_PAIRS[:-1]:
        data = vanilla(f"data/minecraft/{LOOT}/blocks/{old}.json", jar)
        write(prefix + D + f"{LOOT}/blocks/{new}.json", swap_ids(data, ID_PAIRS))
    write(prefix + D + f"{LOOT}/blocks/purplestone_wire.json",
          swap_ids(vanilla(f"data/minecraft/{LOOT}/blocks/redstone_wire.json", jar), [("redstone_wire", "purplestone_wire"), ("redstone", "purplestone")]))
    # One paste for each face, as glow lichen drops one for each face, but without needing shears.
    paste_loot = vanilla(f"data/minecraft/{LOOT}/blocks/glow_lichen.json", jar)
    for entry in paste_loot["pools"][0]["entries"]:
        entry.pop("condition", None)
        entry.pop("conditions", None)
    paste_loot = json.loads(json.dumps(paste_loot).replace('"true"', '"face"').replace("minecraft:glow_lichen", "bluestone:greenstone_paste")
                            .replace("minecraft:blocks/glow_lichen", "bluestone:blocks/greenstone_paste"))
    write(prefix + D + f"{LOOT}/blocks/greenstone_paste.json", paste_loot)
    for ours, theirs in [("greenstone_torch", "redstone_torch"), ("greenstone_block", "redstone_block"), ("greenstone_repeater", "repeater"),
                         ("greenstone_comparator", "comparator"), ("converter_box", "repeater")] + [(rail, "rail") for rail in FLOOR_RAILS]:
        write(prefix + D + f"{LOOT}/blocks/{ours}.json", swap_ids(vanilla(f"data/minecraft/{LOOT}/blocks/{theirs}.json", jar), [(theirs, ours)]))
    write(prefix + D + f"{LOOT}/blocks/purplestone_block.json",
          swap_ids(vanilla(f"data/minecraft/{LOOT}/blocks/redstone_block.json", jar), [("redstone_block", "purplestone_block")]))
    write(prefix + D + f"{LOOT}/blocks/purplestone_torch.json",
          swap_ids(vanilla(f"data/minecraft/{LOOT}/blocks/redstone_torch.json", jar), [("redstone_torch", "purplestone_torch")]))
    write(prefix + D + f"{LOOT}/blocks/purplestone_converter.json",
          swap_ids(vanilla(f"data/minecraft/{LOOT}/blocks/repeater.json", jar), [("repeater", "purplestone_converter")]))

# Recipes and their recipe-book advancements
RECIPES = {
    "bluestone": ("redstone", "redstone"),
    "bluestone_block": ("redstone_block", "redstone"),
    "bluestone_torch": ("redstone_torch", "redstone"),
    "purplestone_torch": ("redstone_torch", "redstone"),
    "purplestone_block": ("redstone_block", "redstone"),
    "purplestone_from_purplestone_block": ("redstone", "redstone"),
    "greenstone_torch": ("redstone_torch", "redstone"),
    "greenstone_block": ("redstone_block", "redstone"),
    "greenstone_paste_from_greenstone_block": ("redstone", "redstone"),
    "greenstone_repeater": ("repeater", "redstone"),
    "greenstone_comparator": ("comparator", "redstone"),
    "bluestone_repeater": ("repeater", "redstone"),
    "bluestone_comparator": ("comparator", "redstone"),
    "bluestone_powered_rail": ("powered_rail", "transportation"),
    "bluestone_activator_rail": ("activator_rail", "transportation"),
    "bluestone_detector_rail": ("detector_rail", "transportation"),
    "bluestone_from_smelting_bluestone_ore": ("redstone_from_smelting_redstone_ore", "redstone"),
    "bluestone_from_smelting_deepslate_bluestone_ore": ("redstone_from_smelting_deepslate_redstone_ore", "redstone"),
    "bluestone_from_blasting_bluestone_ore": ("redstone_from_blasting_redstone_ore", "redstone"),
    "bluestone_from_blasting_deepslate_bluestone_ore": ("redstone_from_blasting_deepslate_redstone_ore", "redstone"),
}
# The purplestone torch and block are the redstone recipes with purplestone dust.
PURPLESTONE_PAIRS = [("redstone_torch", "purplestone_torch"), ("redstone_block", "purplestone_block"), ("redstone", "purplestone")]
# Greenstone paste stands in for redstone dust, and the greenstone parts for the redstone ones.
GREENSTONE_PAIRS = [("redstone_torch", "greenstone_torch"), ("redstone_block", "greenstone_block"), ("repeater", "greenstone_repeater"),
                    ("comparator", "greenstone_comparator"), ("redstone", "greenstone_paste")]
for ours, (theirs, folder) in RECIPES.items():
    stone, pairs = (("purplestone", PURPLESTONE_PAIRS) if ours.startswith("purplestone")
                    else ("greenstone", GREENSTONE_PAIRS) if ours.startswith("greenstone") else ("bluestone", ID_PAIRS))
    recipe = swap_ids(vanilla(f"data/minecraft/{RECIPE}/{theirs}.json"), pairs)
    if recipe.get("group") == "redstone":
        recipe["group"] = "bluestone"
    write(D + f"{RECIPE}/{ours}.json", recipe)
    for jar, prefix in FORMATS:
        advancement = swap_ids(vanilla(f"data/minecraft/{ADVANCEMENT}/recipes/{folder}/{theirs}.json", jar), pairs)
        unlock = advancement["criteria"]["has_the_recipe"]["conditions"]
        unlock[next(iter(unlock))] = f"bluestone:{ours}"
        advancement["rewards"]["recipes"] = [f"bluestone:{ours}"]
        text = re.sub(r'"has_[a-z_]+"', lambda m: m.group(0).replace("redstone", stone), json.dumps(advancement))
        write(prefix + D + f"{ADVANCEMENT}/recipes/{folder}/{ours}.json", json.loads(text))

# Ingredients are plain ids from 1.21.2 and {"item": id} before; the result's id was called "item" before 1.20.5.
def ingredient(item):
    return item if since("1.21.2") else {"item": item}


def result(item, count=1):
    return ({"count": count} if count != 1 else {}) | {"id" if since("1.20.5") else "item": item}


def shapeless(name, ingredients, item, count, group=None):
    recipe = {"type": "minecraft:crafting_shapeless", "category": "redstone"} | ({"group": group} if group else {})
    write(D + f"{RECIPE}/{name}.json", recipe | {"ingredients": [ingredient(i) for i in ingredients], "result": result(item, count)})


def shaped(name, category, key, pattern, item, count=1, group=None):
    recipe = {"type": "minecraft:crafting_shaped", "category": category} | ({"group": group} if group else {})
    write(D + f"{RECIPE}/{name}.json", recipe | {"key": {k: ingredient(v) for k, v in key.items()}, "pattern": pattern, "result": result(item, count)})


shapeless("purplestone", ["minecraft:redstone", "bluestone:bluestone"], "bluestone:purplestone", 2)
shapeless("greenstone_paste", ["bluestone:purplestone", "minecraft:slime_ball"], "bluestone:greenstone_paste", 2)
shapeless("greenstone_paste_from_redstone_and_bluestone", ["minecraft:redstone", "bluestone:bluestone", "minecraft:slime_ball", "minecraft:slime_ball"],
          "bluestone:greenstone_paste", 4, group="greenstone_paste")
# The converter box: cobblestone in the corners, quartz in the middle and one of each dust around it, in any order.
DUSTS = ["minecraft:redstone", "bluestone:bluestone", "bluestone:purplestone", "bluestone:greenstone_paste"]
BOX_RECIPES = []
for top, left, right, bottom in itertools.permutations(range(4)):
    if left > right:
        continue
    name = "converter_box" if not BOX_RECIPES else f"converter_box_{len(BOX_RECIPES) + 1}"
    shaped(name, "redstone", {"C": "minecraft:cobblestone", "Q": "minecraft:quartz", "T": DUSTS[top], "L": DUSTS[left], "R": DUSTS[right], "B": DUSTS[bottom]},
           ["CTC", "LQR", "CBC"], "bluestone:converter_box", group="converter_box")
    BOX_RECIPES.append(name)
# Chain rails: like the powered rail, with iron, an iron chain and dust of their colour. The chain was called "chain"
# before 1.21.9.
CHAIN = "minecraft:iron_chain" if since("1.21.9") else "minecraft:chain"
for rail, dust in [("chain_rail", "minecraft:redstone"), ("bluestone_chain_rail", "bluestone:bluestone")]:
    shaped(rail, "misc", {"I": "minecraft:iron_ingot", "C": CHAIN, "R": dust}, ["I I", "ICI", "IRI"], f"bluestone:{rail}", 6)
STICKY_FROM = {"sticky_rail": "minecraft:rail", "sticky_powered_rail": "minecraft:powered_rail", "sticky_bluestone_powered_rail": "bluestone:bluestone_powered_rail",
               "sticky_detector_rail": "minecraft:detector_rail", "sticky_bluestone_detector_rail": "bluestone:bluestone_detector_rail",
               "sticky_activator_rail": "minecraft:activator_rail", "sticky_bluestone_activator_rail": "bluestone:bluestone_activator_rail",
               "sticky_chain_rail": "bluestone:chain_rail", "sticky_bluestone_chain_rail": "bluestone:bluestone_chain_rail"}
for rail, made_from in STICKY_FROM.items():
    shaped(rail, "misc", {"R": made_from, "#": "minecraft:slime_ball"}, ["RRR", "R#R", "RRR"], f"bluestone:{rail}", 8)
shaped("purplestone_converter", "redstone", {"R": "minecraft:redstone_torch", "#": "bluestone:purplestone", "B": "bluestone:bluestone_torch", "I": "minecraft:stone"},
       ["R#B", "III"], "bluestone:purplestone_converter")
for jar, prefix in FORMATS:
    template = vanilla(f"data/minecraft/{ADVANCEMENT}/recipes/redstone/repeater.json", jar)
    recipe_key = next(iter(template["criteria"]["has_the_recipe"]["conditions"]))
    box_unlocks = [(name, "greenstone_paste") for name in BOX_RECIPES]
    for recipe, unlocked_by in [("purplestone", "bluestone"), ("purplestone_converter", "purplestone"), ("greenstone_paste", "purplestone"),
                                ("greenstone_paste_from_redstone_and_bluestone", "bluestone"),
                                ("chain_rail", "minecraft:rail"), ("bluestone_chain_rail", "minecraft:rail")] + box_unlocks + list(STICKY_FROM.items()):
        item = unlocked_by if ":" in unlocked_by else "bluestone:" + unlocked_by
        has = "has_" + item.split(":")[1]
        write(prefix + D + f"{ADVANCEMENT}/recipes/redstone/" + recipe + ".json", {
            "parent": "minecraft:recipes/root",
            "criteria": {
                # An item predicate's items were a list before 1.20.5.
                has: {"conditions": {"items": [{"items": item if since("1.20.5") else [item]}]}, "trigger": "minecraft:inventory_changed"},
                "has_the_recipe": {"conditions": {recipe_key: "bluestone:" + recipe}, "trigger": "minecraft:recipe_unlocked"},
            },
            "requirements": [["has_the_recipe", has]],
            "rewards": {"recipes": ["bluestone:" + recipe]},
        })

# Clerics sell 2 bluestone dust instead of 2 redstone dust a quarter of the time. Trades are data from 26.1; before that
# the loaders add the trade in code.
if legacy:
    trade = vanilla("data/minecraft/villager_trade/cleric/1/emerald_redstone.json")
    trade["given_item_modifier"] = [{"type": "minecraft:set_item", "item": "bluestone:bluestone",
                                     "condition": {"type": "minecraft:random_chance", "chance": 0.25}}]
    write("data/minecraft/villager_trade/cleric/1/emerald_redstone.json", trade)
    trade = vanilla("data/minecraft/villager_trade/cleric/1/emerald_redstone.json", legacy)
    trade["given_item_modifiers"] = [{"function": "minecraft:set_item", "item": "bluestone:bluestone",
                                      "conditions": [{"condition": "minecraft:random_chance", "chance": 0.25}]}]
    write("mc26.1/data/minecraft/villager_trade/cleric/1/emerald_redstone.json", trade)

    write("pack.mcmeta", {
        "pack": {"description": "Bluestone", "min_format": 84, "max_format": 121},
        # Resource formats 84-88 and data formats 101-107 are Minecraft 26.1 and 26.2.
        "overlays": {"entries": [
            {"min_format": 84, "max_format": 88, "directory": "mc26.1"},
            {"min_format": 101, "max_format": 107, "directory": "mc26.1"},
        ]},
    })

# Armor trims.
TRIMS = {"bluestone": "#071697", "purplestone": "#5A1697", "greenstone": "#169707"}
TRIM_ITEMS = {"bluestone": "bluestone:bluestone", "purplestone": "bluestone:purplestone", "greenstone": "bluestone:greenstone_paste"}
# Each version's trim material file is the game's redstone one with the mod's values: 26.3 names a palette, older
# versions an asset name, 1.21.4 and older the ingredient too, and 1.21.3 and older an item model index.
MODEL_INDEX = {"bluestone": 0.41, "purplestone": 0.42, "greenstone": 0.43}
for jar, prefix in FORMATS:
    redstone = vanilla("data/minecraft/trim_material/redstone.json", jar)
    # A range starting before 1.21.4 needs the item model index too; later versions ignore it.
    if "item_model_index" in vanilla("data/minecraft/trim_material/redstone.json", oldest):
        redstone["item_model_index"] = 0.4
    for material, color in TRIMS.items():
        ours = {"description": {"color": color, "translate": "trim_material.bluestone." + material}}
        if "asset_name" in redstone:
            ours = {"asset_name": material} | ours
        if "palette_id" in redstone:
            ours["palette_id"] = "bluestone:trim/" + material
        if "ingredient" in redstone:
            ours["ingredient"] = TRIM_ITEMS[material]
        if "item_model_index" in redstone:
            ours["item_model_index"] = MODEL_INDEX[material]
        write(prefix + D + "trim_material/" + material + ".json", ours)
write(f"data/minecraft/{ITEM_TAGS}/trim_materials.json", {"values": [TRIM_ITEMS[material] for material in TRIMS]})

# The trim palettes go in every atlas that colours trims: the armor atlas, and the block atlas (items atlas from 1.21.11)
# for inventory icons.
for jar, prefix in FORMATS:
    for name in sorted(n for n in jar.namelist() if n.startswith("assets/minecraft/atlases/") and n.endswith(".json")):
        for source in json.loads(jar.read(name))["sources"]:
            # Older versions leave the namespace off the source type.
            if source["type"].removeprefix("minecraft:") == "paletted_permutations" and "amethyst" in source.get("permutations", {}):
                palettes = source["permutations"]["amethyst"].removeprefix("minecraft:").rsplit("/", 1)[0]
                write(prefix + name, {"sources": [{
                    "type": "minecraft:paletted_permutations", "palette_key": source["palette_key"],
                    "permutations": {material: f"bluestone:{palettes}/{material}" for material in TRIMS}, "textures": source["textures"]}]})

# Inventory icons of trimmed armor: one model per armor piece and material (from 1.21.4, whose item definitions pick them).
for name in sorted(n for n in z.namelist() if n.startswith("assets/minecraft/items/") and n.endswith(".json")):
    definition = json.loads(z.read(name))
    cases = definition["model"].get("cases", []) if definition["model"].get("type") == "minecraft:select" else []
    if not any(c.get("when") == "minecraft:redstone" for c in cases):
        continue
    armor = name.split("/")[-1][:-5]
    for material in TRIMS:
        model = vanilla(f"assets/minecraft/models/item/{armor}_redstone_trim.json")
        model["textures"] = {k: v.replace("_trim_redstone", "_trim_" + material) for k, v in model["textures"].items()}
        write(A + f"models/item/{armor}_{material}_trim.json", model)

# Before 1.21.4 an armor item's model picks the trimmed icon by the trim material's item model index ("trim_type"):
# the game's armor models get the mod's materials added in order, since the last matching override wins.
if not oldest_since("1.21.4"):
    for name in sorted(n for n in oldest.namelist() if n.startswith("assets/minecraft/models/item/") and n.endswith(".json")):
        model = json.loads(oldest.read(name))
        overrides = model.get("overrides", [])
        if not any(o.get("predicate", {}).get("trim_type") == 0.4 for o in overrides):
            continue
        armor = name.split("/")[-1][:-5]
        for material in TRIMS:
            trimmed = vanilla(f"assets/minecraft/models/item/{armor}_redstone_trim.json", oldest)
            trimmed["textures"] = {k: v.replace("_trim_redstone", "_trim_" + material) for k, v in trimmed["textures"].items()}
            write(A + f"models/item/{armor}_{material}_trim.json", trimmed)
            overrides.append({"model": f"bluestone:item/{armor}_{material}_trim", "predicate": {"trim_type": MODEL_INDEX[material]}})
        model["overrides"] = sorted(overrides, key=lambda o: o.get("predicate", {}).get("trim_type", 0))
        write(name, model)

# World generation: the redstone ore vein and heights, plus extra attempts in water biomes.
WORLDGEN_IDS = ID_PAIRS + [("ore_redstone", "ore_bluestone")]
# The ore's feature lives in worldgen/feature from 26.3 and worldgen/configured_feature before.
for jar, prefix in FORMATS:
    folder = "feature" if "data/minecraft/worldgen/feature/ore_redstone.json" in jar.namelist() else "configured_feature"
    write(prefix + D + f"worldgen/{folder}/ore_bluestone.json", swap_ids(vanilla(f"data/minecraft/worldgen/{folder}/ore_redstone.json", jar), WORLDGEN_IDS))
for theirs, ours in [("ore_redstone", "ore_bluestone"), ("ore_redstone_lower", "ore_bluestone_lower")]:
    placed = swap_ids(vanilla(f"data/minecraft/worldgen/placed_feature/{theirs}.json"), WORLDGEN_IDS)
    write(D + f"worldgen/placed_feature/{ours}.json", placed)
    extra = json.loads(json.dumps(placed))
    for modifier in extra["placement"]:
        if modifier["type"] == "minecraft:count":
            modifier["count"] *= 2
    write(D + f"worldgen/placed_feature/{ours}_extra.json", extra)
write(D + "tags/worldgen/biome/has_more_bluestone.json",
      {"values": ["#minecraft:is_ocean", "#minecraft:is_river", "minecraft:swamp", "minecraft:mangrove_swamp"]})

# Tags
write(f"data/minecraft/{BLOCK_TAGS}/mineable/pickaxe.json", {"values": ["bluestone:bluestone_block", "bluestone:purplestone_block", "bluestone:greenstone_block", "bluestone:converter_box", "bluestone:bluestone_ore", "bluestone:deepslate_bluestone_ore"]})
write(f"data/minecraft/{BLOCK_TAGS}/needs_iron_tool.json", {"values": ["bluestone:bluestone_ore", "bluestone:deepslate_bluestone_ore"]})
RAILS = {"values": ["bluestone:bluestone_powered_rail", "bluestone:bluestone_detector_rail", "bluestone:bluestone_activator_rail",
                   ] + ["bluestone:" + rail for rail in FLOOR_RAILS]}
write(f"data/minecraft/{BLOCK_TAGS}/rails.json", RAILS)
write(f"data/minecraft/{ITEM_TAGS}/rails.json", RAILS)
# Since 26.3 flowing water only breaks blocks in this tag; older versions break anything that doesn't block movement.
if legacy:
    write(f"data/minecraft/{BLOCK_TAGS}/washed_away_by_fluids.json",
          {"values": ["bluestone:purplestone_wire", "bluestone:purplestone_torch", "bluestone:purplestone_wall_torch", "bluestone:purplestone_converter",
                      "bluestone:greenstone_paste", "bluestone:greenstone_torch", "bluestone:greenstone_wall_torch", "bluestone:greenstone_repeater",
                      "bluestone:greenstone_comparator"]})
write(f"data/minecraft/{BLOCK_TAGS}/wall_post_override.json", {"values": ["bluestone:bluestone_torch", "bluestone:purplestone_torch", "bluestone:greenstone_torch"]})
ORES = {"values": ["bluestone:bluestone_ore", "bluestone:deepslate_bluestone_ore"]}
write(D + f"{BLOCK_TAGS}/bluestone_ores.json", ORES)
write(D + f"{ITEM_TAGS}/bluestone_ores.json", ORES)
write(f"data/minecraft/{BLOCK_TAGS}/ores.json", {"values": ["#bluestone:bluestone_ores"]})
write(f"data/minecraft/{ITEM_TAGS}/ores.json", {"values": ["#bluestone:bluestone_ores"]})


def full_model(model_id, programmer=False):
    """A model with its parents' elements and textures folded in, from the files written above or the game's."""
    namespace, _, path = model_id.rpartition(":")
    if namespace == "bluestone":
        file = OUT / ("programmer_art/" + A if programmer else A) / "models" / f"{path}.json"
        model = json.loads((file if file.exists() else OUT / A / "models" / f"{path}.json").read_text(encoding="utf-8"))
    else:
        location = f"assets/minecraft/models/{path}.json"
        model = programmer_art(location) if programmer else vanilla(location)
    if "parent" not in model:
        return model
    parent_id = model["parent"] if ":" in model["parent"] else "minecraft:" + model["parent"]
    parent = full_model(parent_id, programmer)
    merged = {**parent, **{k: v for k, v in model.items() if k != "parent"}}
    merged["textures"] = {**parent.get("textures", {}), **model.get("textures", {})}
    return merged


# Before 1.21.11 a blockstate can't turn a model about z: those variants get a model turned beforehand (and a
# Programmer Art one where that pack's models differ).
if not since("1.21.11"):
    import model_rotation
    turned = {}

    def unturned(variant):
        if not variant.get("z"):
            return variant
        key = (variant["model"], variant.get("x", 0), variant.get("y", 0), variant["z"])
        if key not in turned:
            name = key[0].rpartition(":")[2].removeprefix("block/").replace("/", "_") + "_x%d_y%d_z%d" % key[1:]
            model = model_rotation.turn(full_model(key[0]), *key[1:])
            write(A + f"models/block/turned/{name}.json", model)
            programmer = model_rotation.turn(full_model(key[0], True), *key[1:])
            if programmer != model:
                write(f"programmer_art/{A}models/block/turned/{name}.json", programmer)
            turned[key] = f"bluestone:block/turned/{name}"
        return {"model": turned[key], **{k: v for k, v in variant.items() if k not in ("model", "x", "y", "z")}}

    def unturned_all(value):
        return [unturned(v) for v in value] if isinstance(value, list) else unturned(value)

    for path in sorted((OUT / A / "blockstates").glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for key in data.get("variants", {}):
            data["variants"][key] = unturned_all(data["variants"][key])
        for part in data.get("multipart", []):
            part["apply"] = unturned_all(part["apply"])
        write(A + f"blockstates/{path.name}", data)
print("done")
