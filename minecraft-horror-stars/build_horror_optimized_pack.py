#!/usr/bin/env python3
"""Assemble Yanbu3 Horror Optimized 1.0.2 as importable Modrinth pack.

Source pack: All the Creatures PTSD edition v1.0.0 (author-published at Modrinth).
User-requested removals: 7 non-horror mods only.
All original horror mods, FPSBoost, and TCTcore remain installed.
Note: modpack structural tests are NOT runtime tests for TPS or mob AI.
"""
import base64
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import urllib.request
import zipfile
import zlib

HERE = Path(__file__).resolve().parent
BASE = "https://cdn.modrinth.com/data/V2qO0OFH/versions/cKbTjw1c/All%20the%20Creatures%20PTSD%20edition%201.0.0.mrpack"
BASE_SHA256 = "821d7029855cda02023bf23d064f39dc83912794a71edb1af2578aec557f7cf0"
OUT = HERE / "Yanbu3_Horror_Optimized_v1.0.2_Forge_1.19.2.mrpack"
REMOVED = {
    "treeharvester", "oreharvester", "gravelminer",
    "smelt_netheritescrap", "spartanweaponry", "spartanshields", "jei-"
}
ADDITIONS = [
    dict(path="mods/fullbrightnesstoggle-1.19.2-4.0.jar",
         hashes={"sha1":"122802ccc00e3801e3536826c3a77f556298d021",
                 "sha512":"bb9f76321bbd596c9ae6b97b0ced6127135a3f9ba9c7ca40f5b7bd652581db9f2daa87241739cea4874e0e5224722bd7f287c33bf55b5b46cca8a70afcf4021e"},
         env={"client":"required","server":"unsupported"},
         downloads=["https://cdn.modrinth.com/data/aEK1KhsC/versions/lAzcc0Dt/fullbrightnesstoggle-1.19.2-4.0.jar"],fileSize=30077),
    dict(path="mods/Essential_1-5-2_forge_1-19-2.jar",
         hashes={"sha1":"bb851129f6d2ed7049de3365b044ddd6c52c9d7d",
                 "sha512":"0d946400b5fcad6714ebd7dfb9e9fca039ee83bf5ef1ca84530709722fbaa6919fa97926e9727a7f2cad77ce0ece40351c51f11bf2d17f1282f4f516028b601e"},
         env={"client":"required","server":"unsupported"},
         downloads=["https://cdn.modrinth.com/data/k2ZPuTBm/versions/9gCY0MKN/Essential_1-5-2_forge_1-19-2.jar"],fileSize=51561513)
]
# Source config from the user's old pack, preserved except explicit performance changes.
GRAVEYARD_ORIGINAL = "eNrFWFtv2zYUfm6A/AcufWmBVlMSuysK7CFN28TA0gV1tmIojIKWjiQiFKmSVDzvYb9951AX35RYqV0MaFCbPNfvXOnDgy9HNxmwC8PvYM5NzF6yc21MWTiI2XnG80JohUcqEenR5PDg8ODJYziCqLlqblDGkydPP3GVwht2HITsX3YcngyCkM4z4NJlI3XOrRMqvc64BfYrG4RhdS/sW23tuMxzrfhUwshBjvdfjgR+CHKhIDI8cUEM0zL9ijKi26NVhV6dl5Xzv89yXSo3vi2ltCM1zrSuvowLkBLlDpdZwxXWygaIr/TUIuVp2EEaVtRppksUyWfqRuRgRuqDSDOHTK9qglU0TsLB68rbmOc8hUs0scHiLI6FI3gJkyDs8KwWGWktY40aQUKhjbuWfA7mTMWXiHCl+n6D49Jw0rKsGXle9+JB+cjTIPhLl4eb8V7TdBJ2O1djf2F0WYzFP7AWhOF2PH6DO+G8oY2Fg7AHjJjghYV7WMI6bJXJ3OTa3OgyzRRYsup40OTuVSmdKMjHJgOcKWFT1OmyKJLwOuiB/KZrx8Me6bVWaqdtpY3UXSkVGKqzd6Vpgmq7zO406AOXlAqd2GEhS63jcy0lRI5UvMcMcHNfzOA/LpXznZASTTVVLVsfdZT8ESC2Y8eT5IPhaY5sXca9fDVYMa/wpdAUxl+/J4kFisXL4+FmKBapagvUVmVqg1UYEMthRwu95gZ7j4RtjXOdLijqA+9oCgS/gw6nKE2HlT+JgW8lqIiwo4B32YMFwnwHQtS22dRFG/BIy7mrzIq4elsahQ2zVLJO5IRLC80lfBYuA4PjYGE4+H4dL0j7625HyJ9CxSLiTpt77WjU/QAzDNwht3KTHo3pU01LXXObqR3zZQY1zcmwnzNd6dGYJVSXWcPHuD4zHLU/GvQ2KXZA3d5ioTqtvkYGoAAz6eHmuOY5r1ioE/YNwn2DZkPiYL+h+45quK6b4v9RC36lmXwPqEthuiAhhFQ/KLeEqJH2g2A3wIseWD/kxnH4CDdO91z4vErbk8f4rMjwnBvoU3QfG+KHyq3tCNtx6tFMehbvmmVd8/FSm3jrsF4hCjL6Nunagrx+PfVwnjl8mBR+Lxncs2f6Y3qp2D9w76keCpUqDNcgDB9i4/V7gEu8j3WO3sY3JIpcPamfH+tbxOHBU/YZZKRzYE6zVTcr937ads9ICP6NUWTkSgP2jT9674PDcG2NhfUfbUPBLHlmg4aVlnbPVIlsKSgD61xAn1hKYWQWPWM6YZgOFRV75nvQC9aMZfpESf6CtWn7gjXTg9XT43mlfaFRJCynx8MUM5UJxWyTq6j6Z/SiuafswT0Td1XMvOnc2zHzycnAH7dunbUhWXMubTFMdMqaRc8yIyy5nBidoy58MpDXtJKSjijjKvKet9jgcc6eZWglKldlPgVD4ZZ6ht836btc9rlriWphlHcTi66Gd8kIYSr46ahdNjukIoSWZSiPcRbrkoIfZWAd4oohwq2ti2kJyDZY3gI8wHjQteQmXYZvhoOPTQHjjat8VMWDb3C3AVlRNyWA89KKiNE7wLLYv2y8nqQKfMqFshX6m79rtFKrf1ck6U39faQibhQn8vf4YiGzPmkXZexiNpeOPcOXhogEPr9XK2qsSxU7w6Pb5430zd6zqLNtXWqTMrA5vsQ8zeSehvAIOVf0kwmnxl/T7kHmmN9xpfgeTIzMvHC7icg4OgjxpS7tjsZIEWXXWN71j17fLSeHWJT5PgHfp6x3YMG4PUlMBbZyrKrMaJ3v17jdpHHpuNkxHaiP7QknU2KT2jGrapT3AE4MPL7BCbtrmufaYH98SNKmoMW83dYZNykDHFleE/5/ye07P7RGzbR62IEOafXk8RLr9adaYJvn47gebA+/uDok09jys2ay2IDfNmdLdv4HAaVa5Q=="
MIDNIGHT_ORIGINAL = "eNrNVMGOozAMvc9XWHOuqk67vezPRC4Y8BaSKE5AaLT/vg6lTFjNaK5zI8/PwX7vwfsLwGufwp2CqToUMkMSrl5/Q4O90KEoi8fJmoCRtHpZKrEjWGDIMFSYH2xL0AQ3wBtEB9cDTBw7PdyIbQu5pXcTSQS0NVwLuOO2U/wIs0vLXUIROOZbTscrNC6U3d6J8K0vBzjqYKdlMO+8Sd78SYOXCkOeOIb0WGcDjbhk67LUOx0+mFKPnRLinbvPBocbk612tw6pj/zQSPfZdbEVtBxn44NrA4kYahqqYtm+cSqdKdZOlY487JV+cmDjQOZATZHCwJYEOjdBXmIRKhcDsCzKPZuP6oRCV+XLAc75+e30OFzy4bwesjm/MnBZgA9pV3HYjqm3FFAt2G1rSd+9abgLzXlb5UH6mdnBQZVk3Lsz8tJgyEaOTFI6vdE0E9kZCvJhtURsSUqWqElmfcmaBFUzC7rPWqZtsfiaN3LfY06tdOhJOm7iU/f/xqv0wm9JntvvSau5XjdliaZOQQmmxrkkYdsG98zBLRDeTQzoa+fCTo/RcW1atWDC+aufTObAyvmZqalwpE+1Wr+Hz0qbuzcMK/7y9x8GR+bF"
EMBEDDIUM_ORIGINAL = "eNp9kj9Pw0AMxfd+iiozAxIbW8WfCbEAA5Pl3jmJ1ctduPO1RFW/O07SJiIVjDn//Pz8nONqvS6+MjqWrrhfH/VTHw6EUlOEuVA8Pj1vPl7ei5uRcIR7Sv8A5HHrCPZceRIhJSRm0tqpBwq0e/SG7DwTo7ZAQ02IHaBzwaCE2Ctv3j5fHy66feUAliMZmWhjKKXziN/zz4RENOwrRUp06cLkRHAxAkmwUgS2uSwpLtQsp0GOvQlNi8KDdrBwwOi1Ky2UTZtB97GaIdaEFhw3LArdTQm0FMsQm374HIKps9+pBXZ9q9RRe3vt22n7A3YJLKlFGOHcWhRaGkDPjT5D8K7TI6TBsNC35EiL3foUyIueEUx2boxpUW8xChuV+JMoQwXBGJcTB39d3uo9d1Ci+UdiiFavuqeoTmFIR64xvaVPLhv1PAqmoObm404J+yBcsv5G6ijNGddsCWzww7tmLTIYnnpXpx/KmgK5"
HEROBRINE_ORIGINAL = "eNpNj7FqwzAQhuca/A6Hs7SLMaHp1i1DhkCGTCF0kJ2zfUTSFekc47evziapF4F0+r//uzy7FgcMXAfyCOdfM3ryXfGTZ2+bM0qEnkdoLEeEiQdojIcOBYShf6VqbDlgeoAbRiXEEt7JQ225ucePhCr2ywD2FMX4Bgv4hu2urF41ktKOPLnBwQQWH2gV2JuoXTXqOSOUdoEjORKFfFUL5JA0LftuLhbS3pWhNVFiqdH/XY/UopBbVD6raiVD7SqrK7O301IPik9i/MAwcrC3mXp63uCUfipRwoB5lmd/t6Fvbg=="

