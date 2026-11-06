#!/usr/bin/env python3
"""Build Burned World reset A+B. 1.21.4 Fabric JAR, worldgen and reference-inspired textures.
A preview is generated separately; screenshots are references, not vanilla-ready UV atlases.
"""
import io, json, zipfile, math, random, collections, hashlib, re
from pathlib import Path
from PIL import Image, ImageDraw, ImageEnhance
ROOT=Path('/mnt/data')
BASE=ROOT/'BurnedWorld_Inferno_Fabric_1.21.4_v0.3.0-beta1.jar'
OUT=ROOT/'BurnedWorld_Reset_PlanAB_Fabric_1.21.4_v0.4.0-alpha.jar'
PREVIEW=ROOT/'BurnedWorld_Reset_PlanAB_Texture_Preview.png'
N='burnedworld'
with zipfile.ZipFile(BASE) as z:
    src={n:z.read(n) for n in z.namelist() if not n.endswith('/')}

def js(path,obj):
    src[path]=json.dumps(obj,indent=2,ensure_ascii=False).encode()
def txt(path,s):
    src[path]=s.encode()
def get(path):return json.loads(src[path].decode())

# World settings: actual Overworld continental / ridge / erosion noises, no nether ceiling.
dimension='data/minecraft/dimension/overworld.json'
d=get(dimension)
# Use existing biome keys wherever possible; more biomes = overworld landscapes, not Nether presets.
biomes=['ash_wastes','scarred_plains','ember_fields','soul_flats','charred_ridges']
# Climate variations: favor open plain-like basins, allow crimson and warped clusters; all temp/humidity 0
# to avoid registry unbound errors observed in beta1 test. Distinct continents/erosion/weirdness vary spatially.
pts=[]
for er,b in [(-0.95,'ash_wastes'),(-0.72,'scarred_plains'),(-0.49,'ash_wastes'),(-0.23,'scarred_plains'),(0.08,'ash_wastes'),(0.33,'ember_fields'),(0.57,'soul_flats'),(0.82,'ash_wastes'),(1.0,'scarred_plains')]:
    for cont,weird in [(-0.34,-0.45),(0.23,0.42),(0.62,-0.12)]:
        bp=b
        if cont>=0.55 and er < -0.6: bp='charred_ridges'
        pts.append({'biome':f'{N}:{bp}','parameters':{'temperature':0,'humidity':0,'continentalness':cont,'erosion':er,'depth':0,'weirdness':weird,'offset':0}})
d['generator']['biome_source']={'type':'minecraft:multi_noise','biomes':pts}
js(dimension,d)

settings='data/burnedworld/worldgen/noise_settings/ashfall.json'
s=get(settings)
s['default_block']={'Name':'minecraft:netherrack'}
s['default_fluid']={'Name':'minecraft:lava','Properties':{'level':'0'}}
s['sea_level']=63
s['aquifers_enabled']=False
s['disable_mob_generation']=False
# As previously tested: avoid references to unresolved minecraft:overworld/temperature/vegetation.
s['noise_router']['temperature']=0
s['noise_router']['vegetation']=0
s['noise_router']['final_density']='minecraft:overworld/sloped_cheese'
s['noise_router']['initial_density_without_jaggedness']='minecraft:overworld/sloped_cheese'
# no carved caves; OpenWorld Overworld heights from continents and erosion.
s['noise']['min_y']=-64
s['noise']['height']=384

# Surface rules first y=-64 floor, then biome-specific nylium/basalt/blackstone tops. Nether block interiors.
def cond_for_biome(name,block):
    return {'type':'minecraft:condition','if_true':{'type':'minecraft:biome','biome_is':[f'{N}:{name}']},'then_run':{
        'type':'minecraft:condition','if_true':{'type':'minecraft:stone_depth','offset':0,'add_surface_depth':False,'secondary_depth_range':0,'surface_type':'floor'},'then_run':{'type':'minecraft:block','result_state':{'Name':block}}}}
