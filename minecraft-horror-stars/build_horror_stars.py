"""Build a Forge 1.19.2 lowcode Horror Stars preview JAR, no dependencies.
NOTE: This is a 29-star preview; gameplay has not been tested.
"""
from pathlib import Path
import base64
import json
import struct
import zlib
import zipfile

GROUPS = """
cave_dweller|Cave Dweller|cave_dweller:cave_dweller
intruder|The Intruder|the_intruder:intruder,the_intruder:observer
hidebehind|Hidebehind|whisperwoods:hidebehind
zotzpyre|Zotzpyre|whisperwoods:zotzpyre
ghoul|Ghoul|graveyard:ghoul
revenant|Revenant|graveyard:revenant
reaper|Reaper|graveyard:reaper
nightmare|Nightmare|graveyard:nightmare
skeleton_creeper|Skeleton Creeper|graveyard:skeleton_creeper
acolyte|Acolyte|graveyard:acolyte
corrupted_pillager|Corrupted Pillager|graveyard:corrupted_pillager
corrupted_vindicator|Corrupted Vindicator|graveyard:corrupted_vindicator
scuddler|Scuddler|dangers_in_the_darkness:scuddler
daunt|Daunt|dangers_in_the_darkness:daunt
dracoflora|Dracoflora|dangers_in_the_darkness:dracoflora
maw_manta|Maw Manta|dangers_in_the_darkness:maw_manta
rock_tentacle|Rock Tentacle|dangers_in_the_darkness:rock_tentacle,dangers_in_the_darkness:rock_tentacle_phase_2
the_arm|The Arm|dangers_in_the_darkness:the_arm
fever_dream|Fever Dream|dangers_in_the_darkness:fever_dream,dangers_in_the_darkness:fever_dream_upset
hat_man|Hat Man|dangers_in_the_darkness:hat_man
hirschgeist|Hirschgeist|whisperwoods:hirschgeist
wraith|Wraith|graveyard:wraith
lich|Corrupted Champion (Lich)|graveyard:lich
monolith|Monolith|dangers_in_the_darkness:monolith
arborial_colossus|Arboreal Colossus|dangers_in_the_darkness:arborial_colossus_1,dangers_in_the_darkness:arborial_colossus_2
eruptor_lizard_momma|Mother Infernus|dangers_in_the_darkness:eruptor_lizard_momma
lumen_leviathan|Capsizer|dangers_in_the_darkness:lumen_leviathan
shy_guy|Shy Guy|shy_guy:shy_guy_rage,shy_guy:shy_guy_anger,shy_guy:shy_guy
eyes|Eyes in the Darkness|eyesinthedarkness:eyes
"""

