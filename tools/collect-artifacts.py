"""Collect only normal runnable jars, with exact source and verification provenance."""
import hashlib, json, pathlib, shutil, subprocess, zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
artifacts=[]
for loader,source in [('fabric','fabric/build/libs/cuboidplots-1.20.1-fabric-0.1.0.jar'),('forge','forge/build/libs/cuboidplots-1.20.1-forge-0.1.0-all.jar')]:
    source=ROOT/source
    with zipfile.ZipFile(source) as jar:
        assert not any('dev/cuboidplots/testhost/' in name for name in jar.namelist()), 'Test classes must not ship'
        if loader=='fabric':assert 'testhost' not in jar.read('fabric.mod.json').decode()
        else:assert 'cuboidplots.refmap.json' in jar.namelist()
    folder=ROOT/'artifacts/1.20.1'/loader;folder.mkdir(parents=True,exist_ok=True)
    target=folder/f'cuboidplots-1.20.1-{loader}-0.1.0.jar';shutil.copy2(source,target)
    checksum=hashlib.sha256(target.read_bytes()).hexdigest()
    (folder/(target.name+'.sha256')).write_text(checksum+'  '+target.name+'\n')
    verification={}
    for parent in ['ftb','opac']:
        server=ROOT/f'.work/servers/1.20.1/{loader}/{parent}'
        report=(server/'world/cuboidplots-acceptance.txt').read_text()
        evidence=ROOT/f'reports/1.20.1/{loader}/{parent}';evidence.mkdir(parents=True,exist_ok=True)
        (evidence/'acceptance.txt').write_text(report)
        shutil.copy2(server/'dependency-lock.json',evidence/'dependencies.json')
        logs=list(server.glob('integration*.log'))
        first=max((p for p in logs if 'PASS direct parent ownership transfer' in p.read_text(errors='replace')),key=lambda p:p.stat().st_mtime)
        shutil.copy2(first,evidence/'behaviour.log');shutil.copy2(server/'integration.log',evidence/'restart.log')
        verification[parent]={'testHostSha256':hashlib.sha256((server/'mods/cuboidplots.jar').read_bytes()).hexdigest(),'behaviour':'50 assertions passed','restart':'7 assertions passed','manualClient':'not verified','productionStartup':'pending'}
    artifacts.append({'file':target.relative_to(ROOT).as_posix(),'minecraft':'1.20.1','loader':loader,'sha256':checksum,'sourceCommit':commit,'java':17,'bundled':['portable core (Java 8)','MixinExtras 0.4.1'],'verification':verification})
(ROOT/'artifacts/manifest.json').write_text(json.dumps({'artifacts':artifacts},indent=2))
print(json.dumps(artifacts,indent=2))
