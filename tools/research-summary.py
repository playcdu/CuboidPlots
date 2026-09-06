"""Generate an evidence index without turning publication into a compatibility claim."""
import hashlib,json,pathlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
locks=json.loads((ROOT/'research/dependency-lock.json').read_text())
lines=['# Upstream release inventory','','Publication inventory researched on 2026-09-07. A published dependency does not imply that this addon has compiled or passed tests. See the separate test report.','','| Minecraft | Loader | Parent release | FTB Ranks |','|---|---|---|---|']
for mc in ['1.16.5','1.18.2','1.20.1','1.21.1','26.1.2']:
    for loader in ['fabric','forge','neoforge']:
        entries=[x for x in locks if x['mc']==mc and x['loader']==loader]
        parents=[x for x in entries if x['mod'] in ['ftb-chunks','opac']]
        if not parents:continue
        ranks=next((x['version'] for x in entries if x['mod']=='ftb-ranks'),'No matching published loader artifact; configuration fallback')
        for parent in parents:lines.append(f"| {mc} | {loader} | [{parent['mod']} {parent['version']}]({parent['jar']}) | {ranks} |")
lines+=['','## Publication gaps','','- OPAC has no 1.16.5 version in its official Modrinth release listing. Its oldest source branch and current release line are 1.18.2.','- FTB Chunks has no 1.21.1 or 26.1.2 Forge artifact in the official Forge Maven metadata. Fabric and NeoForge artifacts are published.','- No dedicated FTB Chunks NeoForge 1.20.1 artifact appears in the official NeoForge Maven metadata. Running a Forge build on legacy NeoForge has not been established by this inventory. It is not counted as supported.','- OPAC lists Forge, Fabric and NeoForge builds for 1.21.1 and 26.1.2.','', '## Evidence and selected-source audit','','The complete official responses are in `research/metadata/`. `research/dependency-lock.json` records exact releases, source URLs, hashes, dependency IDs, and any failed fetches. Loader source archives for FTB contain only the loader module; `tools/fetch_common.py` also fetches the corresponding published common source archive. These common archives are essential to the protection-hook audit.','','Primary locations: [FTB Maven](https://maven.ftb.dev/releases/dev/ftb/mods/), [OPAC official release API](https://api.modrinth.com/v2/project/open-parties-and-claims/version), [FTB Chunks source](https://github.com/FTBTeam/FTB-Chunks), [OPAC source](https://github.com/thexaero/open-parties-and-claims), [FTB Ranks source](https://github.com/FTBTeam/FTB-Ranks).']
(ROOT/'research/COMPATIBILITY.md').write_text('\n'.join(lines)+'\n')
toolchains={}
for name in ['jdk8','jdk17','jdk21']:
    lock=json.loads((ROOT/f'.work/toolchains/{name}-lock.json').read_text());toolchains[name]={'version':lock['release_name'],'package':lock['binary']['package']}
toolchains['gradle8']={'version':'8.14.3','url':'https://services.gradle.org/distributions/gradle-8.14.3-bin.zip'}
(ROOT/'research/toolchains-lock.json').write_text(json.dumps(toolchains,indent=2)+'\n')
source_index=[]
for path in (ROOT/'.work/upstream').rglob('*.java'):
    if path.name in ['ClaimedChunkManager.java','ClaimedChunkManagerImpl.java','ChunkProtection.java','FTBRanksAPI.java','PermissionValue.java','Team.java','ServerClaimsManager.java']:
        source_index.append({'path':str(path.relative_to(ROOT/'.work/upstream')).replace('\\','/'),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
(ROOT/'research/audited-source-index.json').write_text(json.dumps(source_index,indent=2)+'\n')
