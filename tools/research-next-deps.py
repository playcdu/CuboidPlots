"""Pin published support libraries for the next port using official release metadata."""
import concurrent.futures,json,urllib.parse
from prepare_servers import ROOT,get
def query(task):
    mc,project=task
    url=f'https://api.modrinth.com/v2/project/{project}/version?'+urllib.parse.urlencode({'game_versions':json.dumps([mc]),'loaders':json.dumps(['fabric'])})
    versions=json.loads(get(url))
    (ROOT/f'research/metadata/{project}-{mc}.json').write_text(json.dumps(versions,indent=2))
    release=next(v for v in versions if v['version_type']=='release')
    return {'minecraft':mc,'project':project,'version':release['version_number'],'id':release['id'],'files':release['files'],'dependencies':release['dependencies']}
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    results=list(pool.map(query,[(mc,project) for mc in ['1.21.1','26.1.2'] for project in ['fabric-api','architectury-api','cloth-config','forge-config-api-port']]))
(ROOT/'research/support-dependency-lock.json').write_text(json.dumps(results,indent=2))
for entry in results:print(entry['minecraft'],entry['project'],entry['version'],flush=True)
