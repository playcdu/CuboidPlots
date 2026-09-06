"""Resolve and download exact dependencies into private test servers, never artifacts/."""
import concurrent.futures, hashlib, json, pathlib, urllib.request
ROOT=pathlib.Path(__file__).resolve().parents[1]
def get(url):
    return urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'CuboidPlots verification/0.1'}),timeout=120).read()
def modrinth(slug,version):
    data=json.loads(get(f'https://api.modrinth.com/v2/project/{slug}/version/{version.replace("+","%2B")}'))
    primary=next(f for f in data['files'] if f['primary'])
    return primary['filename'],primary['url']
def download(task):
    file,url=task
    file.parent.mkdir(parents=True,exist_ok=True)
    if not file.exists():file.write_bytes(get(url))
    return {'file':file.name,'url':url,'sha256':hashlib.sha256(file.read_bytes()).hexdigest()}
if __name__=='__main__':
    mc='1.20.1';loader='fabric';locks=json.loads((ROOT/'research/dependency-lock.json').read_text())
    shared=[modrinth('fabric-api','0.92.6+1.20.1'),modrinth('architectury-api','9.2.14+fabric'),modrinth('cloth-config','11.1.136+fabric')]
    configport=modrinth('forge-config-api-port','v8.0.3-1.20.1-Fabric')
    for parent in ['ftb','opac']:
        dest=ROOT/f'.work/servers/{mc}/{loader}/{parent}'
        files=list(shared)
        for dep in locks:
            if dep['mc']==mc and dep['loader']==loader and (parent=='ftb' and dep['mod'].startswith('ftb-') or parent=='opac' and dep['mod']=='opac'):
                files.append((dep['jar'].split('/')[-1],dep['jar']))
        if parent=='opac':files.append(configport)
        tasks=[(dest/'mods'/name,url) for name,url in files]
        tasks.append((dest/'fabric-server-launch.jar',f'https://meta.fabricmc.net/v2/versions/loader/{mc}/0.16.14/1.0.3/server/jar'))
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool: results=list(pool.map(download,tasks))
        (dest/'dependency-lock.json').write_text(json.dumps(results,indent=2))
        (dest/'eula.txt').write_text('eula=true\n')
        (dest/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port='+('25575' if parent=='ftb' else '25576')+'\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nmotd=CuboidPlots isolated verification server\n')
        print(parent,'prepared',flush=True)