s['surface_rule']={'type':'minecraft:sequence','sequence':[
  {'type':'minecraft:condition','if_true':{'type':'minecraft:vertical_gradient','random_name':'minecraft:bedrock_floor','true_at_and_below':{'above_bottom':0},'false_at_and_above':{'above_bottom':5}},'then_run':{'type':'minecraft:block','result_state':{'Name':'minecraft:bedrock'}}},
  cond_for_biome('ember_fields','minecraft:crimson_nylium'),
  cond_for_biome('soul_flats','minecraft:warped_nylium'),
  cond_for_biome('charred_ridges','minecraft:basalt'),
  cond_for_biome('ash_wastes','minecraft:blackstone'),
  cond_for_biome('scarred_plains','minecraft:netherrack'),
  {'type':'minecraft:condition','if_true':{'type':'minecraft:stone_depth','offset':3,'add_surface_depth':False,'secondary_depth_range':0,'surface_type':'floor'},'then_run':{'type':'minecraft:block','result_state':{'Name':'minecraft:netherrack'}}}
]}
js(settings,s)

# Ore veins 2..4 blocks below ground. y_spread is a valid INT (earlier beta1 nested uniform was invalid).
ore_names=['coal','copper','diamond','emerald','gold','iron','lapis','redstone']
ore_count={'coal':8,'copper':7,'diamond':3,'emerald':2,'gold':4,'iron':8,'lapis':4,'redstone':4}
for name in ore_names:
    placed=f'data/{N}/worldgen/placed_feature/shallow_{name}.json'
    j=get(placed)
    j['placement']=[{'type':'minecraft:count','count':ore_count[name]},{'type':'minecraft:in_square'},
      {'type':'minecraft:heightmap','heightmap':'OCEAN_FLOOR_WG'},
      {'type':'minecraft:random_offset','xz_spread':0,'y_spread':-3},
      {'type':'minecraft:biome'}]
    js(placed,j)

# Very sparse -> wooded Overworld-style areas: native CRIMSON/WARPED huge fungi, via vanilla configured features,
# but tested base below must be NYLIUM and position must be air above terrain; cannot generate on lava.
for family in ['crimson','warped']:
    for density,count in [('sparse',2),('wooded',5)]:
        name=f'{family}_{density}_trees'
        p=f'data/{N}/worldgen/placed_feature/{name}.json'
        js(p,{'feature':f'minecraft:{family}_fungus','placement':[
          {'type':'minecraft:count','count':count},
          {'type':'minecraft:in_square'},
          {'type':'minecraft:heightmap','heightmap':'MOTION_BLOCKING'},
          {'type':'minecraft:block_predicate_filter','predicate':{'type':'minecraft:matching_blocks','blocks':f'minecraft:{family}_nylium','offset':[0,-1,0]}},
          {'type':'minecraft:biome'}
        ]})

# Four base biomes; fifth copies base and uses open landscape.
orig={b:get(f'data/{N}/worldgen/biome/{b}.json') for b in ['ash_wastes','ember_fields','charred_ridges','soul_flats']}
extra=json.loads(json.dumps(orig['ash_wastes']))
for b in biomes:
    j=json.loads(json.dumps(orig.get(b,extra)))
    j['carvers']=[]
    # Keep worldgen step indexes existing, including under-surface ores as step 6.
    j['features']=[list(step) for step in j['features']]
    # Remove any old tree/water/other fire rain features and existing Nether placed vegetation.
    for i in [9,10]:
        j['features'][i]=[]
    if b=='ember_fields':
        j['features'][10]=[f'{N}:crimson_wooded_trees','minecraft:crimson_forest_vegetation','minecraft:patch_crimson_roots']
    if b=='soul_flats':
        j['features'][10]=[f'{N}:warped_wooded_trees','minecraft:warped_forest_vegetation','minecraft:nether_sprouts']
    if b=='scarred_plains':
        j['features'][10]=['minecraft:patch_fire']
    if b=='ash_wastes':
        j['features'][10]=['minecraft:patch_fire']
    if b=='charred_ridges':
        j['features'][10]=['minecraft:patch_fire']
    if b in ('scarred_plains','ash_wastes'):
        j['features'][1]=['minecraft:lake_lava_surface']
    # Existing spawners: retain familiar mobs, exclude Wither Skeleton outside fortresses.
    j['spawners']['monster']=[x for x in j['spawners']['monster'] if x['type']!='minecraft:wither_skeleton']
    for typ,weight in [('minecraft:spider',45),('minecraft:blaze',4),('minecraft:enderman',12)]:
        if not any(x['type']==typ for x in j['spawners']['monster']):j['spawners']['monster'].append({'type':typ,'weight':weight,'minCount':1,'maxCount':2})
    if b in ('ash_wastes','scarred_plains','charred_ridges'):
        j['effects']['sky_color']=int('9B6674',16)
    elif b=='ember_fields':
        j['effects']['sky_color']=int('B3605F',16)
    else:
        j['effects']['sky_color']=int('69859C',16)
    # No water feature; lava generation by noise sea fluid
    js(f'data/{N}/worldgen/biome/{b}.json',j)

