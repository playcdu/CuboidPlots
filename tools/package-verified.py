"""Package a committed version branch after behaviour, restart and production checks."""
import argparse,hashlib,json,pathlib,shutil,subprocess,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--mc',required=True);parser.add_argument('--source',type=pathlib.Path,required=True);args=parser.parse_args()
source=args.source.resolve();mc=args.mc
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=source,text=True).strip()
dirty=subprocess.check_output(['git','status','--porcelain','--','core','platform','fabric','forge','gradle.properties','build.gradle'],cwd=source,text=True)
assert not dirty,'Commit runtime source and build definitions before packaging'
manifest_path=ROOT/'artifacts/manifest.json'
manifest=json.loads(manifest_path.read_text()) if manifest_path.exists() else {'artifacts':[]}
manifest['artifacts']=[entry for entry in manifest['artifacts'] if entry['minecraft']!=mc]
for loader in ['fabric','forge']:
    suffix='-all' if loader=='forge' else ''
    built=source/f'{loader}/build/libs/cuboidplots-{mc}-{loader}-0.1.0{suffix}.jar'
    checksum=hashlib.sha256(built.read_bytes()).hexdigest()
    with zipfile.ZipFile(built) as jar:
        names=set(jar.namelist())
        assert not any('/testhost/' in name or 'FtbTransferAccess' in name for name in names),'Test fixtures must not ship'
        config=json.loads(jar.read('cuboidplots.mixins.json'))
        for mixin in config['mixins']:assert (config['package']+'.'+mixin).replace('.','/')+'.class' in names,'Missing mixin '+mixin
        if loader=='forge':assert 'cuboidplots.refmap.json' in names
        else:assert 'testhost' not in jar.read('fabric.mod.json').decode()
    verification={}
    for parent in ['ftb','opac']:
        server=ROOT/f'.work/servers/{mc}/{loader}/{parent}'
        assert hashlib.sha256((server/'mods/cuboidplots.jar').read_bytes()).hexdigest()==checksum,'Production server tested a different jar'
        report=(server/'world/cuboidplots-acceptance.txt').read_text()
        assert 'FIRST RUN PASS 50 assertions' in report and 'RESTART PASS 7 assertions' in report
        protocol=json.loads((server/'protocol-result.json').read_text())
        assert protocol['status']=='PASS'
        startup=(server/'protocol-server.log').read_text(errors='replace')
        assert 'Done (' in startup and 'All dimensions are saved' in startup and 'InvalidMixinException' not in startup
        evidence=ROOT/f'reports/{mc}/{loader}/{parent}';evidence.mkdir(parents=True,exist_ok=True)
        (evidence/'acceptance.txt').write_text(report)
        for original,dest in [('dependency-lock.json','dependencies.json'),('protocol-result.json','protocol-result.json'),('protocol-server.log','production-startup.log'),('integration.log','restart.log')]:shutil.copy2(server/original,evidence/dest)
        first=max((path for path in server.glob('integration*.log') if 'PASS direct parent ownership transfer' in path.read_text(errors='replace')),key=lambda path:path.stat().st_mtime)
        shutil.copy2(first,evidence/'behaviour.log')
        verification[parent]={'testHostSha256':(server/'testhost.sha256').read_text().strip(),'behaviour':'50 assertions passed','restart':'7 assertions passed','productionStartup':'passed','protocolClients':f"{len(protocol['checks'])} checks passed",'manualClient':'not verified'}
    folder=ROOT/f'artifacts/{mc}/{loader}';folder.mkdir(parents=True,exist_ok=True)
    target=folder/f'cuboidplots-{mc}-{loader}-0.1.0.jar';shutil.copy2(built,target)
    target.with_suffix('.jar.sha256').write_text(checksum+'  '+target.name+'\n')
    manifest['artifacts'].append({'file':target.relative_to(ROOT).as_posix(),'minecraft':mc,'loader':loader,'sha256':checksum,'sourceCommit':commit,'java':17,'bundled':['portable core (Java 8)','MixinExtras 0.4.1'],'verification':verification})
manifest_path.write_text(json.dumps(manifest,indent=2))
print('Packaged verified',mc,'jars from',commit)
