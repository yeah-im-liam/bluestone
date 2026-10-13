"""Writes Bluestone's assets and data, copying the matching vanilla files with redstone swapped for bluestone.

python dev/gen_resources.py <26.3 client jar> common/src/main/resources <art folder> <26.1 client jar> <unzipped 26.3 Programmer Art pack>

The client jars are in the Loom cache (fabric-loom/<version>/minecraft-client.jar); the Programmer Art pack is
minecraft/resourcepacks/programmer_art.zip in the 26.3 asset index. The art folder holds default/ and programmer art/,
with one folder per thing (bluestone torch, converter, ...). Each of those has block/, item/ or palettes/ folders that
say where a texture goes, with the mod's textures next to the redstone originals they were drawn from.
"""
import json
import re
import shutil
import sys
import zipfile
from pathlib import Path

JAR = sys.argv[1]
LEGACY_JAR = sys.argv[4]          # 26.1, whose loot table and advancement format 26.1 and 26.2 share
PROGRAMMER_ART = Path(sys.argv[5])  # the game's Programmer Art pack, unzipped
OUT = Path(sys.argv[2])          # common/src/main/resources
ART = Path(sys.argv[3])          # bluestone/textures (the user's art)
z = zipfile.ZipFile(JAR)
legacy = zipfile.ZipFile(LEGACY_JAR)


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

for stone in ["bluestone", "purplestone", "greenstone"]:
    write(A + f"models/block/{stone}_torch.json", {"parent": "minecraft:block/template_redstone_torch", "textures": {"torch": f"bluestone:block/{stone}_torch"}})
    write(A + f"models/block/{stone}_torch_off.json", {"parent": "minecraft:block/template_torch_unlit", "textures": {"torch": f"bluestone:block/{stone}_torch_off"}})
    write(A + f"models/block/{stone}_wall_torch.json", {"parent": "minecraft:block/template_redstone_torch_wall", "textures": {"torch": f"bluestone:block/{stone}_torch"}})
    write(A + f"models/block/{stone}_wall_torch_off.json", {"parent": "minecraft:block/template_torch_wall_unlit", "textures": {"torch": f"bluestone:block/{stone}_torch_off"}})

TORCH_TEXTURES = {"unlit": "bluestone:block/bluestone_torch_off", "lit": "bluestone:block/bluestone_torch"}


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


# The converter: shaped like a four-tick repeater, with the input-end torch in the input colour and the output-end torch
# in the output colour. Its top is the same in both directions; only the torches swap.
TORCHES = {"red": ("minecraft:block/redstone_torch", "minecraft:block/redstone_torch_off"),
           "blue": ("bluestone:block/bluestone_torch", "bluestone:block/bluestone_torch_off")}
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
                top = "bluestone:block/converter" + on
                model["textures"] = {"particle": top, "slab": "minecraft:block/smooth_stone", "top": top, "lock": "minecraft:block/bedrock",
                                     "in_lit": TORCHES[input_color][0], "in_unlit": TORCHES[input_color][1],
                                     "out_lit": TORCHES[output_color][0], "out_unlit": TORCHES[output_color][1]}
                write(prefix + "models/block/converter_" + mode + on + locked + ".json", model)


converter_models(lambda name: vanilla("assets/minecraft/models/block/" + name + ".json"), A)
converter_models(lambda name: json.loads((PROGRAMMER_ART / ("assets/minecraft/models/block/" + name + ".json")).read_text()), "programmer_art/" + A)
ROTATION = {"south": 0, "west": 90, "north": 180, "east": 270}
variants = {}
for facing, y in ROTATION.items():
    for mode in MODES:
        for powered in [False, True]:
            for locked in [False, True]:
                variant = {"model": "bluestone:block/converter_" + mode + ("_on" if powered else "") + ("_locked" if locked else "")}
                if y:
                    variant["y"] = y
                key = "facing=%s,locked=%s,mode=%s,powered=%s" % (facing, str(locked).lower(), mode, str(powered).lower())
                variants[key] = variant
write(A + "blockstates/converter.json", {"variants": variants})