def restore(b64):
    return zlib.decompress(base64.b64decode(b64))

def main():
    subprocess.run([sys.executable,str(HERE/"build_horror_stars.py")],check=True)
    stars = HERE/"Yanbu3-Horror-Stars-Forge-1.19.2-v1.2.0-preview-29stars.jar"
    assert stars.is_file(), "Missing star JAR"
    request = urllib.request.Request(BASE, headers={"User-Agent":"Yanbu3-Horror-Optimized-Builder/1.0"})
    with urllib.request.urlopen(request,timeout=150) as response:
        payload = response.read()
    assert hashlib.sha256(payload).hexdigest()==BASE_SHA256, "Original Modrinth modpack checksum mismatch!"
    graveyard = restore(GRAVEYARD_ORIGINAL).decode("utf-8-sig")
    # Avoid giant horde spikes, but keep all normal monster types and all bosses.
    original_event = r'(\["The Graveyard - Horde Config"\.horde\][^\[]*?\bgenerate = )true'
    graveyard, num = re.subn(original_event,r'\g<1>false',graveyard,count=1)
    assert num == 1, "Horde toggle missing"
    graveyard = graveyard.replace("maxSummonedMobs = 30","maxSummonedMobs = 6")
    graveyard = graveyard.replace("maxGroupSizeSummonedMobs = 5","maxGroupSizeSummonedMobs = 2")
    graveyard = graveyard.replace("maxGroupSizeRevenant = 8","maxGroupSizeRevenant = 3")
    graveyard = graveyard.replace("minGroupSizeRevenant = 5","minGroupSizeRevenant = 2")
    graveyard = graveyard.replace("maxGroupSizeGhoul = 5","maxGroupSizeGhoul = 3")
    graveyard = graveyard.replace("minGroupSizeGhoul = 2","minGroupSizeGhoul = 1")
    # Retain all monster types, Lich, and other bosses.
    assert '["The Graveyard - Structures Config".lichPrison]' in graveyard
    assert re.search(r'lichPrison\]\s*generate = true',graveyard)
    midnight = json.loads(restore(MIDNIGHT_ORIGINAL))
    midnight["lurker_spawn_rate"]=2
    midnight["nether_lurker_spawn_rate"]=1
    embeddium = json.loads(restore(EMBEDDIUM_ORIGINAL))
    embeddium["performance"]["chunk_builder_threads"]=2  # leave CPU time for entity AI
    with zipfile.ZipFile(io.BytesIO(payload)) as source:
        index=json.loads(source.read("modrinth.index.json"))
        assert index["dependencies"]=={"forge":"43.5.0","minecraft":"1.19.2"}
        before=len(index["files"])
        removed=[x for x in index["files"] if any(t in x["path"].lower() for t in REMOVED)]
        assert len(removed)==7,removed
        index["files"]=[x for x in index["files"] if x not in removed]+ADDITIONS
        assert len(index["files"])==before-7+2
        index["name"]="Yanbu3 Horror Optimized"
        index["versionId"]="1.0.2"
        index["summary"]="Same horror creatures and bosses, seven user-requested utility/gear removals, lightweight horde and summon settings, Essential, Full Bright, Forge 1.19.2. Runtime TPS not guaranteed."
        with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=7) as out:
            for obj in source.infolist():
                out.writestr(obj,source.read(obj.filename))
            out.writestr("overrides/mods/"+stars.name,stars.read_bytes())
            out.writestr("overrides/config/graveyard-1.19.x-common.toml",graveyard.encode())
            out.writestr("overrides/config/midnightlurkerconfig.json",json.dumps(midnight,indent=2)+"\n")
            out.writestr("overrides/config/embeddium-options.json",json.dumps(embeddium,indent=2)+"\n")
            out.writestr("overrides/config/True Herobrine.toml",restore(HEROBRINE_ORIGINAL))
            out.writestr("overrides/options.txt","version:3105\nrenderDistance:8\nsimulationDistance:5\nparticles:1\nmaxFps:120\n")
            out.writestr("modrinth.index.json",json.dumps(index,indent=2,ensure_ascii=False)+"\n")
            out.writestr("overrides/yanbu3-pack-info.txt",
                "v1.0.2: 7 requested mod removals only. No horror mob or boss removed. No In Control.\n"
                "Modrinth downloads FPSBoost and The Boy in the Bath, exactly as in the original pack.\n"
                "Horde event disabled, boss minions limited, large regular mob groups reduced.\n"
                "Embeddium chunk building limited to two threads, render 8 and sim distance 5.\n"
                "This is an untested tuning profile; cannot guarantee FPS/TPS stability.\n")
    with zipfile.ZipFile(OUT) as final:
        assert final.testzip() is None
        idx=json.loads(final.read("modrinth.index.json"))
        assert len(idx["files"])==23
        n=final.namelist()
        assert len([x for x in n if x.startswith("overrides/mods/") and x.endswith(".jar")])==11
        assert len(n)==19, (len(n),n)
        assert all(not any(w in f["path"].lower() for w in REMOVED) for f in idx["files"])
        assert any("collective" in f["path"].lower() for f in idx["files"])
        assert any("fpsboost" in f["path"].lower() for f in idx["files"])
        assert all("incontrol" not in f["path"].lower() for f in idx["files"])
    print("BUILT",OUT.name,OUT.stat().st_size,"sha256",hashlib.sha256(OUT.read_bytes()).hexdigest())

if __name__ == "__main__":
    main()
