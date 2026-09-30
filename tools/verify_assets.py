"""Validate generated resources and, optionally, their exact inclusion in a release jar."""
from pathlib import Path
import argparse
import json
import re
import zipfile
import tomllib
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
MACHINES = ("slot_machine", "multiplier_machine", "scratch_table", "hilo_table",
            "roulette_table", "penguin_machine", "color_wheel_table", "shop_machine", "blind_box_machine")
CARDS = ("match", "dice", "fruit", "sevens", "gems", "bingo", "vault", "crown")


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, help="Check all source resources against this jar")
    args = parser.parse_args()
    files = [p for p in RES.rglob("*") if p.is_file()]
    documents = {p.relative_to(RES).as_posix(): json.loads(p.read_text(encoding="utf-8"))
                 for p in files if p.suffix == ".json"}

    def model_exists(reference):
        if reference.startswith("teamecon:"):
            require("assets/teamecon/models/" + reference.split(":", 1)[1] + ".json" in documents,
                    "Missing model: " + reference)

    for path, document in documents.items():
        if "/models/" in path:
            model_exists(document.get("parent", ""))
            textures = document.get("textures", {})
            for name, texture in textures.items():
                seen = set()
                while texture.startswith("#"):
                    alias = texture[1:]
                    require(alias not in seen and alias in textures, f"Bad texture alias in {path}: {alias}")
                    seen.add(alias)
                    texture = textures[alias]
                if texture.startswith("teamecon:"):
                    target = RES / ("assets/teamecon/textures/" + texture.split(":", 1)[1] + ".png")
                    require(target.is_file(), "Missing texture: " + str(target))
            for element in document.get("elements", []):
                require(all(-16 <= n <= 32 for n in element["from"] + element["to"]), "Invalid model extent: " + path)
                require(all(a < b for a, b in zip(element["from"], element["to"])), "Degenerate element: " + path)
                for face in element["faces"].values():
                    texture = face["texture"]
                    require(not texture.startswith("#") or texture[1:] in textures, "Unbound face texture in " + path)
        if "/blockstates/" in path:
            for variant in document["variants"].values():
                model_exists(variant["model"])

    for name in MACHINES:
        variants = documents[f"assets/teamecon/blockstates/{name}.json"]["variants"]
        expected = {f"facing={face},half={half}" for half in ("bottom", "top") for face in ("north", "south", "east", "west")}
        if name not in ("shop_machine","blind_box_machine"):
            expected={f"assembled={assembled},half={half},facing={face}" for assembled in ("true","false") for half in ("bottom","top") for face in ("north","south","east","west")}
        require(set(variants) == expected, "Missing facing/half variants: " + name)
        if name in ('shop_machine','blind_box_machine'):
            faces=[]
            for half,offset in [('bottom',0),('top',16)]:
                for element in documents[f'assets/teamecon/models/block/{name}_{half}.json']['elements']:
                    a=element['from'].copy();b=element['to'].copy();a[1]+=offset;b[1]+=offset
                    for face in element['faces']:
                        axis={'west':0,'east':0,'down':1,'up':1,'north':2,'south':2}[face]
                        plane=(b if face in ('east','up','south') else a)[axis]
                        uv=[i for i in range(3) if i!=axis]
                        faces.append((face,plane,[a[uv[0]],a[uv[1]],b[uv[0]],b[uv[1]]]))
            for i,(face,plane,a) in enumerate(faces):
                for other,depth,b in faces[i+1:]:
                    if face==other and abs(plane-depth)<1e-6:
                        overlap=min(a[2],b[2])-max(a[0],b[0])>1e-6 and min(a[3],b[3])-max(a[1],b[1])>1e-6
                        require(not overlap,f'Coplanar vending faces flicker: {name} {face} at {plane}')
        if name != "scratch_table":
            recipe = documents[f"data/teamecon/recipe/{name}.json"]
            require(recipe["result"] == {"id": "teamecon:" + name, "count": 1}, "Bad recipe result: " + name)
            require(set("".join(recipe["pattern"]).replace(" ", "")) == set(recipe["key"]), "Bad recipe ingredients: " + name)
        loot = documents[f"data/teamecon/loot_table/blocks/{name}.json"]
        require(len(loot["pools"]) == 1 and loot["pools"][0]["rolls"] == 1, "Machine must drop once: " + name)
        if True:
            require({"condition": "minecraft:block_state_property", "block": "teamecon:" + name,
                     "properties": {"half": "bottom"}} in loot["pools"][0]["conditions"], "Missing half condition: " + name)
        if name != "scratch_table":
            advancement = documents[f"data/teamecon/advancement/recipes/{name}.json"]
            require(advancement["rewards"]["recipes"] == ["teamecon:" + name], "Recipe unlock mismatch: " + name)
    require(len([p for p in documents if p.startswith("data/teamecon/recipe/")]) == 9, "Expected eight machines and one handbook recipe")
    for name in MACHINES:
        model=documents[f"assets/teamecon/models/item/{name}.json"]
        require(model['parent']=='minecraft:item/generated' and model['textures']['layer0']==f'teamecon:item/{name}', 'Missing inventory thumbnail: '+name)
        with Image.open(RES/f'assets/teamecon/textures/item/{name}.png') as im:
            require(im.size==(64,64) and im.getbbox() is not None, 'Empty or invalid thumbnail: '+name)
    require(documents['data/teamecon/recipe/guide_book.json']['ingredients']==[{'item':'minecraft:book'},{'item':'minecraft:iron_ingot'}], 'Wrong handbook ingredients')
    for wheel in ['roulette_wheel','prize_wheel']:
        with Image.open(RES/f'assets/teamecon/textures/gui/{wheel}.png') as image: require(image.size==(512,512), 'Low resolution wheel')
    book=documents['data/teamecon/patchouli_books/casino_guide/book.json']
    require(book['use_resource_pack'] and book['custom_book_item']=='teamecon:guide_book', 'Invalid Patchouli book definition')
    require(not book.get('i18n',False), 'Localized literal pages must not treat percentages as format arguments')
    for lang in ['zh_cn','en_us','zh_tw']:
        prefix=f'assets/teamecon/patchouli_books/casino_guide/{lang}/'
        entries={p:d for p,d in documents.items() if p.startswith(prefix+'entries/')}
        require(len(entries)==21, 'Incomplete handbook: '+lang)
        for p,entry in entries.items():
            require(prefix+'categories/'+entry['category'].split(':')[1]+'.json' in documents, 'Missing book category: '+p)
            require(entry['pages'] and all(page['type'] in ('patchouli:text','patchouli:image','patchouli:crafting') and page['text'] for page in entry['pages']), 'Empty book entry: '+p)
            for page in entry['pages']:
                if page['type'] == 'patchouli:crafting':
                    recipe = page['recipe'].removeprefix('teamecon:')
                    require('data/teamecon/recipe/'+recipe+'.json' in documents, 'Missing handbook recipe: '+p)
            require(any(page['type']=='patchouli:image' for page in entry['pages']), 'Chapter lacks an illustration: '+p)
            for page in entry['pages']:
                require(not re.search(r'返奖率|成功率|期望值|\bRTP\b|return.to.player|expected value|success chance|success rate',page['text'],re.I), 'Handbook exposes hidden statistics: '+p)
                for ref in page.get('images',[]):
                    require((RES/'assets'/ref.replace(':','/',1)).is_file(), 'Missing handbook illustration: '+ref)
    require("data/teamecon/recipe/terminal.json" not in documents, "Terminal must be obtained by points exchange")
    for old in ("recipes", "loot_tables", "advancements"):
        require(not list((RES / "data/teamecon" / old).rglob("*.json")), "Obsolete Minecraft data directory: " + old)

    english = documents["assets/teamecon/lang/en_us.json"]
    chinese = documents["assets/teamecon/lang/zh_cn.json"]
    require(english.keys() == chinese.keys(), "Chinese/English language keys do not match")
    traditional = documents['assets/teamecon/lang/zh_tw.json']
    require(english.keys() == traditional.keys(), 'Traditional Chinese keys do not match')
    require(book['version']=='1.0.0', 'Stale handbook version')
    for kind in CARDS:
        require(f"assets/teamecon/models/item/scratch_card_{kind}.json" in documents, "Missing ticket item model: " + kind)
        for key in (f"item.teamecon.scratch_card_{kind}", f"scratch.teamecon.rule.{kind}", f"scratch.teamecon.short.{kind}"):
            require(key in english, "Missing ticket translation: " + key)
    require("data/teamecon/recipe/scratch_table.json" not in documents, "Retired scratch machine still craftable")
    for key in english:
        placeholders = lambda s: re.findall(r"%(?:\d+\$)?[sd]", s)
        require(placeholders(english[key]) == placeholders(chinese[key]), "Translation placeholders differ: " + key)
        require(placeholders(english[key]) == placeholders(traditional[key]), 'Traditional placeholders differ: '+key)
        for translation in (english[key], chinese[key], traditional[key]):
            require('\ufffd' not in translation and '???' not in translation, 'Broken text encoding: '+key)
    for lang in ('zh_cn','zh_tw','en_us'):
        content='\n'.join(json.dumps(v,ensure_ascii=False) for k,v in documents.items()
                         if f'patchouli_books/casino_guide/{lang}/' in k)
        require(not re.search(r'1[–-]50|51[–-]100|1\.96|Visual QA|GameTest|待补充|测试专用|TODO',content,re.I), 'Stale or test-only guide text: '+lang)
        require('洁柔厨' in content, 'Missing exact author ID: '+lang)
    for key in re.findall(r'Component\.translatable\("([a-z_]+\.teamecon\.[^"]+)"\)',
                          '\n'.join(p.read_text(encoding='utf-8') for p in (ROOT/'src/main/java').rglob('*.java'))):
        require(key in english, 'Untranslated direct component key: '+key)
    for java in (ROOT / "src/main/java").rglob("*.java"):
        source = java.read_text(encoding="utf-8")
        for reference in re.findall(r'ResourceLocation\.fromNamespaceAndPath\(\s*"teamecon",\s*"(textures/[^"\n]+\.png)"', source):
            require((RES / "assets/teamecon" / reference).is_file(), "Missing Java-referenced asset: " + reference)

    images = [p for p in files if p.suffix == ".png"]
    for path in images:
        with Image.open(path) as image:
            image.verify()
    for name in ("casino", "shop"):
        with Image.open(RES / f"assets/teamecon/textures/gui/{name}.png") as image:
            require(image.size == (320, 240), "Wrong GUI dimensions: " + name)
    if args.jar:
        with zipfile.ZipFile(args.jar) as jar:
            names = set(jar.namelist())
            metadata=tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode('utf-8'))
            require(metadata['mods'][0]['authors']=='洁柔厨', 'Corrupt or missing author metadata')
            version = next(line.split('=', 1)[1].strip() for line in (ROOT/'gradle.properties').read_text(encoding='utf-8').splitlines() if line.startswith('mod_version='))
            require(metadata['mods'][0]['version']==version, 'Wrong mod version')
            require(metadata['mods'][0]['logoFile'] in names, 'Missing mod-list logo')
            require({'LICENSE','NOTICE.md','CREDITS.md'}<=names, 'Missing release notices')
            require(not any(p.startswith("com/evolt/teamecon/qa/") for p in names), "QA classes leaked into the release")
            require(not any(p.startswith("com/evolt/teamecon/gametest/") for p in names), "GameTest classes leaked into the release")
            require("data/teamecon/recipe/scratch_table.json" not in names, "Retired recipe leaked into jar")
            require("data/teamecon/recipe/terminal.json" not in names, "Craftable terminal leaked into jar")
            require("assets/teamecon/textures/gui/penguin.png" not in names, "Replaced penguin artwork leaked into jar")
            require("assets/teamecon/textures/gui/core_hop.png" not in names, "Unselected core artwork leaked into jar")
            expected = {p.relative_to(RES).as_posix() for p in files}
            extra = {n for n in names if not n.endswith('/') and n.startswith(('data/teamecon/','assets/teamecon/'))} - expected
            require(not extra, 'Obsolete resources leaked into jar: '+str(sorted(extra)))
            for path in files:
                name = path.relative_to(RES).as_posix()
                require(name in names and jar.read(name) == path.read_bytes(), "Jar has stale/missing resource: " + name)
    print(f"PASS: {len(documents)} JSON files, {len(images)} PNGs, {len(english)} keys in 3 languages, six casino machines, eight tickets, nine recipes, 21-entry trilingual Patchouli handbook"
          + (", exact jar resources and QA exclusion" if args.jar else ""))


if __name__ == "__main__":
    main()