# All active custom biomes remain in available structure biome tags, including Stronghold so End is reachable,
# but precise stronghold placement within the middle of a lava ocean requires separate custom feature work.
for tag in ['bastion_remnant','nether_fortress','stronghold']:
    path=f'data/minecraft/tags/worldgen/biome/has_structure/{tag}.json'
    j=get(path);j['values']=[f'{N}:{b}' for b in biomes];js(path,j)

# Stage A+B: disable combat / dragon actions. Keep simple Ashwalker only (existing ability).
txt(f'data/{N}/function/load.mcfunction','# Reset A+B: world generation and mob appearance only. Special attacks are a later phase.\n')
txt(f'data/{N}/function/mob_powers.mcfunction','# Disabled in Reset A+B until abilities phase.\n')
txt(f'data/{N}/function/dragon/attack.mcfunction','# Deferred: two-headed model and special attacks.\n')
txt(f'data/{N}/function/dragon/meteor_wave.mcfunction','# Deferred: meteor effects and laser attack.\n')
# Remove old unsafe/buggy armor tick from default to avoid unwanted surface replacement in reset stage.
txt(f'data/{N}/function/tick.mcfunction','# Plan A+B only: no automatic world mutation around players.\n')

# Provide test commands as normal mcfunction files so user can inspect appearances quickly.
txt(f'data/{N}/function/testing/show_mobs.mcfunction','''# Summon samples onto nearby safe ground in a TEST world only.
execute positioned ~3 ~ ~3 run summon minecraft:cow ~ ~ ~
execute positioned ~-3 ~ ~3 run summon minecraft:pig ~ ~ ~
execute positioned ~3 ~ ~-3 run summon minecraft:sheep ~ ~ ~
execute positioned ~-3 ~ ~-3 run summon minecraft:creeper ~ ~ ~
execute positioned ~6 ~ ~0 run summon minecraft:spider ~ ~ ~
execute positioned ~-6 ~ ~0 run summon minecraft:zombie ~ ~ ~
execute positioned ~0 ~ ~6 run summon minecraft:skeleton ~ ~ ~
execute positioned ~0 ~ ~-6 run summon minecraft:enderman ~ ~ ~
execute positioned ~7 ~ ~7 run summon minecraft:blaze ~ ~ ~
execute positioned ~-7 ~ ~-7 run summon minecraft:wither_skeleton ~ ~ ~
''')

