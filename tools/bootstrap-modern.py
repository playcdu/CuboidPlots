"""Pin and fetch the extra toolchain required by current upstream loader builds."""
import concurrent.futures,hashlib,io,json,zipfile
from prepare_servers import ROOT,get
lock=ROOT/'research/toolchains-lock.json'
pins=json.loads(lock.read_text())
if 'jdk25' not in pins:
    release=json.loads(get('https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'))[0]
    pins['jdk25']={'version':release['release_name'],'package':release['binary']['package']}
pins['gradle9']={'version':'9.5.0','url':'https://services.gradle.org/distributions/gradle-9.5.0-bin.zip'}
lock.write_text(json.dumps(pins,indent=2))
def fetch(name):
    folder=ROOT/'.work/toolchains'/name
    if folder.exists():return
    pin=pins[name]
    url=pin['package']['link'] if name.startswith('jdk') else pin['url']
    checksum=pin['package']['checksum'] if name.startswith('jdk') else get(url+'.sha256').decode().strip()
    payload=get(url);assert hashlib.sha256(payload).hexdigest()==checksum
    folder.mkdir(parents=True);zipfile.ZipFile(io.BytesIO(payload)).extractall(folder)
    print(name,pin['version'],'ready',flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:list(pool.map(fetch,['jdk25','gradle9']))
