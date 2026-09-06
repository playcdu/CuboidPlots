"""Package the dependency-range fix after the real 0.5.4 conflict regression passes."""
import copy,hashlib,json,pathlib,shutil,subprocess,zipfile
from prepare_servers import ROOT
manifest_file=ROOT/'artifacts/manifest.json';manifest=json.loads(manifest_file.read_text())
for mc,source in [('1.20.1',ROOT),('1.18.2',ROOT/'.work/ports/1.18.2')]:
    old=next(entry for entry in manifest['artifacts'] if entry['minecraft']==mc and entry['loader']=='forge' and entry['file'].endswith('-0.1.0.jar'))
    built=source/f'forge/build/libs/cuboidplots-{mc}-forge-0.1.1-all.jar'
    previous=ROOT/old['file']
    with zipfile.ZipFile(previous) as before,zipfile.ZipFile(built) as after:
        class_bytes=lambda jar:{name:hashlib.sha256(jar.read(name)).hexdigest() for name in jar.namelist() if name.endswith('.class')}
        assert class_bytes(before)==class_bytes(after),'Hotfix must not silently change runtime classes'
        dependency=json.loads(after.read('META-INF/jarjar/metadata.json'))['jars'][0]
        assert dependency['version']=={'range':'[0.4.1,)','artifactVersion':'0.4.1'}
    verify={}
    for parent in ['ftb','opac']:
        server=ROOT/f'.work/servers/{mc}/forge/{parent}'
        result=json.loads((server/'protocol-result.json').read_text())
        log=(server/'protocol-server.log').read_text(errors='replace')
        assert result['status']=='PASS' and 'MixinExtrasServiceImpl(version=0.5.4)' in log and 'Done (' in log and 'All dimensions are saved' in log
        assert hashlib.sha256((server/'mods/cuboidplots.jar').read_bytes()).digest()==hashlib.sha256(built.read_bytes()).digest()
        evidence=ROOT/f'reports/{mc}/forge/{parent}/hotfix-0.1.1';evidence.mkdir(parents=True,exist_ok=True)
        for name in ['protocol-result.json','protocol-server.log','protocol-client.log']:shutil.copy2(server/name,evidence/name)
        shutil.copy2(ROOT/'.work/compat-fixture/dependency.json',evidence/'compatibility-fixture.json')
        verify[parent]={'productionStartup':'passed with MixinExtras 0.5.4','protocolClients':'6 checks passed with MixinExtras 0.5.4','runtimeClasses':'byte-identical to tested 0.1.0; its 50 behaviour, 7 restart and FTB Ranks results retained as baseline evidence','manualClient':'not verified','fullUserModpack':'not available; reported dependency constraint reproduced by private fixture'}
    target=ROOT/f'artifacts/{mc}/forge/cuboidplots-{mc}-forge-0.1.1.jar';shutil.copy2(built,target)
    checksum=hashlib.sha256(target.read_bytes()).hexdigest();target.with_suffix('.jar.sha256').write_text(checksum+'  '+target.name+'\n')
    entry={'file':target.relative_to(ROOT).as_posix(),'minecraft':mc,'loader':'forge','version':'0.1.1','sha256':checksum,'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=source,text=True).strip(),'java':17,'bundled':['portable core (Java 8)','MixinExtras 0.4.1 with accepted runtime range [0.4.1,)'],'verification':verify}
    # Keep superseded binaries for provenance, outside the current installable-jar folders.
    archive=ROOT/f'artifacts/superseded/{mc}/forge'/previous.name;archive.parent.mkdir(parents=True,exist_ok=True)
    assert previous.resolve().is_relative_to((ROOT/'artifacts').resolve()) and archive.resolve().is_relative_to((ROOT/'artifacts').resolve())
    shutil.move(previous,archive);shutil.move(previous.with_suffix('.jar.sha256'),archive.with_suffix('.jar.sha256'))
    old['file']=archive.relative_to(ROOT).as_posix();old['status']='superseded';old['supersededBy']=entry['file']
    manifest['artifacts'].append(entry)
    print(entry['file'],checksum,flush=True)
manifest_file.write_text(json.dumps(manifest,indent=2))
negative=ROOT/'.work/servers/1.20.1/forge/ftb/startup-only.log'
assert 'VERSION_RESOLUTION_FAILED' in negative.read_text(errors='replace')
shutil.copy2(negative,ROOT/'reports/1.20.1/forge/mixinextras-negative-control.log')
