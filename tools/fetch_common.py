import concurrent.futures,io,pathlib,urllib.request,zipfile,xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
def fetch(args):
    mc,prefix,mod=args
    meta=ROOT/f'research/metadata/ftb-{mod}-fabric.xml'
    ver=[x.text for x in ET.parse(meta).findall('.//version') if x.text.startswith(prefix+'.')][-1]
    url=f'https://maven.ftb.dev/releases/dev/ftb/mods/ftb-{mod}/{ver}/ftb-{mod}-{ver}-sources.jar'
    try:
        data=urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'CuboidPlots/0.1'}),timeout=90).read()
        dest=ROOT/f'.work/upstream/{mc}/common/ftb-{mod}'
        zipfile.ZipFile(io.BytesIO(data)).extractall(dest)
        print(mc,mod,ver,'OK',flush=True)
    except Exception as exc: print(url,repr(exc),flush=True)
if __name__=='__main__':
    tasks=[(mc,prefix,mod) for mc,prefix in [('1.16.5','1605'),('1.18.2','1802'),('1.20.1','2001'),('1.21.1','2101'),('26.1.2','26.1.2')] for mod in ['chunks','teams','ranks','library']]
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as p: list(p.map(fetch,tasks))
