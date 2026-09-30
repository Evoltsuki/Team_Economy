"""Reproducible pixel textures, multi-element machines and Minecraft 1.21 data."""
from pathlib import Path
from copy import deepcopy
import json
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "src/main/resources"
ASSETS = BASE / "assets/teamecon"
MACHINES = {
    "slot_machine": ("777", (62, 35, 31), (228, 178, 70), ["IGI", "GRG", "IGI"],
                     {"I": "iron_ingot", "G": "gold_ingot", "R": "redstone_block"}),
    "multiplier_machine": ("x2", (40, 48, 42), (110, 216, 164), ["IRI", "RDR", "IRI"],
                           {"I": "iron_ingot", "R": "redstone_block", "D": "diamond"}),
    "scratch_table": ("WIN", (60, 43, 32), (229, 192, 112), ["PPP", "PRP", "PPP"],
                      {"P": "oak_planks", "R": "redstone"}),
    "hilo_table": ("100", (43, 43, 66), (177, 158, 228), ["III", "RQR", "III"],
                   {"I": "iron_ingot", "R": "redstone", "Q": "quartz_block"}),
    "roulette_table": ("SPIN", (48, 49, 34), (227, 177, 79), ["GIG", "RER", "III"],
                       {"G": "gold_ingot", "I": "iron_ingot", "R": "redstone", "E": "emerald"}),
    "penguin_machine": ("HOP", (34, 65, 83), (121, 222, 244), ["III", "BRB", "IGI"],
                        {"I": "iron_ingot", "B": "packed_ice", "R": "redstone_block", "G": "slime_ball"}),
    "color_wheel_table": ("COLOR", (36, 46, 66), (255, 208, 83), ["IGI", "LRD", "III"],
                          {"I": "iron_ingot", "G": "glass", "L": "lapis_lazuli", "R": "redstone_block", "D": "diamond"}),
    "shop_machine": ("SHOP", (41, 52, 46), (111, 219, 168), ["IGI", "GEG", "IGI"],
                     {"I": "iron_ingot", "G": "gold_ingot", "E": "emerald"}),
}
GOLD = (226, 181, 84, 255)
WHITE = (245, 240, 219, 255)
DARK = (17, 26, 25, 255)
FONT = ImageFont.load_default(size=9)

def write_json(relative, data):
    path = BASE / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

def save_image(relative, image):
    path = ASSETS / "textures" / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)