# Greenstone. Paste is a flat layer on each face it covers, tinted by power like dust.
paste_model = vanilla("assets/minecraft/models/block/resin_clump.json")
paste_model["textures"] = {"particle": "bluestone:block/greenstone_paste", "texture": "bluestone:block/greenstone_paste"}
for element in paste_model["elements"]:
    for face in element["faces"].values():
        face["tintindex"] = 0
write(A + "models/block/greenstone_paste.json", paste_model)
write(A + "blockstates/greenstone_paste.json", swap_models(vanilla("assets/minecraft/blockstates/resin_clump.json"),
                                                           [("minecraft:block/resin_clump", "bluestone:block/greenstone_paste")]))

# The greenstone repeater and comparator sit on any face (attached) and point any way along it (output). The game's
# models lie on the floor pointing north; these rotations, measured from the game's own block model rotations (the
# same in 26.1 and 26.3), turn the model's floor to the attached face and its north to the output.
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
GREEN_TORCHES = {"unlit": "bluestone:block/greenstone_torch_off", "lit": "bluestone:block/greenstone_torch"}
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

# The wall converter: a plate on the wall behind it, which is south of it when it faces north.
plate = {face: {"texture": "#plate", "uv": uv} for face, uv in [
    ("north", [0, 0, 16, 16]), ("south", [0, 0, 16, 16]), ("east", [0, 0, 2, 16]), ("west", [14, 0, 16, 16]), ("up", [0, 14, 16, 16]), ("down", [0, 0, 16, 2])]}
plate["south"]["cullface"] = "south"
write(A + "models/block/wall_converter.json", {"parent": "minecraft:block/block",
                                               "textures": {"particle": "bluestone:block/wall_converter", "plate": "bluestone:block/wall_converter"},
                                               "elements": [{"from": [0, 0, 14], "to": [16, 16, 16], "faces": plate}]})
write(A + "blockstates/wall_converter.json", {"variants": {
    f"facing={facing}": ({"model": "bluestone:block/wall_converter", "y": y} if y else {"model": "bluestone:block/wall_converter"})
    for facing, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]}})

# Slime rails and chain gear rails. The slime rail on a floor is an ordinary rail with its own texture.
for name in ["rail", "rail_corner", "rail_raised_ne", "rail_raised_sw"]:
    model = vanilla(f"assets/minecraft/models/block/{name}.json")
    model["textures"] = {k: v.replace("minecraft:block/rail", "bluestone:block/slime_rail") for k, v in model["textures"].items()}
    write(A + f"models/block/slime_{name}.json", model)
write(A + "blockstates/slime_rail.json", swap_models(vanilla("assets/minecraft/blockstates/rail.json"), [("minecraft:block/rail", "bluestone:block/slime_rail")]))

# Chain gear rails have an arrow on their texture pointing the way they drive carts. On the game's rail models the
# texture's bottom edge is south; a "_reversed" model turns the texture round where the rotation can't (on slopes).
def flipped(model_name, textures, reversed_texture):
    model = vanilla(f"assets/minecraft/models/block/{model_name}.json")
    if reversed_texture:
        for element in model["elements"]:
            for face in element["faces"].values():
                face["rotation"] = (face.get("rotation", 0) + 180) % 360
    model["textures"] = dict(model.get("textures", {}), **textures)
    return model


TURN = {0: "south", 90: "west", 180: "north", 270: "east"}
for rail in ["chain_gear_rail", "slime_chain_gear_rail"]:
    texture = {"rail": f"bluestone:block/{rail}", "particle": f"bluestone:block/{rail}"}
    for kind, template in [("flat", "rail_flat"), ("raised_ne", "template_rail_raised_ne"), ("raised_sw", "template_rail_raised_sw")]:
        write(A + f"models/block/{rail}_{kind}.json", flipped(template, texture, False))
        write(A + f"models/block/{rail}_{kind}_reversed.json", flipped(template, texture, True))
    variants = {}
    for key, variant in vanilla("assets/minecraft/blockstates/powered_rail.json")["variants"].items():
        props = dict(part.split("=") for part in key.split(","))
        if props["powered"] != "false":
            continue
        shape = props["shape"]
        kind = "raised_ne" if variant["model"].endswith("raised_ne") else "raised_sw" if variant["model"].endswith("raised_sw") else "flat"
        y = variant.get("y", 0)
        for reversed_ in [False, True]:
            axis_z = shape in ("north_south", "ascending_north", "ascending_south")
            drive = ("north" if reversed_ else "south") if axis_z else ("west" if reversed_ else "east")
            name = f"bluestone:block/{rail}_{kind}" + ("" if TURN[y] == drive else "_reversed")
            out = {"model": name}
            if y:
                out["y"] = y
            variants[f"reversed={str(reversed_).lower()},shape={shape}"] = out
    # Chain gear rails are straight, so they never curve; the curved shapes get the flat model to keep the game quiet.
    for shape in ["south_east", "south_west", "north_west", "north_east"]:
        for reversed_ in ["false", "true"]:
            variants[f"reversed={reversed_},shape={shape}"] = {"model": f"bluestone:block/{rail}_flat"}
    write(A + f"blockstates/{rail}.json", {"variants": variants})

