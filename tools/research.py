"""Fetch upstream metadata, keeping exact snapshots and failures for the audit."""
import concurrent.futures, datetime, json, pathlib, urllib.request, xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'research' / 'metadata'
OUT.mkdir(parents=True, exist_ok=True)
URLS = {
    'opac-versions': 'https://api.modrinth.com/v2/project/open-parties-and-claims/version',
    'ftb-branches': 'https://api.github.com/repos/FTBTeam/FTB-Chunks/branches?per_page=100',
    'opac-branches': 'https://api.github.com/repos/thexaero/open-parties-and-claims/branches?per_page=100',
    'ranks-branches': 'https://api.github.com/repos/FTBTeam/FTB-Ranks/branches?per_page=100',
}
for mod in ['chunks', 'ranks', 'teams', 'library']:
    for loader in ['fabric', 'forge', 'neoforge']:
        URLS[f'ftb-{mod}-{loader}'] = f'https://maven.ftb.dev/releases/dev/ftb/mods/ftb-{mod}-{loader}/maven-metadata.xml'

def fetch(pair):
    name, url = pair
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'CuboidPlots-reference-research/0.1'})
        with urllib.request.urlopen(req, timeout=45) as response:
            data = response.read()
        suffix = '.xml' if url.endswith('.xml') else '.json'
        (OUT / (name + suffix)).write_bytes(data)
        if name.endswith('branches'):
            summary = [v['name'] for v in json.loads(data)]
        elif name == 'opac-versions':
            versions = json.loads(data)
            summary = {mc: [(v['version_number'], v['loaders']) for v in versions if mc in v['game_versions']][:6] for mc in ['1.16.5','1.18.2','1.20.1','1.21.1','26.1.2']}
        else:
            summary = [v.text for v in ET.fromstring(data).findall('.//version')][-8:]
        print(name, summary, flush=True)
        return {'name': name, 'url': url, 'bytes': len(data)}
    except Exception as exc:
        print(name, str(exc), flush=True)
        return {'name': name, 'url': url, 'error': str(exc)}

if __name__ == '__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(fetch, URLS.items()))
    (OUT / 'fetch-report.json').write_text(json.dumps({'utc': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'results': results}, indent=2))