def textures(name, spec):
    title, body, accent = spec[:3]
    for base in (False, True):
        for face in ("front", "side", "top", "bottom"):
            img = Image.new("RGBA", (32, 32), body + (255,))
            d = ImageDraw.Draw(img)
            d.rectangle((1, 1, 30, 30), outline=tuple(min(255, c + 20) for c in body), width=1)
            d.line((2, 2, 29, 2), fill=accent, width=2)
            d.line((2, 29, 29, 29), fill=accent, width=2)
            for x in (3, 28):
                for y in (4, 27): d.rectangle((x, y, x+1, y+1), fill=GOLD)
            if base:
                d.rectangle((7, 6, 24, 25), fill=tuple(max(0, c-12) for c in body), outline=accent)
                for y in (10, 14, 18, 22): d.line((10, y, 21, y), fill=body)
            elif face == "front":
                d.rectangle((3, 3, 28, 9), fill=DARK)
                box = d.textbbox((0, 0), title, font=FONT)
                d.text(((32-(box[2]-box[0]))//2, 1), title, fill=accent, font=FONT)
                d.rectangle((4, 12, 27, 24), fill=DARK, outline=accent)
                if name == "slot_machine":
                    for x in (6, 14, 22):
                        d.rectangle((x, 14, x+4, 22), fill=(230, 222, 189), outline=(115, 83, 32))
                        d.rectangle((x+1, 17, x+3, 19), fill=(173, 62, 48))
                elif name == "roulette_table":
                    d.ellipse((7, 10, 25, 28), fill=(27, 62, 40), outline=GOLD, width=2)
                    for angle in range(0, 360, 45):
                        d.pieslice((10, 13, 22, 25), angle, angle+40, fill=(176, 54, 45) if angle%90==0 else (26, 29, 29))
                elif name == "penguin_machine":
                    d.rectangle((5, 13, 26, 23), fill=(21, 48, 65))
                    for x in [6,14,22]:d.rectangle((x,21,x+4,22),fill=(195,246,251))
                    d.rectangle((8,14,13,19),fill=(129,197,100));d.point((9,16),fill=(48,78,35));d.point((12,16),fill=(48,78,35))
                elif name == "shop_machine":
                    for x, c in ((8,(95,210,154)), (15,(225,179,78)), (22,(212,85,61))):
                        d.rectangle((x-1, 15, x+2, 20), fill=c)
                    d.line((5, 21, 26, 21), fill=accent)
                else:
                    for x in range(7, 26, 4): d.line((x, 16, x, 20), fill=accent)
                d.rectangle((12, 26, 20, 27), fill=DARK)
            elif face == "side":
                for y in (11, 15, 19, 23): d.line((8, y, 23, y), fill=DARK, width=2)
                d.line((4, 6, 4, 25), fill=accent)
            elif face == "top":
                d.rectangle((6, 6, 25, 25), fill=tuple(max(0,c-8) for c in body), outline=accent)
                d.rectangle((13, 13, 18, 18), fill=accent)
            save_image("block/" + name + ("_base" if base else "") + "_" + face + ".png", img)

def box(a, b, front="#front", side="#side", top="#top", uv=None):
    faces = {face: {"texture": front if face=="north" else top if face=="up" else "#bottom" if face=="down" else side}
             for face in ("north","south","east","west","up","down")}
    if uv is not None:
        for value in faces.values(): value["uv"] = uv
    return {"from": list(a), "to": list(b), "faces": faces}

def cube(a, b, texture="#side", uv=None):
    e = box(a,b,texture,texture,texture,uv)
    e["faces"]["down"]["texture"] = texture
    return e

def model(name, base=False):
    suffix = "_base" if base else ""
    tex = {face: f"teamecon:block/{name}{suffix}_{face}" for face in ("front","side","top","bottom")}
    tex["particle"] = tex["side"]
    if base:
        elements = [box((2,0,2),(14,2,14)),box((3,2,3),(13,14,13)),box((1,14,1),(15,16,15))]
    else:
        elements = [box((1,0,2),(15,16,15))]
        if name == "slot_machine":
            elements += [box((1,13,0),(15,16,2)),box((2,5,.5),(14,13,2)),
                         cube((2,2,-.5),(14,4,2),"#top"), cube((15,5,7),(16,8,10))]
        elif name == "multiplier_machine":
            elements += [box((2,6,.5),(14,13,2)),cube((0,1,1),(2,15,3),"#top"),
                         cube((14,1,1),(16,15,3),"#top"),box((3,14,0),(13,16,2))]
        elif name == "scratch_table":
            elements = [box((2,0,3),(14,8,14)),box((0,7,0),(16,9,16)),
                        box((2,9,10),(14,14,13)),box((2,4,0),(14,11,2))]
            card = cube((3,9,3),(13,9.6,9),"#front")
            elements.append(card)
        elif name == "hilo_table":
            elements += [box((2,5,0),(14,13,2)),cube((2,2,-.5),(7,4,2),"#top"),
                         cube((9,2,-.5),(14,4,2),"#front"),box((3,14,1),(13,16,2))]
        elif name == "roulette_table":
            elements = [box((1,0,2),(15,15,15)),box((0,0,0),(16,3,16)),
                        cube((1,3,0),(3,14,2),"#top"),cube((13,3,0),(15,14,2),"#top"),
                        cube((3,13,0),(13,15,2),"#top"),cube((3,3,0),(13,5,2),"#top")]
        elif name == "penguin_machine":
            elements += [cube((0,0,0),(2,15,3),"#top"),cube((14,0,0),(16,15,3),"#top"),
                         cube((2,13,0),(14,16,3),"#top"),cube((2,1,-.5),(14,4,2),"#top")]
        elif name == "shop_machine":
            elements += [box((2,5,.5),(14,13,2)),cube((3,1,-.5),(13,3,2),"#top"),
                         cube((14,4,0),(16,12,3),"#top")]
    return {"parent":"minecraft:block/block","textures":tex,"elements":elements}

def item_model(name):
    upper = model(name)
    if name != "shop_machine":
        lower = model(name, True)
        for e in lower["elements"]:
            for f in e["faces"].values(): f["texture"] = f["texture"].replace("#", "#base_")
        for key,value in lower["textures"].items():
            if key != "particle": upper["textures"]["base_"+key] = value
        for e in upper["elements"]:
            e["from"][1] += 16
            e["to"][1] += 16
        upper["elements"] = lower["elements"] + upper["elements"]
    tall = name != "shop_machine"
    upper["display"] = {
        "gui":{"rotation":[20,35,0],"translation":[0,-7 if tall else 0,0],"scale":[.53,.53,.53] if tall else [.75,.75,.75]},
        "ground":{"rotation":[0,0,0],"translation":[0,3,0],"scale":[.25,.25,.25]},
        "fixed":{"rotation":[0,180,0],"translation":[0,-5 if tall else 0,0],"scale":[.45,.45,.45]},
        "thirdperson_righthand":{"rotation":[75,45,0],"translation":[0,2.5,0],"scale":[.28,.28,.28]},
        "firstperson_righthand":{"rotation":[0,45,0],"translation":[0,-1,0],"scale":[.4,.4,.4]},
    }
    return upper

def machine_data(name, spec):
    variants = {}
    halves = (("bottom", name+"_base"),("top",name)) if name!="shop_machine" else (("",name),)
    for half, target in halves:
        for facing, rotation in (("north",0),("east",90),("south",180),("west",270)):
            key = (f"assembled=false,half={half}," if half else "") + f"facing={facing}"
            variants[key] = {"model":f"teamecon:block/{target}","y":rotation}
            if half:
                variants[f"assembled=true,half={half},facing={facing}"]={"model":f"teamecon:block/{name}_assembled"}
    write_json(f"assets/teamecon/blockstates/{name}.json", {"variants":variants})
    write_json(f"assets/teamecon/models/block/{name}.json", model(name))
    if name!="shop_machine": write_json(f"assets/teamecon/models/block/{name}_base.json", model(name,True))
    if name!="shop_machine": write_json(f"assets/teamecon/models/block/{name}_assembled.json", {
        "parent":"minecraft:block/block","textures":{"particle":f"teamecon:block/{name}_side"},"elements":[]})
    write_json(f"assets/teamecon/models/item/{name}.json", item_model(name))
    if name!="scratch_table": write_json(f"data/teamecon/recipe/{name}.json", {
        "type":"minecraft:crafting_shaped","category":"misc","pattern":spec[3],
        "key":{key:{"item":"minecraft:"+value} for key,value in spec[4].items()},
        "result":{"id":"teamecon:"+name,"count":1}})
    conditions = [{"condition":"minecraft:survives_explosion"}]
    if name!="shop_machine":
        conditions.append({"condition":"minecraft:block_state_property","block":"teamecon:"+name,"properties":{"half":"bottom"}})
    write_json(f"data/teamecon/loot_table/blocks/{name}.json", {
        "type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"teamecon:"+name}],"conditions":conditions}]})
    if name!="scratch_table": write_json(f"data/teamecon/advancement/recipes/{name}.json", {
        "parent":"minecraft:recipes/root","criteria":{"has_material":{"trigger":"minecraft:inventory_changed",
        "conditions":{"items":[{"items":"minecraft:"+next(iter(spec[4].values()))}]}}},
        "requirements":[["has_material"]],"rewards":{"recipes":["teamecon:"+name]}})

def sprites():
    wheel = Image.new("RGBA",(64,64),(0,0,0,0)); d=ImageDraw.Draw(wheel)
    order=[0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26]
    red={1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36}
    for i, number in enumerate(order):
        color=(34,140,83) if number==0 else (190,55,49) if number in red else (28,37,34)
        d.pieslice((2,2,61,61),i*360/37,(i+1)*360/37,fill=color,outline=(187,143,62))
    d.ellipse((1,1,62,62),outline=GOLD,width=2)
    d.ellipse((21,21,42,42),fill=(36,70,47),outline=GOLD,width=2)
    d.ellipse((29,29,34,34),fill=WHITE)
    save_image("gui/roulette_wheel.png",wheel)
    slime=Image.new("RGBA",(24,24),(0,0,0,0)); d=ImageDraw.Draw(slime)
    d.rectangle((2,3,21,22),fill=(150,210,114,190),outline=(178,228,144,230))
    d.rectangle((5,6,18,19),fill=(123,183,89,245))
    d.rectangle((5,9,8,12),fill=(49,76,34));d.rectangle((15,9,18,12),fill=(49,76,34))
    d.rectangle((11,15,13,17),fill=(49,76,34));d.line((4,5,18,5),fill=(204,242,176),width=1)
    d.rectangle((3,18,4,20),fill=(179,231,144));d.rectangle((19,6,20,8),fill=(179,231,144))
    save_image("gui/slime_hop.png",slime)
    for old_name in ("penguin.png","core_hop.png"):
        old=ASSETS/"textures/gui"/old_name
        if old.is_file():old.unlink()
    save_image("block/white.png",Image.new("RGBA",(16,16),(255,255,255,255)))

def gui(name):
    img=Image.new("RGBA",(320,240),(0,0,0,0)); d=ImageDraw.Draw(img)
    d.rounded_rectangle((0,0,319,239),radius=6,fill=(23,35,30),outline=GOLD,width=2)
    d.line((5,19,314,19),fill=(77,94,73))
    def panel(rect): d.rounded_rectangle(rect,radius=3,fill=(31,47,38),outline=(82,99,71))
    if name=="casino":
        panel((7,40,185,134)); panel((188,40,313,134))
        d.rectangle((190,114,209,134),fill=(13,23,20),outline=(151,129,76))
    else:
        panel((7,41,313,145))
    d.rectangle((7,137 if name=="casino" else 146,313,151 if name=="casino" else 158),fill=(20,31,27))
    panel((5,160,173,238)); panel((177,160,314,238))
    for row in range(3):
        for col in range(9):
            x,y=7+col*18,163+row*18
            d.rectangle((x,y,x+17,y+17),fill=(15,25,21),outline=(75,86,66))
    for col in range(9):
        x=7+col*18
        d.rectangle((x,221,x+17,238),fill=(15,25,21),outline=(129,109,63))
    save_image(f"gui/{name}.png",img)

def main():
    for name,spec in MACHINES.items():
        textures(name,spec); machine_data(name,spec)
    sprites(); gui("casino"); gui("shop")
    write_json("data/minecraft/tags/block/mineable/pickaxe.json",{"replace":False,"values":["teamecon:"+name for name in MACHINES]})
    # Only superseded files from this generator are removed; Minecraft 1.21 uses singular directories.
    for name in list(MACHINES)+["terminal"]:
        for old in (BASE/f"data/teamecon/recipes/{name}.json",BASE/f"data/teamecon/loot_tables/blocks/{name}.json"):
            if old.is_file() and old.resolve().is_relative_to(BASE.resolve()): old.unlink()
    preview=Image.new("RGBA",(640,400),(17,27,22,255)); d=ImageDraw.Draw(preview)
    for i,name in enumerate(MACHINES):
        image=Image.open(ASSETS/f"textures/block/{name}_front.png").resize((64,64),Image.Resampling.NEAREST)
        x=12+i*88
        preview.paste(image,(x,12)); d.text((x,81),MACHINES[name][0],font=FONT,fill=WHITE)
    preview.paste(Image.open(ASSETS/"textures/gui/casino.png"),(0,112))
    preview.paste(Image.open(ASSETS/"textures/gui/shop.png"),(320,112))
    preview.paste(Image.open(ASSETS/"textures/gui/roulette_wheel.png"),(18,360))
    (ROOT/"build").mkdir(exist_ok=True); preview.save(ROOT/"build/asset-preview.png")
    from gen_redesign_assets import main as redesign_assets
    redesign_assets()
    print(f"Generated {len(MACHINES)} machine registrations, 7 recipes, loot, GUI and animation sprites.")

if __name__=="__main__": main()
