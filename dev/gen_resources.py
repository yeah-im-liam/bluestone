"""Writes Bluestone's assets and data, copying the matching vanilla files with redstone swapped for bluestone.

python dev/gen_resources.py <26.3 client jar> common/src/main/resources <art folder> <26.1 client jar> <unzipped 26.3 Programmer Art pack>

The client jars are in the Loom cache (fabric-loom/<version>/minecraft-client.jar); the Programmer Art pack is
minecraft/resourcepacks/programmer_art.zip in the 26.3 asset index. The art folder holds default/ and programmer_art/
with the same layout as the game's textures folder.
"""
import json
import re
import shutil
import sys
import zipfile
from pathlib import Path

JAR = sys.argv[1]
LEGACY_JAR = sys.argv[4]
PROGRAMMER_ART = Path(sys.argv[5])  # the game's Programmer Art pack, unzipped  # 26.1, whose loot table and advancement format 26.1 and 26.2 share
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
for name, src in [("bluestone_block", "redstone_block"), ("bluestone_ore", "redstone_ore"), ("deepslate_bluestone_ore", "deepslate_redstone_ore")]:
    write(A + f"blockstates/{name}.json", {"variants": {"": {"model": f"bluestone:block/{name}"}}})
for name, src, prefix in [
    ("bluestone_torch", "redstone_torch", "redstone_torch"),
    ("bluestone_wall_torch", "redstone_wall_torch", "redstone_wall_torch"),
    ("bluestone_repeater", "repeater", "repeater"),
    ("bluestone_comparator", "comparator", "comparator"),
    ("bluestone_powered_rail", "powered_rail", "powered_rail"),
    ("bluestone_activator_rail", "activator_rail", "activator_rail"),
    ("bluestone_detector_rail", "detector_rail", "detector_rail"),
]:
    data = vanilla(f"assets/minecraft/blockstates/{src}.json")
    write(A + f"blockstates/{name}.json", swap_models(data, [("minecraft:block/" + prefix, "bluestone:block/" + name)]))

# Block models
for name in ["bluestone_block", "bluestone_ore", "deepslate_bluestone_ore"]:
    write(A + f"models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"bluestone:block/{name}"}})

write(A + "models/block/bluestone_torch.json", {"parent": "minecraft:block/template_redstone_torch", "textures": {"torch": "bluestone:block/bluestone_torch"}})
write(A + "models/block/bluestone_torch_off.json", {"parent": "minecraft:block/template_torch_unlit", "textures": {"torch": "bluestone:block/bluestone_torch_off"}})
write(A + "models/block/bluestone_wall_torch.json", {"parent": "minecraft:block/template_redstone_torch_wall", "textures": {"torch": "bluestone:block/bluestone_torch"}})
write(A + "models/block/bluestone_wall_torch_off.json", {"parent": "minecraft:block/template_torch_wall_unlit", "textures": {"torch": "bluestone:block/bluestone_torch_off"}})

TORCH_TEXTURES = {"unlit": "bluestone:block/bluestone_torch_off", "lit": "bluestone:block/bluestone_torch"}


def diode_model(vanilla_name, ours, top):
    parent = vanilla(f"assets/minecraft/models/block/{vanilla_name}.json")
    textures = {"particle": top, "top": top}
    for key, value in TORCH_TEXTURES.items():
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


# The converter: a one-tick repeater whose input-end torch shows the input colour and output-end torch the output colour.
TORCHES = {"red": ("minecraft:block/redstone_torch", "minecraft:block/redstone_torch_off"),
           "blue": ("bluestone:block/bluestone_torch", "bluestone:block/bluestone_torch_off")}
MODES = {"red_to_blue": ("red", "blue"), "blue_to_red": ("blue", "red")}


def torch_end(element):
    """Which torch an element of the repeater_1tick models belongs to: the output torch sits at z 2-4, the input one at z 6-8."""
    center = [(a + b) / 2 for a, b in zip(element["from"], element["to"])]
    for end, z in (("out", 3), ("in", 7)):
        offset = sorted(abs(c - h) for c, h in zip(center, (8, 6, z)))
        if offset == [0, 0, 3]:
            return end  # one of the glow boxes around that torch's head
    return "out" if abs(center[2] - 3) < abs(center[2] - 7) else "in"


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
                model = converter_model(source("repeater_1tick" + on + locked))
                top = "bluestone:block/converter_" + mode + on
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

# Item models and item definitions
GENERATED = {
    "bluestone": "bluestone:item/bluestone",
    "purplestone": "bluestone:item/purplestone",
    "converter": "bluestone:item/converter",
    "bluestone_torch": "bluestone:block/bluestone_torch",
    "bluestone_repeater": "bluestone:item/bluestone_repeater",
    "bluestone_comparator": "bluestone:item/bluestone_comparator",
    "bluestone_powered_rail": "bluestone:block/bluestone_powered_rail",
    "bluestone_activator_rail": "bluestone:block/bluestone_activator_rail",
    "bluestone_detector_rail": "bluestone:block/bluestone_detector_rail",
}
for name, texture in GENERATED.items():
    write(A + f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": texture}})
    write(A + f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"bluestone:item/{name}"}})