# On walls and ceilings: the floor model turned onto the face it sits on, its arrow along the way it travels.
for attached_rail, model in [("attached_slime_rail", "bluestone:block/slime_rail"), ("attached_slime_chain_gear_rail", "bluestone:block/slime_chain_gear_rail_flat")]:
    write(A + f"blockstates/{attached_rail}.json", {"variants": {
        f"attached={attached},travel={travel}": rotated(model, attached, {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}[travel])
        for (attached, travel) in ALL_PAIRS if attached != "down"}})

# Item models and item definitions
GENERATED = {
    "bluestone": "bluestone:item/bluestone",
    "purplestone": "bluestone:item/purplestone",
    "converter": "bluestone:item/converter",
    "bluestone_torch": "bluestone:block/bluestone_torch",
    "purplestone_torch": "bluestone:block/purplestone_torch",
    "greenstone_paste": "bluestone:item/greenstone_paste",
    "greenstone_torch": "bluestone:block/greenstone_torch",
    "greenstone_repeater": "bluestone:item/greenstone_repeater",
    "greenstone_comparator": "bluestone:item/greenstone_comparator",
    "wall_converter": "bluestone:block/wall_converter",
    "slime_rail": "bluestone:block/slime_rail",
    "chain_gear_rail": "bluestone:block/chain_gear_rail",
    "slime_chain_gear_rail": "bluestone:block/slime_chain_gear_rail",
    "bluestone_repeater": "bluestone:item/bluestone_repeater",
    "bluestone_comparator": "bluestone:item/bluestone_comparator",
    "bluestone_powered_rail": "bluestone:block/bluestone_powered_rail",
    "bluestone_activator_rail": "bluestone:block/bluestone_activator_rail",
    "bluestone_detector_rail": "bluestone:block/bluestone_detector_rail",
}
for name, texture in GENERATED.items():
    write(A + f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": texture}})
    write(A + f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"bluestone:item/{name}"}})
for name in ["bluestone_block", "purplestone_block", "greenstone_block", "bluestone_ore", "deepslate_bluestone_ore"]:
    write(A + f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"bluestone:block/{name}"}})

# Textures: the user's art, and the Programmer Art versions in the folder Fabric reads (NeoForge serves it as a pack).
# The redstone originals kept beside them for comparison are skipped.
OURS = re.compile(r"bluestone|purplestone|greenstone|converter|slime_rail|chain_gear")
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
# Programmer Art puts the redstone torch on the old plain torch shape; the other torches follow it.
for stone in ["bluestone", "purplestone", "greenstone"]:
    for theirs in ["redstone_torch", "redstone_torch_off", "redstone_wall_torch", "redstone_wall_torch_off"]:
        model = json.loads((PROGRAMMER_ART / f"assets/minecraft/models/block/{theirs}.json").read_text())
        model["textures"] = {k: v.replace("minecraft:block/redstone_torch", f"bluestone:block/{stone}_torch") for k, v in model["textures"].items()}
        write(f"programmer_art/assets/bluestone/models/block/{theirs.replace('redstone', stone)}.json", model)
write("programmer_art/pack.mcmeta", {"pack": {"description": {"translate": "pack.bluestone.programmer_art.description"}, "min_format": 84, "max_format": 97}})