# Lossless 32x32 silhouette and each mob's color palette from preview PNGs.
MASK = "eNq9kksSwyAMQ5Ew6f1vXBuHNsTETDfVIhN4Rv5AKb8JO84cIzdATQ2w47LlFZm9cSbHW8ZxHMYRWh5S3gM+Gk2dci4ujaOnInRlaqaTah3kt7C+NbjFgpw7E2muTmfsOS6YZLwaT2CVkaurU+mANNFqROhtWmnK8XRc/Lsw6KeV1NcjN2PPErnti3VN+4sFwGdCD40GuIyM8RUC08jCM78PlHeDUDDLX/QG3dEDeg=="
PALETTES = "eNp9V9tu4zoM/Jc+68GSJdnaXykWhjfxNsY6ceE4LdrF+fcz1IVy5E3QGghlRRyRwyHz9+XQfwzd8XOYpmF5+fH6Wgn/91O82kZYKawWypg7U9JbWRmhrTBtfC1bJaQ1QjYqrijZCFXhvcSK9iumwicnVI1V5U9pKyENPdZ/66d4GS/rcjuWYKTD6UKyNzY9GIVPTSNcE18bJbQS5C3shk+pNZ72IRJV4zyLp2Ekp/E4/BpO4+VYYKmBl/zH09n0WCgO8CMBMbzHwboiPAmMxIbaA3oYFotPFQ5qmwTme16/37+WocgRDpZCpXtn00NxUhgpOCHS4kRZiaZOGaoa4TNgq8dQGroenkgEQHk7zbfpHgfuQjGX8eBsehxGi9aJJr2VyJaPguQEtUiOsx7MQyA4hcglbZ2ALMPHcOkvaxGTlohq0q2zGXgrAaYWDWevNYG3DF5R5sBbAvSYtwBjnWcMg+nfS9bWUtSIQyqhbAYoTeVdqyptwKktoqA4LkRIS0kyj4kr/dWwUiUol/HttJ77kiwah7S5KrIZ0LgYhIq9K0VxkrXhWBEcpMDVj+EoHOPIahOc659hGtb50h2WYdjHyAlkNMeIzUAdpMoIywgqqlCiDitBQyKAlVY/pw7l2HKI+sM8fa3Djjm6uWNONIPIoK7o5r44YzxgKSDSFXMHvGmJO08SRkFWOuhiQHOYl+X2vg7H7n2cpv6tjJFpSGhtYmg2PTAKEIDkvBnjBTWRkwq9Diyr3BPNoW+B0k27h/UBERwP/TrvewRSxQTJZqw1JRBya7c9AuR2ZtMifAE26nmp4bqb5F0Pt+Pxn/2qEvW2XwUz9itf9FzloV9J4eRduyJ6PRPDUPK+kQQsx/5WChA6kUK8062zGUS5uqtC0mCUFIsjlXFI4JNUkQyioUjDDDouYPTvaV76eyzkCv8pKNkMlEZMIDmJ8FTsxBttNh2DeOML+hGY2vnmkMoCYM79Z3eGLhdY0AjB3EbnvhhNGd9aKuz0mtoB1a1zXOnKN0WlnuiytoEsLXerZT786dYBaA5TUe8IM8kNlxWboZEbChRz1Q8RxA+eiEgpfbVUT6JDGu87l0mA1tPQ9cu54IymVs3szGYspFa0m8ZNQZE1DRk8bJEMOfhp5ZNKsj5Lm1b+Gx106Y7oXSUe9GtUT1K6bAY8CBIuZFhfpA6yVzEeKjxVhXs/EkJqstZsh79TvxJzCg5Lqh4u7GwGLDS4GdKR5Bv5B1gubD+9eGF7UtjU6ysq0TZPf8v1cHobxmtR3k7gqyaNXGzFwNA6pSBFrlZB4Zzmmtps0bsVwIHzz6Uf11MxdlJ/xga+VjKDZwqBUX4qiQJoiMt56tpu0LuV4HcaD4VXRfkTKh2azSggKvYybj5Qf/9wR7/bo3crwfF5vszT7srQOFSk44mFzegciXY0Wqa5koYVzaPH3Q5d2MFtv/yal7GfOowD8/V6uxaKRQd59Q65ZDOHHDXaJnfOD7n5J9HmvS4Xgv+B+uu8dNP43S/H7jyfz6VoUtfOP3yyGUJAlEFTl8xHak2YM3PCtjv0biWm/XYeLt00fIz9etqVn/JX5gPZjD+9bKgjnRBAEb0U5Ca/2aF3KwHB9fTVvd2+irtDkGtRJ5XJZkw/5QJXrdXd4CNZUIo9ercSk/A1FHknbkLRuCezGRwbOgILdbOdY7DKlN/u0LsVcvvf/zn9jr0="

# Tiers approximate the creature size found in the bundled original mod JARs.
HEALTH = {
 'cave_dweller':45, 'intruder':50, 'hidebehind':50, 'zotzpyre':35,
 'ghoul':35,'revenant':35,'reaper':45,'nightmare':50,'skeleton_creeper':35,
 'acolyte':35,'corrupted_pillager':35,'corrupted_vindicator':35,
 'scuddler':35,'daunt':45,'dracoflora':50,'maw_manta':50,'rock_tentacle':65,
 'the_arm':65,'fever_dream':45,'hat_man':45,'hirschgeist':50,'wraith':35,'lich':50,
 'monolith':65,'arborial_colossus':65,'eruptor_lizard_momma':50,'lumen_leviathan':65,
 'shy_guy':45, 'eyes':35
}

MOD_TOML = """modLoader="lowcodefml"
loaderVersion="[43,)"
license="All Rights Reserved"
showAsResourcePack=false

[[mods]]
modId="yanbu3_horror_stars"
version="1.2.0-preview"
displayName="Yanbu3 Horror Stars (29-star preview)"
description=""" + '"""29 horror creature star types from bundled mods; experimental gameplay not tested."""' + """

[[dependencies.yanbu3_horror_stars]]
modId="minecraft"
mandatory=true
versionRange="[1.19.2,1.19.3)"
ordering="NONE"
side="BOTH"

[[dependencies.yanbu3_horror_stars]]
modId="forge"
mandatory=true
versionRange="[43,)"
ordering="NONE"
side="BOTH"
"""