for name in ["bluestone_block", "bluestone_ore", "deepslate_bluestone_ore"]:
    write(A + f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"bluestone:block/{name}"}})

# Textures: the user's art, and the Programmer Art versions in the folder Fabric reads (NeoForge serves it as a pack).
for src_dir, dst_dir in [(ART / "default", OUT / "assets/bluestone/textures"), (ART / "programmer_art", OUT / "programmer_art/assets/bluestone/textures")]:
    for png in sorted(src_dir.rglob("*.png")):
        rel = png.relative_to(src_dir)
        if rel.parts[0] == "palettes":
            if dst_dir.parts[-4] == "programmer_art":
                continue
            # 26.3 reads trim palettes from palettes/, 26.1 and 26.2 from trims/color_palettes/.
            for palette_dir in ["palettes/trim", "trims/color_palettes"]:
                dst = dst_dir / palette_dir / png.name
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(png, dst)
            continue
        dst = dst_dir / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(png, dst)
# Programmer Art puts the redstone torch on the old plain torch shape; the bluestone torch follows it.
for theirs in ["redstone_torch", "redstone_torch_off", "redstone_wall_torch", "redstone_wall_torch_off"]:
    model = json.loads((PROGRAMMER_ART / f"assets/minecraft/models/block/{theirs}.json").read_text())
    model["textures"] = {k: v.replace("minecraft:block/redstone_torch", "bluestone:block/bluestone_torch") for k, v in model["textures"].items()}
    write(f"programmer_art/assets/bluestone/models/block/{theirs.replace('redstone', 'bluestone')}.json", model)
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
    write(prefix + D + "loot_table/blocks/converter.json",
          swap_ids(vanilla("data/minecraft/loot_table/blocks/repeater.json", jar), [("repeater", "converter")]))

