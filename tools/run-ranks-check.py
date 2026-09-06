"""Test live FTB Ranks on private servers, restoring configuration after both stages."""
import argparse, json, pathlib, subprocess, threading
from prepare_servers import ROOT
parser=argparse.ArgumentParser()
parser.add_argument('--mc',choices=['1.18.2','1.20.1'],default='1.20.1')
parser.add_argument('--loader',choices=['fabric','forge'],default='fabric')
args=parser.parse_args()
directory=ROOT/f'.work/servers/{args.mc}/{args.loader}/ftb'
config=directory/'world/cuboidplots/config.properties'
ranks=directory/'world/serverconfig/ftbranks/ranks.snbt'
backups={file:file.read_bytes() for file in [config,ranks]}
java=next((ROOT/'.work/toolchains/jdk17').glob('*/bin/java.exe'))
forge='1.20.1-47.4.10' if args.mc=='1.20.1' else '1.18.2-40.3.12'
port=(25575 if args.loader=='fabric' else 25577)+(10 if args.mc=='1.18.2' else 0)
command=[str(java),'-Xmx1536M',f'-Djava.io.tmpdir={ROOT / ".work/tmp"}']+(['-jar','fabric-server-launch.jar'] if args.loader=='fabric' else [f'@libraries/net/minecraftforge/forge/{forge}/win_args.txt'])+['nogui']
try:
    config.write_text('provider=ftbranks\ndefault.max_regions=32\ndefault.max_volume=1000000\n')
    for phase in ['behaviour','restart']:
        result=directory/f'ranks-{phase}-result.json';result.unlink(missing_ok=True)
        server=subprocess.Popen(command,cwd=directory,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
        def clients():
            try:
                child=subprocess.Popen(['node',str(ROOT/'tools/ranks-check.cjs'),str(directory),args.mc,str(port),phase],cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
                with (directory/f'ranks-{phase}-client.log').open('w') as out:
                    for line in child.stdout:
                        out.write(line);out.flush()
                        if line.startswith('CONSOLE '):server.stdin.write(line[8:]);server.stdin.flush()
                child.wait()
            finally:
                if server.poll() is None:server.stdin.write('stop\n');server.stdin.flush()
        timer=threading.Timer(240,lambda:server.kill() if server.poll() is None else None);timer.start()
        with (directory/f'ranks-{phase}-server.log').open('w',encoding='utf-8') as out:
            for line in server.stdout:
                out.write(line);out.flush()
                if 'Done (' in line:threading.Thread(target=clients).start()
        server.wait();timer.cancel()
        if server.returncode or not result.exists() or json.loads(result.read_text())['status']!='PASS':raise SystemExit(f'FAIL {phase}: inspect {directory}')
        print(result.read_text(),flush=True)
finally:
    for file,contents in backups.items():file.write_bytes(contents)
