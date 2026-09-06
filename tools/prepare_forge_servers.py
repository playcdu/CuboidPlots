"""Install exact Forge dependencies into isolated test servers."""
import concurrent.futures, json, subprocess
from prepare_servers import ROOT, download, modrinth

java=next((ROOT/'.work/toolchains/jdk17').glob('*/bin/java.exe'))
locks=json.loads((ROOT/'research/dependency-lock.json').read_text())
installer=ROOT/'.work/toolchains/forge-1.20.1-47.4.10-installer.jar'
download((installer,'https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-installer.jar'))
for parent in ['ftb','opac']:
    dest=ROOT/f'.work/servers/1.20.1/forge/{parent}'
    files=[modrinth('architectury-api','9.2.14+forge'),modrinth('cloth-config','11.1.136+forge')]
    for dep in locks:
        if dep['mc']=='1.20.1' and dep['loader']=='forge' and (parent=='ftb' and dep['mod'].startswith('ftb-') or parent=='opac' and dep['mod']=='opac'):
            files.append((dep['jar'].split('/')[-1],dep['jar']))
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        results=list(pool.map(download,[(dest/'mods'/name,url) for name,url in files]))
    (dest/'dependency-lock.json').write_text(json.dumps(results,indent=2))
    (dest/'eula.txt').write_text('eula=true\n')
    (dest/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port='+('25577' if parent=='ftb' else '25578')+'\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\n')
    config=dest/'world/cuboidplots';config.mkdir(parents=True,exist_ok=True)
    (config/'config.properties').write_text('provider=config\ndefault.max_regions=32\ndefault.max_volume=1000000\n')
    if not (dest/'libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt').exists():
        with (dest/'installer.log').open('w') as out:
            subprocess.run([str(java),'-jar',str(installer),'--installServer',str(dest)],cwd=dest,stdout=out,stderr=subprocess.STDOUT,check=True)
    print(parent,'Forge server installed',flush=True)