# Language
write(A + "lang/en_us.json", {
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
    "itemGroup.bluestone.main": "Bluestone",
    "item.bluestone.purplestone": "Purplestone Dust",
    "block.bluestone.purplestone_wire": "Purplestone Wire",
    "block.bluestone.purplestone_torch": "Purplestone Torch",
    "block.bluestone.purplestone_block": "Block of Purplestone",
    "block.bluestone.greenstone_paste": "Greenstone Paste",
    "block.bluestone.greenstone_torch": "Greenstone Torch",
    "block.bluestone.greenstone_block": "Block of Greenstone",
    "block.bluestone.greenstone_repeater": "Greenstone Repeater",
    "block.bluestone.greenstone_comparator": "Greenstone Comparator",
    "block.bluestone.wall_converter": "Wall Converter",
    "block.bluestone.slime_rail": "Slime Rail",
    "block.bluestone.chain_gear_rail": "Chain Gear Rail",
    "block.bluestone.slime_chain_gear_rail": "Slime Chain Gear Rail",
    "block.bluestone.converter": "Converter",
    "trim_material.bluestone.bluestone": "Bluestone Material",
    "trim_material.bluestone.purplestone": "Purplestone Material",
    "pack.bluestone.programmer_art": "Bluestone Programmer Art",
    "pack.bluestone.programmer_art.description": "Programmer Art textures for Bluestone",
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
# Minecraft 26.3 changed the loot table and advancement formats; mc26.1/ is a pack overlay with the old ones for 26.1 and 26.2.
for jar, prefix in [(z, ""), (legacy, "mc26.1/")]:
    for old, new in ID_PAIRS[:-1]:
        data = vanilla(f"data/minecraft/loot_table/blocks/{old}.json", jar)
        write(prefix + D + f"loot_table/blocks/{new}.json", swap_ids(data, ID_PAIRS))
    write(prefix + D + "loot_table/blocks/purplestone_wire.json",
          swap_ids(vanilla("data/minecraft/loot_table/blocks/redstone_wire.json", jar), [("redstone_wire", "purplestone_wire"), ("redstone", "purplestone")]))
    write(prefix + D + "loot_table/blocks/greenstone_paste.json",
          swap_ids(vanilla("data/minecraft/loot_table/blocks/resin_clump.json", jar), [("resin_clump", "greenstone_paste")]))
    for ours, theirs in [("greenstone_torch", "redstone_torch"), ("greenstone_block", "redstone_block"), ("greenstone_repeater", "repeater"),
                         ("greenstone_comparator", "comparator"), ("wall_converter", "repeater"), ("slime_rail", "rail"),
                         ("chain_gear_rail", "rail"), ("slime_chain_gear_rail", "rail")]:
        write(prefix + D + f"loot_table/blocks/{ours}.json", swap_ids(vanilla(f"data/minecraft/loot_table/blocks/{theirs}.json", jar), [(theirs, ours)]))
    write(prefix + D + "loot_table/blocks/purplestone_block.json",
          swap_ids(vanilla("data/minecraft/loot_table/blocks/redstone_block.json", jar), [("redstone_block", "purplestone_block")]))
    write(prefix + D + "loot_table/blocks/purplestone_torch.json",
          swap_ids(vanilla("data/minecraft/loot_table/blocks/redstone_torch.json", jar), [("redstone_torch", "purplestone_torch")]))
    write(prefix + D + "loot_table/blocks/converter.json",
          swap_ids(vanilla("data/minecraft/loot_table/blocks/repeater.json", jar), [("repeater", "converter")]))

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
    recipe = swap_ids(vanilla(f"data/minecraft/recipe/{theirs}.json"), pairs)
    if recipe.get("group") == "redstone":
        recipe["group"] = "bluestone"
    write(D + f"recipe/{ours}.json", recipe)
    for jar, prefix in [(z, ""), (legacy, "mc26.1/")]:
        advancement = swap_ids(vanilla(f"data/minecraft/advancement/recipes/{folder}/{theirs}.json", jar), pairs)
        unlock = advancement["criteria"]["has_the_recipe"]["conditions"]
        unlock[next(iter(unlock))] = f"bluestone:{ours}"
        advancement["rewards"]["recipes"] = [f"bluestone:{ours}"]
        text = re.sub(r'"has_[a-z_]+"', lambda m: m.group(0).replace("redstone", stone), json.dumps(advancement))
        write(prefix + D + f"advancement/recipes/{folder}/{ours}.json", json.loads(text))

write(D + "recipe/purplestone.json", {"type": "minecraft:crafting_shapeless", "category": "redstone",
                                     "ingredients": ["minecraft:redstone", "bluestone:bluestone"], "result": {"count": 2, "id": "bluestone:purplestone"}})
write(D + "recipe/greenstone_paste.json", {"type": "minecraft:crafting_shapeless", "category": "redstone",
                                          "ingredients": ["bluestone:purplestone", "minecraft:slime_ball"], "result": {"count": 2, "id": "bluestone:greenstone_paste"}})
write(D + "recipe/greenstone_paste_from_redstone_and_bluestone.json", {
    "type": "minecraft:crafting_shapeless", "category": "redstone", "group": "greenstone_paste",
    "ingredients": ["minecraft:redstone", "bluestone:bluestone", "minecraft:slime_ball", "minecraft:slime_ball"],
    "result": {"count": 4, "id": "bluestone:greenstone_paste"}})
write(D + "recipe/wall_converter.json", {"type": "minecraft:crafting_shapeless", "category": "redstone",
                                        "ingredients": ["bluestone:purplestone", "bluestone:greenstone_paste", "minecraft:stone"],
                                        "result": {"id": "bluestone:wall_converter"}})
for rail, middle in [("slime_rail", "minecraft:slime_ball"), ("chain_gear_rail", "minecraft:iron_chain")]:
    write(D + f"recipe/{rail}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "key": {"R": "minecraft:rail", "#": middle},
                                     "pattern": ["RRR", "R#R", "RRR"], "result": {"count": 8, "id": f"bluestone:{rail}"}})
write(D + "recipe/slime_chain_gear_rail.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                               "key": {"R": "bluestone:chain_gear_rail", "#": "minecraft:slime_ball"},
                                               "pattern": ["RRR", "R#R", "RRR"], "result": {"count": 8, "id": "bluestone:slime_chain_gear_rail"}})
