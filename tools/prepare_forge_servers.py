"""Install exact Forge dependencies into isolated test servers."""
import argparse, concurrent.futures, json, subprocess
from prepare_servers import ROOT, download, modrinth

parser=argparse.ArgumentParser();parser.add_argument('--mc',choices=['1.18.2','1.20.1'],default='1.20.1');args=parser.parse_args()
mc=args.mc;forge_version='47.4.10' if mc=='1.20.1' else '40.3.12'
java=next((ROOT/'.work/toolchains/jdk17').glob('*/bin/java.exe'))
locks=json.loads((ROOT/'research/dependency-lock.json').read_text())
installer=ROOT/f'.work/toolchains/forge-{mc}-{forge_version}-installer.jar'
download((installer,f'https://maven.minecraftforge.net/net/minecraftforge/forge/{mc}-{forge_version}/forge-{mc}-{forge_version}-installer.jar'))
for parent in ['ftb','opac']:
    dest=ROOT/f'.work/servers/{mc}/forge/{parent}'
    architectury,cloth=('9.2.14','11.1.136') if mc=='1.20.1' else ('4.12.94','6.5.116')
    files=[modrinth('architectury-api',architectury+'+forge'),(f'cloth-config-forge-{cloth}.jar',f'https://maven.shedaniel.me/me/shedaniel/cloth/cloth-config-forge/{cloth}/cloth-config-forge-{cloth}.jar')]
    for dep in locks:
        if dep['mc']==mc and dep['loader']=='forge' and (parent=='ftb' and dep['mod'].startswith('ftb-') or parent=='opac' and dep['mod']=='opac'):
            files.append((dep['jar'].split('/')[-1],dep['jar']))
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        results=list(pool.map(download,[(dest/'mods'/name,url) for name,url in files]))
    (dest/'dependency-lock.json').write_text(json.dumps(results,indent=2))
    (dest/'eula.txt').write_text('eula=true\n')
    server_port=(25577 if mc=='1.20.1' else 25587)+(0 if parent=='ftb' else 1)
    (dest/'server.properties').write_text(f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={server_port}\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\n')
    config=dest/'world/cuboidplots';config.mkdir(parents=True,exist_ok=True)
    (config/'config.properties').write_text('provider=config\ndefault.max_regions=32\ndefault.max_volume=1000000\n')
    if not (dest/f'libraries/net/minecraftforge/forge/{mc}-{forge_version}/win_args.txt').exists():
        with (dest/'installer.log').open('w') as out:
            subprocess.run([str(java),'-jar',str(installer),'--installServer',str(dest)],cwd=dest,stdout=out,stderr=subprocess.STDOUT,check=True)
    print(parent,'Forge server installed',flush=True)