# Recipes and their recipe-book advancements
RECIPES = {
    "bluestone": ("redstone", "redstone"),
    "bluestone_block": ("redstone_block", "redstone"),
    "bluestone_torch": ("redstone_torch", "redstone"),
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
for ours, (theirs, folder) in RECIPES.items():
    recipe = swap_ids(vanilla(f"data/minecraft/recipe/{theirs}.json"), ID_PAIRS)
    if recipe.get("group") == "redstone":
        recipe["group"] = "bluestone"
    write(D + f"recipe/{ours}.json", recipe)
    for jar, prefix in [(z, ""), (legacy, "mc26.1/")]:
        advancement = swap_ids(vanilla(f"data/minecraft/advancement/recipes/{folder}/{theirs}.json", jar), ID_PAIRS)
        text = json.dumps(advancement).replace(f'"minecraft:{theirs}"', f'"bluestone:{ours}"')
        text = re.sub(r'"has_[a-z_]+"', lambda m: m.group(0).replace("redstone", "bluestone"), text)
        write(prefix + D + f"advancement/recipes/{folder}/{ours}.json", json.loads(text))

write(D + "recipe/purplestone.json", {"type": "minecraft:crafting_shapeless", "category": "redstone",
                                     "ingredients": ["minecraft:redstone", "bluestone:bluestone"], "result": {"count": 2, "id": "bluestone:purplestone"}})
write(D + "recipe/converter.json", {"type": "minecraft:crafting_shaped", "category": "redstone",
                                   "key": {"R": "minecraft:redstone_torch", "#": "bluestone:purplestone", "B": "bluestone:bluestone_torch", "I": "minecraft:stone"},
                                   "pattern": ["R#B", "III"], "result": {"id": "bluestone:converter"}})
for jar, prefix in [(z, ""), (legacy, "mc26.1/")]:
    template = vanilla("data/minecraft/advancement/recipes/redstone/repeater.json", jar)
    recipe_key = next(iter(template["criteria"]["has_the_recipe"]["conditions"]))
    for recipe, unlocked_by in [("purplestone", "bluestone"), ("converter", "purplestone")]:
        write(prefix + D + "advancement/recipes/redstone/" + recipe + ".json", {
            "parent": "minecraft:recipes/root",
            "criteria": {
                "has_" + unlocked_by: {"conditions": {"items": [{"items": "bluestone:" + unlocked_by}]}, "trigger": "minecraft:inventory_changed"},
                "has_the_recipe": {"conditions": {recipe_key: "bluestone:" + recipe}, "trigger": "minecraft:recipe_unlocked"},
            },
            "requirements": [["has_the_recipe", "has_" + unlocked_by]],
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

# Inventory icons of trimmed armor: one model per armor piece and material, and a case for each in the armor item's model selector.
for name in sorted(n for n in z.namelist() if n.startswith("assets/minecraft/items/") and n.endswith(".json")):
    definition = json.loads(z.read(name))
    cases = definition["model"].get("cases", []) if definition["model"].get("type") == "minecraft:select" else []
    redstone = [c for c in cases if c.get("when") == "minecraft:redstone"]
    if not redstone:
        continue
    armor = name.split("/")[-1][:-5]
    position = cases.index(redstone[0]) + 1
    for material in TRIMS:
        model = vanilla(f"assets/minecraft/models/item/{armor}_redstone_trim.json")
        model["textures"] = {k: v.replace("_trim_redstone", "_trim_" + material) for k, v in model["textures"].items()}
        write(A + f"models/item/{armor}_{material}_trim.json", model)
        case = json.loads(json.dumps(redstone[0]).replace(f"minecraft:item/{armor}_redstone_trim", f"bluestone:item/{armor}_{material}_trim"))
        case["when"] = "bluestone:" + material
        cases.insert(position, case)
        position += 1
    write(f"assets/minecraft/items/{armor}.json", definition)

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
write("data/minecraft/tags/block/mineable/pickaxe.json", {"values": ["bluestone:bluestone_block", "bluestone:bluestone_ore", "bluestone:deepslate_bluestone_ore"]})
write("data/minecraft/tags/block/needs_iron_tool.json", {"values": ["bluestone:bluestone_ore", "bluestone:deepslate_bluestone_ore"]})
RAILS = {"values": ["bluestone:bluestone_powered_rail", "bluestone:bluestone_detector_rail", "bluestone:bluestone_activator_rail"]}
write("data/minecraft/tags/block/rails.json", RAILS)
write("data/minecraft/tags/item/rails.json", RAILS)
write("data/minecraft/tags/block/wall_post_override.json", {"values": ["bluestone:bluestone_torch"]})
ORES = {"values": ["bluestone:bluestone_ore", "bluestone:deepslate_bluestone_ore"]}
write(D + "tags/block/bluestone_ores.json", ORES)
write(D + "tags/item/bluestone_ores.json", ORES)
write("data/minecraft/tags/block/ores.json", {"values": ["#bluestone:bluestone_ores"]})
write("data/minecraft/tags/item/ores.json", {"values": ["#bluestone:bluestone_ores"]})
print("done")
