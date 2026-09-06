"""Lock published parent releases and inspect their published sources, not moving branches."""
import concurrent.futures, hashlib, io, json, pathlib, urllib.request, zipfile, xml.etree.ElementTree as ET
ROOT = pathlib.Path(__file__).resolve().parents[1]
WORK = ROOT / '.work' / 'upstream'
WORK.mkdir(parents=True, exist_ok=True)
TARGETS = {'1.16.5':('1605',['fabric','forge']), '1.18.2':('1802',['fabric','forge']), '1.20.1':('2001',['fabric','forge']), '1.21.1':('2101',['fabric','forge','neoforge']), '26.1.2':('26.1.2',['fabric','forge','neoforge'])}
def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent':'CuboidPlots/0.1 research'}), timeout=90).read()
def obtain(item):
    try:
        path = WORK / item['mc'] / item['loader'] / item['mod']
        path.mkdir(parents=True, exist_ok=True)
        for key in ['pom','sources']:
            if key not in item: continue
            data = get(item[key])
            item[key+'_sha256'] = hashlib.sha256(data).hexdigest()
            if key == 'sources':
                zipfile.ZipFile(io.BytesIO(data)).extractall(path)
            else:
                (path / 'artifact.pom').write_bytes(data)
        print(item['mc'],item['loader'],item['mod'],item['version'], 'OK', flush=True)
    except Exception as exc:
        item['fetch_error'] = str(exc)
        print(item['mc'],item['loader'],item['mod'],str(exc),flush=True)
    return item
if __name__ == '__main__':
    items=[]
    opac=json.loads((ROOT/'research/metadata/opac-versions.json').read_text())
    for mc,(prefix,loaders) in TARGETS.items():
        for loader in loaders:
            for mod in ['chunks','teams','library','ranks']:
                meta=ROOT/f'research/metadata/ftb-{mod}-{loader}.xml'
                matches=[x.text for x in ET.parse(meta).findall('.//version') if x.text.startswith(prefix+'.')]
                if not matches: continue
                ver=matches[-1]
                artifact=f'ftb-{mod}-{loader}'
                base=f'https://maven.ftb.dev/releases/dev/ftb/mods/{artifact}/{ver}/{artifact}-{ver}'
                items.append({'mc':mc,'loader':loader,'mod':'ftb-'+mod,'version':ver,'jar':base+'.jar','pom':base+'.pom','sources':base+'-sources.jar'})
            matches=[v for v in opac if mc in v['game_versions'] and loader in v['loaders']]
            if matches:
                v=matches[0]; files=v['files']
                item={'mc':mc,'loader':loader,'mod':'opac','version':v['version_number'],'version_id':v['id'],'jar':next(f['url'] for f in files if f['primary']),'dependencies':v['dependencies']}
                sources=next((f['url'] for f in files if f['filename'].endswith('-sources.jar')),None)
                if sources: item['sources']=sources
                items.append(item)
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
        results=list(pool.map(obtain,items))
    (ROOT/'research/dependency-lock.json').write_text(json.dumps(results,indent=2)+'\n')
