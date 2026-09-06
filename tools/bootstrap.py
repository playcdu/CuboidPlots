"""Download pinned toolchains into ignored .work, never system-install them."""
import concurrent.futures, hashlib, io, json, pathlib, urllib.request, zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT/'.work/toolchains'
OUT.mkdir(parents=True,exist_ok=True)
def get(url):
    return urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'CuboidPlots/0.1'}),timeout=180).read()
def fetch(task):
    name,url=task
    dest=OUT/name
    if dest.exists(): return
    try:
        if name.startswith('jdk'):
            major=name[3:]
            meta=json.loads(get(f'https://api.adoptium.net/v3/assets/latest/{major}/hotspot?architecture=x64&image_type=jdk&os=windows'))[0]
            package=meta['binary']['package']; url=package['link']; checksum=package['checksum']
            (OUT/(name+'-lock.json')).write_text(json.dumps(meta,indent=2))
        else: checksum=get(url+'.sha256').decode().strip()
        data=get(url)
        assert hashlib.sha256(data).hexdigest()==checksum
        dest.mkdir()
        zipfile.ZipFile(io.BytesIO(data)).extractall(dest)
        print(name,'ready',flush=True)
    except Exception as exc: print(name,repr(exc),flush=True)
if __name__=='__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(fetch,[('jdk8',''),('jdk17',''),('jdk21',''),('gradle8','https://services.gradle.org/distributions/gradle-8.14.3-bin.zip')]))