write(D + "recipe/converter.json", {"type": "minecraft:crafting_shaped", "category": "redstone",
                                   "key": {"R": "minecraft:redstone_torch", "#": "bluestone:purplestone", "B": "bluestone:bluestone_torch", "I": "minecraft:stone"},
                                   "pattern": ["R#B", "III"], "result": {"id": "bluestone:converter"}})
for jar, prefix in [(z, ""), (legacy, "mc26.1/")]:
    template = vanilla("data/minecraft/advancement/recipes/redstone/repeater.json", jar)
    recipe_key = next(iter(template["criteria"]["has_the_recipe"]["conditions"]))
    for recipe, unlocked_by in [("purplestone", "bluestone"), ("converter", "purplestone"), ("greenstone_paste", "purplestone"),
                                ("greenstone_paste_from_redstone_and_bluestone", "bluestone"), ("wall_converter", "greenstone_paste"),
                                ("slime_rail", "minecraft:rail"), ("chain_gear_rail", "minecraft:rail"), ("slime_chain_gear_rail", "chain_gear_rail")]:
        item = unlocked_by if ":" in unlocked_by else "bluestone:" + unlocked_by
        has = "has_" + item.split(":")[1]
        write(prefix + D + "advancement/recipes/redstone/" + recipe + ".json", {
            "parent": "minecraft:recipes/root",
            "criteria": {
                has: {"conditions": {"items": [{"items": item}]}, "trigger": "minecraft:inventory_changed"},
                "has_the_recipe": {"conditions": {recipe_key: "bluestone:" + recipe}, "trigger": "minecraft:recipe_unlocked"},
            },
            "requirements": [["has_the_recipe", has]],
            "rewards": {"recipes": ["bluestone:" + recipe]},
        })

# Clerics sell 2 bluestone dust instead of 2 redstone dust a quarter of the time.
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

# Armor trims. 26.3 builds trim textures from palette ids; 26.1 and 26.2 from asset names and the armor_trims atlas.
# Colours are redstone's #971607 run through the same mix as each dust: red and blue swapped, or 60% red plus full blue.
TRIMS = {"bluestone": "#071697", "purplestone": "#5A1697"}
for material, color in TRIMS.items():
    description = {"color": color, "translate": "trim_material.bluestone." + material}
    write(D + "trim_material/" + material + ".json", {"description": description, "palette_id": "bluestone:trim/" + material})
    write("mc26.1/" + D + "trim_material/" + material + ".json", {"asset_name": material, "description": description})
