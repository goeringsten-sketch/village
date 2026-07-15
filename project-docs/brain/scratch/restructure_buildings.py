import re
import os

filepath = r"c:\Users\Sten\Desktop\village\resources\config\buildings.yml"

with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Update menu_categories block
menu_categories_target = """menu_categories:
  - Allgemeine Gebäude
  - Wohnen
  - Pfade & Wege
  - Produktion & Verarbeitung
  - Fabrik & Verarbeitung
  - Handel & Logistik
  - Wissenschaft & Forschung
  - Medizin & Apotheke
  - Zerstreuung & Kultur
  - Sicherheit & Verteidigung
  - Außenposten
  - Bezirke & Erweiterungen"""

content = re.sub(r"menu_categories:\n(  - .+\n?)+", menu_categories_target + "\n", content)

# 2. Extract categories
# Using '\ncategories:\n' to avoid matching 'menu_categories:'
parts = content.split("\ncategories:\n")
header_and_settings = parts[0] + "\ncategories:\n"
categories_content = parts[1]

cat_keys = ["general", "wohnen", "paths", "production", "factory", "trade", "research", "medicine", "recreation", "defense", "outposts", "district"]

blocks = {}
lines = categories_content.splitlines(keepends=True)

current_cat = None
current_block = []

for line in lines:
    m = re.match(r"^\s*([a-z_]+):\s*$", line)
    if m and m.group(1) in cat_keys:
        key = m.group(1)
        print(f"Found category key: '{key}'")
        if current_cat:
            blocks[current_cat] = "".join(current_block)
        current_cat = key
        current_block = [line]
    else:
        if current_cat:
            current_block.append(line)

if current_cat:
    blocks[current_cat] = "".join(current_block)

print(f"All keys found: {list(blocks.keys())}")

new_production_buildings = """
      steinbruch:
        name: "Steinbruch"
        description: "Ein offener Steinbruch für Bergarbeiter. Bietet Platz für Abbauarbeiten."
        permission: "village.building.production.steinbruch"
        icon: STONE
        requires_village_level: 2
        validation_mode: block_check
        required_blocks:
          STONE: 20
          COBBLESTONE: 20
        workstation_blocks: [STONE, COBBLESTONE]
        area:
          shape: rectangle
          min_size: "5x5"
          max_size: "20x20"
        villager_slots: 2
        build_cost:
          items: { IRON_PICKAXE: 2, COBBLESTONE: 64 }
          money: 100

      baeckerei:
        name: "Bäckerei"
        description: "Hier verarbeitet der Bäcker Weizen zu Brot, Keksen und Kuchen."
        permission: "village.building.production.baeckerei"
        icon: BREAD
        requires_village_level: 3
        validation_mode: schematic
        schematic: "baeckerei.schem"
        workstation_blocks: [SMOKER]
        additional_workstation_blocks:
          truhe:
            type: CHEST
            label: "Zutatenkiste"
            required: true
            count_min: 1
            count_max: 2
            public: true
            villager_accessible: true
        area:
          shape: rectangle
          min_size: "5x5"
          max_size: "10x10"
        villager_slots: 2
        build_cost:
          items: { SMOKER: 1, OAK_PLANKS: 64, BRICKS: 32 }
          money: 150
        recipes:
          brot_backen:
            inputs: { WHEAT: 3 }
            outputs: { BREAD: 2 }
            duration_seconds: 40
            required_villager_job: BAKER
            required_building_level: 1
          kuchen_backen:
            inputs: { WHEAT: 3, EGG: 1, MILK_BUCKET: 1, SUGAR: 2 }
            outputs: { CAKE: 1 }
            duration_seconds: 120
            required_villager_job: BAKER
            required_building_level: 1
"""

blocks["production"] = blocks["production"].rstrip() + "\n" + new_production_buildings + "\n"

ordered_categories = []
for key in cat_keys:
    if key in blocks:
        ordered_categories.append(blocks[key])

new_content = header_and_settings + "".join(ordered_categories)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(new_content)

print("Restructuring complete!")