def dec(value):
    return zlib.decompress(base64.b64decode(value))

def png_chunk(name, data):
    body = name + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xffffffff)

def star_png(name, mask, palettes):
    pal = palettes[name]
    image = bytearray()
    for y in range(32):
        image.append(0)
        for code in mask[32*y:32*(y+1)]:
            image.extend(pal[code if code < len(pal) else 3])
    return (b"\x89PNG\r\n\x1a\n"
            + png_chunk(b"IHDR",struct.pack(">2I5B",32,32,8,6,0,0,0))
            + png_chunk(b"IDAT",zlib.compress(image,9))
            + png_chunk(b"IEND",b""))

def j(obj):
    return (json.dumps(obj,ensure_ascii=False,indent=2)+"\n").encode("utf-8")

def t(text):
    return (text.rstrip()+"\n").encode("utf-8")

def build():
    stars = [(slug,title,ids.split(",")) for slug,title,ids in
             (row.split("|",2) for row in GROUPS.strip().splitlines())]
    mask = dec(MASK)
    palettes = json.loads(dec(PALETTES))
    assert len(stars)==29 and len(mask)==1024 and len(palettes)==29
    dest = Path(__file__).resolve().parent / "Yanbu3-Horror-Stars-Forge-1.19.2-v1.2.0-preview-29stars.jar"
    with zipfile.ZipFile(dest,"w",zipfile.ZIP_DEFLATED,compresslevel=9) as out:
        def add(path,data):
            out.writestr(path,data)
        add("META-INF/mods.toml",t(MOD_TOML))
        add("pack.mcmeta",j({"pack":{"description":"Yanbu3 Horror Stars Forge 1.19.2 29-star preview","pack_format":9,"forge:resource_pack_format":9,"forge:data_pack_format":10}}))
        add("data/minecraft/tags/functions/load.json",j({"values":["horrorstars:load"]}))
        add("data/horrorstars/functions/load.mcfunction",t("""scoreboard objectives add horrorstars dummy "Horror Stars"
scoreboard objectives add hs_unique dummy
execute unless score TeamStars horrorstars matches 0.. run scoreboard players set TeamStars horrorstars 0
scoreboard objectives setdisplay sidebar horrorstars
schedule function horrorstars:scan 1s replace"""))
        for hp in (35,45,50,65):
            add(f"data/horrorstars/functions/balance_{hp}.mcfunction",
                t(f"""attribute @s minecraft:generic.max_health base set {hp}
data merge entity @s {{Health:{hp}.0f}}
attribute @s minecraft:generic.attack_damage base set 6
tag @s add horrorstars_balanced_v120"""))
        scan=[]
        reset=["scoreboard players set TeamStars horrorstars 0","scoreboard players reset * hs_unique"]
        overrides=[]
        for index,(slug,title,entities) in enumerate(stars):
            cmd=91001+index
            for entity in entities:
                hp=35 if entity=="the_intruder:observer" else HEALTH.get(slug,35)
                scan.append(f"execute as @e[type={entity},tag=!horrorstars_balanced_v120] run function horrorstars:balance_{hp}")
            overrides.append({"predicate":{"custom_model_data":cmd},"model":f"horrorstars:item/stars/{slug}"})
            add(f"assets/horrorstars/models/item/stars/{slug}.json",j({"parent":"minecraft:item/generated","textures":{"layer0":f"horrorstars:item/stars/{slug}"}}))
            add(f"assets/horrorstars/textures/item/stars/{slug}.png",star_png(slug,mask,palettes))
            add(f"data/horrorstars/advancements/collect/{slug}.json",j({"criteria":{"picked":{"trigger":"minecraft:inventory_changed","conditions":{"items":[{"items":["minecraft:nether_star"],"nbt":'{HorrorStarId:"horrorstars:'+slug+'"}'}]}}},"rewards":{"function":f"horrorstars:collect/{slug}"}}))
            collect=[
                f"execute unless score #{slug} hs_unique matches 1 run scoreboard players add TeamStars horrorstars 1",
                f"execute unless score #{slug} hs_unique matches 1 run tellraw @a "+json.dumps({"text":"★ "+title+" star collected!","color":"aqua"}),
                f"scoreboard players set #{slug} hs_unique 1",
                'execute if score TeamStars horrorstars matches 10.. unless score #won hs_unique matches 1 run title @a title {"text":"10 DIFFERENT HORROR STARS!","bold":true,"color":"gold"}',
                "execute if score TeamStars horrorstars matches 10.. unless score #won hs_unique matches 1 run playsound minecraft:ui.toast.challenge_complete master @a ~ ~ ~ 1 1",
                "execute if score TeamStars horrorstars matches 10.. run scoreboard players set #won hs_unique 1",
            ]
            add(f"data/horrorstars/functions/collect/{slug}.mcfunction",t("\n".join(collect)))
            reset.append(f"advancement revoke @a only horrorstars:collect/{slug}")
            kill_ids=[]
            for i,entity in enumerate(entities):
                alt=slug if i==0 else f"{slug}_alt{i}"
                kill_ids.append(alt)
                add(f"data/horrorstars/advancements/kill/{alt}.json",j({"criteria":{"killed":{"trigger":"minecraft:player_killed_entity","conditions":{"entity":{"type":entity}}}},"rewards":{"function":f"horrorstars:drop/{slug}"}}))
                # Uses Minecraft's confirmed-hurt event; visible particles replace missing custom-renderer hit flash.
                add(f"data/horrorstars/advancements/hit/{alt}.json",j({"criteria":{"hit":{"trigger":"minecraft:player_hurt_entity","conditions":{"entity":{"type":entity},"damage":{"dealt":{"min":0.1}}}}},"rewards":{"function":f"horrorstars:hit/{alt}"}}))
                hit = [
                    f"advancement revoke @s only horrorstars:hit/{alt}",
                    f"execute at @s as @e[type={entity},distance=..12,sort=nearest,limit=1] at @s run particle minecraft:damage_indicator ~ ~1.4 ~ 0.5 0.5 0.5 0.1 10 force",
                    f"execute at @s as @e[type={entity},distance=..12,sort=nearest,limit=1] at @s run particle minecraft:dust 1 0.07 0.07 0.9 ~ ~1.4 ~ 0.5 0.6 0.5 0 10 normal"
                ]
                add(f"data/horrorstars/functions/hit/{alt}.mcfunction",t("\n".join(hit)))
            itemname=json.dumps({"text":title+" Horror Star","italic":False,"color":"gold"},separators=(",",":"))
            lore=json.dumps({"text":"Collected from "+title,"color":"gray","italic":False},separators=(",",":"))
            nbt=('{Item:{id:"minecraft:nether_star",Count:1b,tag:{CustomModelData:'+str(cmd)+',HorrorStarId:"horrorstars:'+slug+'",display:{Name:\''+itemname+'\',Lore:[\''+lore+'\']}}},PickupDelay:0s,Age:0s}')
            drop=[f"advancement revoke @s only horrorstars:kill/{alt}" for alt in kill_ids]
            drop.append(f"execute at @s run summon minecraft:item ~ ~0.5 ~ {nbt}")
            drop.append("tellraw @s "+json.dumps({"text":title+" dropped a colored Horror Star!","color":"aqua"}))
            add(f"data/horrorstars/functions/drop/{slug}.mcfunction",t("\n".join(drop)))
        scan.append("schedule function horrorstars:scan 1s replace")
        reset.append('tellraw @a {"text":"Horror Stars preview challenge reset: 0/10.","color":"yellow"}')
        add("data/horrorstars/functions/scan.mcfunction",t("\n".join(scan)))
        add("data/horrorstars/functions/reset.mcfunction",t("\n".join(reset)))
        add("data/horrorstars/functions/help.mcfunction",t('tellraw @s {"text":"Horror Stars 29-star PREVIEW; win at 10 different stars. Not all mods audited.","color":"gold"}'))
        add("assets/minecraft/models/item/nether_star.json",j({"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/nether_star"},"overrides":overrides}))
        add("data/horrorstars/README.txt",t("PREVIEW: 29 stars from bundled horror mod JARs. Other downloadable mods not fully audited. Runtime gameplay not tested."))
    assert len(zipfile.ZipFile(dest).namelist()) == 263
    print(f"Built {dest.name}: {dest.stat().st_size} bytes, 29 stars")

if __name__=="__main__":
    build()