write("data/minecraft/tags/item/trim_materials.json", {"values": ["bluestone:" + material for material in TRIMS]})

items_atlas = vanilla("assets/minecraft/atlases/items.json")["sources"][1]
write("assets/minecraft/atlases/items.json", {"sources": [{
    "type": "minecraft:paletted_permutations", "palette_key": items_atlas["palette_key"],
    "permutations": {material: "bluestone:trim/" + material for material in TRIMS}, "textures": items_atlas["textures"]}]})
for atlas in ["items", "armor_trims"]:
    source = [x for x in vanilla(f"assets/minecraft/atlases/{atlas}.json", legacy)["sources"] if x["type"] == "minecraft:paletted_permutations"][0]
    write(f"mc26.1/assets/minecraft/atlases/{atlas}.json", {"sources": [{
        "type": "minecraft:paletted_permutations", "palette_key": source["palette_key"],
        "permutations": {material: "bluestone:trims/color_palettes/" + material for material in TRIMS}, "textures": source["textures"]}]})

# Inventory icons of trimmed armor: one model per armor piece and material. client/TrimItemModels adds the cases to the
# armor item files while the game loads them, so the vanilla item files are not replaced.
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

# World generation: the redstone ore vein and heights, plus extra attempts in water biomes.
WORLDGEN_IDS = ID_PAIRS + [("ore_redstone", "ore_bluestone")]
write(D + "worldgen/feature/ore_bluestone.json", swap_ids(vanilla("data/minecraft/worldgen/feature/ore_redstone.json"), WORLDGEN_IDS))
write("mc26.1/" + D + "worldgen/configured_feature/ore_bluestone.json",
      swap_ids(vanilla("data/minecraft/worldgen/configured_feature/ore_redstone.json", legacy), WORLDGEN_IDS))
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
write("data/minecraft/tags/block/mineable/pickaxe.json", {"values": ["bluestone:bluestone_block", "bluestone:purplestone_block", "bluestone:greenstone_block", "bluestone:bluestone_ore", "bluestone:deepslate_bluestone_ore"]})
write("data/minecraft/tags/block/needs_iron_tool.json", {"values": ["bluestone:bluestone_ore", "bluestone:deepslate_bluestone_ore"]})
RAILS = {"values": ["bluestone:bluestone_powered_rail", "bluestone:bluestone_detector_rail", "bluestone:bluestone_activator_rail",
                   "bluestone:slime_rail", "bluestone:chain_gear_rail", "bluestone:slime_chain_gear_rail"]}
write("data/minecraft/tags/block/rails.json", RAILS)
write("data/minecraft/tags/item/rails.json", RAILS)
# Since 26.3 flowing water only breaks blocks in this tag; 26.1 and 26.2 break anything that doesn't block movement.
# Purplestone and the converter, like redstone and the repeater, can't sit in water.
write("data/minecraft/tags/block/washed_away_by_fluids.json",
      {"values": ["bluestone:purplestone_wire", "bluestone:purplestone_torch", "bluestone:purplestone_wall_torch", "bluestone:converter",
                  "bluestone:greenstone_torch", "bluestone:greenstone_wall_torch", "bluestone:greenstone_repeater", "bluestone:greenstone_comparator",
                  "bluestone:wall_converter"]})
write("data/minecraft/tags/block/wall_post_override.json", {"values": ["bluestone:bluestone_torch", "bluestone:purplestone_torch", "bluestone:greenstone_torch"]})
ORES = {"values": ["bluestone:bluestone_ore", "bluestone:deepslate_bluestone_ore"]}
write(D + "tags/block/bluestone_ores.json", ORES)
write(D + "tags/item/bluestone_ores.json", ORES)
write("data/minecraft/tags/block/ores.json", {"values": ["#bluestone:bluestone_ores"]})
write("data/minecraft/tags/item/ores.json", {"values": ["#bluestone:bluestone_ores"]})
print("done")
