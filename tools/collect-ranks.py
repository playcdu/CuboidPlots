"""Attach real FTB Ranks verification evidence to already packaged addon jars."""
import json,shutil
from prepare_servers import ROOT
file=ROOT/'artifacts/manifest.json';manifest=json.loads(file.read_text())
for entry in manifest['artifacts']:
    mc,loader=entry['minecraft'],entry['loader']
    server=ROOT/f'.work/servers/{mc}/{loader}/ftb';evidence=ROOT/f'reports/{mc}/{loader}/ftb'
    counts=[]
    for phase in ['behaviour','restart']:
        result=json.loads((server/f'ranks-{phase}-result.json').read_text())
        assert result['status']=='PASS',f'Ranks did not pass: {mc}/{loader}/{phase}'
        counts.append(len(result['checks']))
        for suffix in ['result.json','client.log','server.log']:
            name=f'ranks-{phase}-{suffix}';shutil.copy2(server/name,evidence/name)
    entry['verification']['ftb']['ftbRanks']=f'{counts[0]} live checks and {counts[1]} restart checks passed'
file.write_text(json.dumps(manifest,indent=2))
print('Collected FTB Ranks evidence for',len(manifest['artifacts']),'artifacts')