# Assets: Minecraft takes 2D UV sheets not perspective PNG renders. Extract pixel palette directly
# from user's exact uploaded images; map hand-painted readable faces and coherent vein placements into UV.
refs={
 'spider':'48a5241c-f4b0-4977-9f40-cb80fb05c502.png',
 'zombie':'5887953116.webp',
 'sheep':'ae4354ff-9964-40f0-8446-863f5a0a84c3.png',
 'creeper':'be48aa23-eb62-44fb-86ac-9ea622bd65f0.png',
 'pig':'c1059810-3b36-4ae4-899a-d1136b2c674e.png',
 'cow':'8d4a0366-f856-4f20-b03b-47782c9e35ae.png',
 'skeleton':'0d82d646-8f4d-4eef-990b-1b77815fd5d6.png',
 'enderman':'enderman-planetminecraft-com-13957581_iso_l.webp',
}
# Distinct source-based image color samples; crop out checkerboard/fake backgrounds.
sourcepalette={}
sourceimg={}
for kind,f in refs.items():
    im=Image.open(ROOT/f).convert('RGBA');sourceimg[kind]=im
    colors=[]
    # Take pixels from central region, avoid watermarks and far edges.
    rx=(im.width//10,im.height//10,im.width*8//10,im.height*8//10)
    sample=im.crop(rx).resize((90,90),Image.Resampling.NEAREST)
    for r,g,b,a in sample.getdata():
        mx=max(r,g,b);mn=min(r,g,b)
        if a<220 or mx<9 or mn>245:continue
        if r>g*1.18 and r>b*1.16 and r>65:colors.append((r,g,b))
    sourcepalette[kind]=sorted(colors,key=lambda rgb: rgb[0],reverse=True)

base_recolor={
 'spider':(25,24,27), 'zombie':(68,29,32), 'sheep':(42,33,32), 'creeper':(78,76,80),
 'pig':(43,28,30),'cow':(33,31,34),'skeleton':(204,191,182),'wither_skeleton':(28,25,28),
 'enderman':(55,12,17),'blaze':(61,16,13)
}
glow={
 'spider':(251,35,35),'zombie':(252,114,31),'sheep':(252,103,31),'creeper':(200,37,38),
 'pig':(246,28,39),'cow':(255,127,26),'skeleton':(236,62,49),'wither_skeleton':(244,96,31),
 'enderman':(255,97,20),'blaze':(255,132,32)
}

def clamp(n):return int(min(255,max(0,n)))
def shade(color,delta):return tuple(clamp(int(v+delta)) for v in color)
def glowcol(k):
    c=glow[k]
    p=sourcepalette.get(k)
    if not p:return c
    sample=p[min(len(p)-1,len(p)//7)]
    # preserve saturated fire highlights from reference rather than inventing palette.
    return (clamp((c[0]*2+sample[0])/3),clamp((c[1]*2+sample[1])/3),clamp((c[2]*2+sample[2])/3))

def uv_layout(kind):
    # Coordinates in 64x32 atlas: front head/body on vanilla model. Most reuse classic entity layout.
    if kind=='spider':return [(32,8,48,16),(28,20,40,28)]
    if kind in ('cow','pig','sheep'):return [(8,8,16,16),(22,20,32,30)]
    return [(8,8,16,16),(20,20,28,32)]

# Generate 64x32 / 64x64 atlas with stable hand-built pixel regions. We mix actual reference
# colors with programmatically placed hard edges for crack details, avoiding static 'noise only'.
def draw_mob_texture(kind,base_name=None):
    rng=random.Random({'spider':31,'zombie':13,'sheep':53,'creeper':89,'pig':90,'cow':74,'skeleton':81,'wither_skeleton':83,'enderman':47,'blaze':61}[kind])
    w,h=(64,64) if kind=='zombie' else (64,32)
    main=base_recolor[kind];hot=glowcol(kind)
    a=Image.new('RGBA',(w,h));p=a.load()
    # Pixelated paint for all UV islands; darker bottom, lighter upper sides.
    for y in range(h):
     for x in range(w):
      rough=rng.choice([-5,-3,-2,0,0,0,1,2,4]);top=(5 if y < h*.45 else -2)
      p[x,y]=(*shade(main,rough+top),255)
    D=ImageDraw.Draw(a)
    # Distinct large pixel clusters, not blurry randomized static. A few 2x3 weathered plates
    # are based on reference shading; coherent larger surfaces are needed at game camera distance.
    for j in range(44 if h==32 else 65):
        px=rng.randrange(w-3);py=rng.randrange(h-3)
        plate=shade(main,rng.choice([-13,-11,8,10,12]))
        D.rectangle((px,py,px+rng.choice([1,2]),py+rng.choice([1,2])),fill=(*plate,255))
    # Segmented underside, shoulder plates, leg bracers based on reference silhouettes.
    if kind in ('zombie','skeleton','wither_skeleton'):
        D.rectangle((32,20,39,23),fill=(*shade(main,-22),255))
        D.line((40,20,43,23),fill=(*shade(glow[kind],-50),255),width=2)
        D.rectangle((4,26,8,30),fill=(*shade(main,-18),255))
    if kind in ('cow','pig','sheep'):
        D.rectangle((0,21,5,27),fill=(*shade(main,-20),255))
        D.line((4,25,7,28),fill=(*shade(glow[kind],-15),255),width=2)
    # geometric cracks of distinct branching edges in heat colors
    def vein(points,width=1,bright=False):
        D.line(points,fill=shade(hot,35 if bright else -30),width=width)
        if bright:D.line(points[1:] if len(points)>1 else points,fill=hot,width=1)
    # Black stone textures should have cracks, not random scattered dots.
    for x in [3,14,25,38,49,60]:
        if x>=w-4:continue
        if rng.random()<.5:
            sy=rng.randint(2,min(h-6,18));
            vein([(x,sy),(x+rng.choice([-2,1]),sy+2),(x+rng.choice([-1,3]),sy+5)],bright=rng.random()<.45)
    face,torso=uv_layout(kind)
    fx,fy,fx2,fy2=face;tx,ty,tx2,ty2=torso
    # Underpaint torso field, vivid divisions (magma belt/ribs) rather than scattered glow.
    if kind in ('zombie','blaze'):
        D.rectangle((tx,ty+5,tx2-1,min(h-1,ty+8)),fill=shade(hot,-45))
        D.line([(tx,ty+6),(tx+3,ty+8),(tx+6,ty+6),(tx2-1,ty+7)],fill=hot,width=2)
    elif kind=='skeleton':
        D.rectangle((tx,ty,tx2-1,ty+3),fill=(84,30,31,255))
        for cy in range(ty+4,min(ty+11,h-1),2):D.line((tx+1,cy,tx2-2,cy),fill=(215,202,192,255))
        vein([(tx,ty+2),(tx+2,ty+4),(tx+3,ty+6)],bright=True)
    elif kind=='sheep':
        D.rectangle((tx,ty,tx2-1,ty+3),fill=(205,183,163,255));vein([(tx,ty+4),(tx+4,ty+6),(tx2-1,ty+5)],bright=True)
    elif kind=='enderman':
        for cy in range(ty+7,min(ty+12,h)):D.line((tx,cy,tx2-1,cy),fill=shade(hot,cy-ty-2))
    elif kind=='blaze':
        for x in [tx,tx+2,tx+5,tx2-1]:vein([(x,ty+1),(x-1,ty+5),(x,ty+9)],bright=True)
    else:
        vein([(tx,ty+2),(tx+2,ty+6),(tx+4,ty+7),(tx+5,ty+10)],bright=True)
        vein([(tx2-2,ty+1),(tx2-3,ty+4),(tx2-2,ty+7)],bright=False)

    # Paint iconic facial expressions from user reference. Everything remains attached to vanilla UV.
    D.rectangle((fx,fy,fx2-1,fy2-1),fill=(*shade(main,8),255))
    if kind=='creeper':
        # White hot square eyes / black mouth, unmistakable reference
        D.rectangle((fx+1,fy+2,fx+2,fy+3),fill=(248,239,231,255))
        D.rectangle((fx+5,fy+2,fx+6,fy+3),fill=(248,239,231,255))
        D.rectangle((fx+3,fy+4,fx+4,fy+5),fill=(21,17,20,255))
        vein([(fx+3,fy),(fx+4,fy+2),(fx+6,fy+3)],bright=False)
    elif kind=='spider':
        # bright red eyes and lava mandibles; spider geometry head is wider
        for xx in [fx+2,fx+6,fx+10]:D.rectangle((xx,fy+3,xx+2,fy+4),fill=(*hot,255))
        vein([(fx+5,fy),(fx+7,fy+2),(fx+9,fy+6)],bright=True)
    elif kind=='pig':
        for xx in [fx+1,fx+6]:D.rectangle((xx,fy+2,xx+1,fy+3),fill=(240,238,224,255))
        D.rectangle((fx+2,fy+5,fx+5,fy+7),fill=(54,35,33,255));D.point((fx+3,fy+6),fill=(20,15,17,255));D.point((fx+4,fy+6),fill=(20,15,17,255))
        vein([(fx+2,fy),(fx+3,fy+2),(fx+6,fy+4)],bright=True)
    elif kind=='cow':
        for xx in [fx+1,fx+5]:D.rectangle((xx,fy+2,xx+1,fy+3),fill=(255,215,104,255))
        D.rectangle((fx+2,fy+5,fx+6,fy+7),fill=(27,24,23,255));D.line((fx+2,fy+7,fx+6,fy+7),fill=(*hot,255))
        vein([(fx+1,fy),(fx+3,fy+2),(fx+5,fy+4)],bright=True)
    elif kind=='sheep':
        D.rectangle((fx+1,fy+2,fx+2,fy+3),fill=(253,247,225,255));D.rectangle((fx+5,fy+2,fx+6,fy+3),fill=(253,247,225,255))
        D.rectangle((fx+2,fy+4,fx+5,fy+6),fill=(12,12,13,255))
        vein([(fx+4,fy),(fx+2,fy+2),(fx+3,fy+5)],bright=True)
    elif kind=='zombie':
        D.rectangle((fx+1,fy+2,fx+3,fy+3),fill=(255,168,35,255))
        D.rectangle((fx+5,fy+2,fx+6,fy+3),fill=(23,13,17,255))
        D.rectangle((fx+1,fy+5,fx+6,fy+6),fill=(19,16,19,255))
        D.line([(fx+2,fy),(fx+3,fy+2),(fx+6,fy+5)],fill=(113,35,31,255))
    elif kind=='skeleton':
        D.rectangle((fx+2,fy+2,fx+3,fy+3),fill=(38,17,21,255));D.rectangle((fx+5,fy+2,fx+6,fy+3),fill=(38,17,21,255))
        D.rectangle((fx+3,fy+5,fx+4,fy+6),fill=(30,27,28,255))
        vein([(fx,fy),(fx+2,fy+3),(fx+3,fy+4)],bright=True)
    elif kind=='wither_skeleton':
        D.rectangle((fx+1,fy+2,fx+2,fy+3),fill=(255,107,26,255));D.rectangle((fx+5,fy+2,fx+6,fy+3),fill=(255,107,26,255))
        D.rectangle((fx+3,fy+5,fx+5,fy+6),fill=(20,10,12,255))
        vein([(fx+1,fy),(fx+3,fy+2),(fx+5,fy+6)],bright=True)
    elif kind=='enderman':
        for xx in [fx+1,fx+5]:D.rectangle((xx,fy+3,xx+2,fy+3),fill=(255,174,48,255))
        D.line((fx+1,fy+1,fx+7,fy+1),fill=(102,14,18,255))
    elif kind=='blaze':
        for xx in [fx+1,fx+5]:D.rectangle((xx,fy+2,xx+2,fy+3),fill=(255,192,66,255))
        vein([(fx+3,fy),(fx+5,fy+2),(fx+7,fy+4)],bright=True)
    # crisp glowing joints on legs/arms in the reference
    if kind in ('spider','cow','pig','creeper','enderman','blaze'):
        for lx in [4,18,35,51]:
            if lx>=w:continue
            yy=min(h-3,24)
            D.line((lx,yy,lx+2,yy),fill=(*hot,255))
    return a

file_map={
 'spider':'assets/minecraft/textures/entity/spider/spider.png',
 'zombie':'assets/minecraft/textures/entity/zombie/zombie.png',
 'sheep':'assets/minecraft/textures/entity/sheep/sheep.png',
 'creeper':'assets/minecraft/textures/entity/creeper/creeper.png',
 'pig':'assets/minecraft/textures/entity/pig/pig.png',
 'cow':'assets/minecraft/textures/entity/cow/cow.png',
 'skeleton':'assets/minecraft/textures/entity/skeleton/skeleton.png',
 'wither_skeleton':'assets/minecraft/textures/entity/skeleton/wither_skeleton.png',
 'enderman':'assets/minecraft/textures/entity/enderman/enderman.png',
 'blaze':'assets/minecraft/textures/entity/blaze.png'
}
textures={}
for kind,path in file_map.items():
    im=draw_mob_texture(kind)
    # 2x texture res for crisp vanilla geometry; 1 atlas pixel becomes 2 blocks.
    im=im.resize((im.width*2,im.height*2),Image.Resampling.NEAREST)
    textures[kind]=im
    with io.BytesIO() as f:
        im.save(f,format='PNG',optimize=True);src[path]=f.getvalue()
# Wool outer layer: source reference half beige / half lava black, cracks only on scorched region.
wool=Image.new('RGBA',(64,32));d=ImageDraw.Draw(wool)
rg=random.Random(21)
for y in range(32):
 for x in range(64):
    b=(207,190,166) if x>=35 else (36,28,27)
    jitter=rg.choice([-9,-5,0,1,5,8]);d.point((x,y),fill=(*shade(b,jitter),255))
for x in range(4,31,8):d.line([(x,5),(x+2,9),(x+1,15),(x+4,19)],fill=(255,90,30,255),width=2)
for x in range(35,64,4):
 for y in range(1,32,5):
  if rg.random()<.4:d.point((x,y),fill=(237,218,194,255))
wool=wool.resize((128,64),Image.Resampling.NEAREST)
f=io.BytesIO();wool.save(f,format='PNG');src['assets/minecraft/textures/entity/sheep/sheep_fur.png']=f.getvalue()

# Reference images do not themselves supply UV atlases. Save lightweight labels/metadata, never claim exact geometry.
js('assets/burnedworld/reference_attribution.json',{'description':'Mob UV textures in this pack were painted using the visual color and geometry cues from the user-supplied reference renders. The 3D perspective PNGs are not UV atlases and were not copied directly over the vanilla Minecraft model.','mobs':{k:Path(p).name for k,p in refs.items()},'unprovided_reference':['blaze','wither_skeleton']})

# A simple in-jar text guide for upcoming model work.
txt('README-PLAN-AB.txt', '''BURNED WORLD – RESET PLAN A+B v0.4.0-alpha – Minecraft Java 1.21.4 Fabric

PLAN A: Overworld-style continents, hills, spacious plains and some crimson/warped woods; Nether surface blocks; lava seas; no biome carvers; no cave-noise final density; near-surface ore at about 3 blocks depth.
PLAN B: UV atlases for 10 mobs using user-provided rendered PNG art as primary visual references. Vanilla 3D geometry is unchanged; pixel-perfect matching of rendered PNGs needs dedicated custom entity models and extra renderer code.
Known limitations: Java/Fabric launch not testable in this environment; a new world is mandatory. Exact End stronghold-in-ocean arrangement and two-headed dragon are postponed. Existing biome tags keep strongholds eligible generally. No mob special attacks are enabled by this reset.

Test mob command: /function burnedworld:testing/show_mobs  (activate cheats; use in a test world)
''')
mod=get('fabric.mod.json')
mod['version']='0.4.0-alpha'
mod['name']='Burned World – Reset Plan A+B'
mod['description']='Overworld terrain and Nether-colored biomes, lava seas, surface ores, no carvers, and mob appearances based on supplied reference renders.'
js('fabric.mod.json',mod)
js('pack.mcmeta',{'pack':{'pack_format':61,'description':'Burned World Reset Plan A+B (Minecraft 1.21.4)'}})

# Make preview sprite sheets from EXACT included textures (not generated reference images).
view=Image.new('RGB',(1200,640),'#e3e4e7')
D=ImageDraw.Draw(view)
for i,(kind,img) in enumerate(textures.items()):
    xx=(i%5)*240;yy=(i//5)*320
    im=img.resize((224, 224 if img.height==img.width else 112),Image.Resampling.NEAREST)
    view.paste(im,(xx+8,yy+28),im)
    D.text((xx+8,yy+8),kind.replace('_',' ').upper(),fill=(34,34,43))
D.text((10,612),'Flat UV texture atlases - model geometry remains vanilla. PNG renders are references, not atlas files.',fill=(38,39,45))
view.save(PREVIEW)

# Validate JSON syntax and dependencies before packing.
for path,data in src.items():
    if path.endswith('.json') or path=='pack.mcmeta':
        try:json.loads(data.decode())
        except Exception as e:raise ValueError((path,str(e)))
# Custom biomes and features are real files; do not refer to missing biome ID.
for p in pts:
    assert f'data/{p["biome"].replace(":", "/worldgen/biome/")}.json' in src
# Name of file for namespace-based resources verified above.
for b in biomes:
    j=get(f'data/{N}/worldgen/biome/{b}.json')
    for group in j['features']:
      for feature in group:
        if feature.startswith(N+':'):
            assert f'data/{N}/worldgen/placed_feature/{feature.split(":")[1]}.json' in src,feature
for path,data in src.items():
    if b'"burnedworld:crimson_forest"' in data and path.endswith('.json'):raise Exception('Removed nether biome ID in '+path)
# Build reproducible zip, keep class intact. (No signing involved.)
with zipfile.ZipFile(OUT,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=8) as z:
 for path in sorted(src):z.writestr(path,src[path])
with zipfile.ZipFile(OUT) as z:
 assert z.testzip() is None
 assert z.read('com/burnedworld/BurnedWorld.class')[:4]==b'\xca\xfe\xba\xbe'
 print('JAR:',OUT,OUT.stat().st_size,'files:',len(z.namelist()))
 print('SHA256:',hashlib.sha256(OUT.read_bytes()).hexdigest())
 print('Preview:',PREVIEW)
 print('Biome climate points:',len(pts))
 print('Hostile monster types by biome:')
 for b in biomes:
    j=get(f'data/{N}/worldgen/biome/{b}.json');print(' ',b, [x['type'].split(':')[-1] for x in j['spawners']['monster']])
