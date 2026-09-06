"""Start an isolated dedicated server, run vanilla-protocol clients, and stop it."""
import argparse, pathlib, subprocess, threading
from prepare_servers import ROOT
parser=argparse.ArgumentParser();parser.add_argument('parent',choices=['ftb','opac']);parser.add_argument('--mc',choices=['1.18.2','1.20.1'],default='1.20.1');parser.add_argument('--loader',choices=['fabric','forge'],default='fabric');args=parser.parse_args()
directory=ROOT/f'.work/servers/{args.mc}/{args.loader}/{args.parent}'
port={('fabric','ftb'):25575,('fabric','opac'):25576,('forge','ftb'):25577,('forge','opac'):25578}[args.loader,args.parent]
if args.mc=='1.18.2':port+=10
java=next((ROOT/'.work/toolchains/jdk17').glob('*/bin/java.exe'))
forge='1.20.1-47.4.10' if args.mc=='1.20.1' else '1.18.2-40.3.12'
command=[str(java),'-Xmx1536M',f'-Djava.io.tmpdir={ROOT / ".work/tmp"}']+(['-jar','fabric-server-launch.jar'] if args.loader=='fabric' else [f'@libraries/net/minecraftforge/forge/{forge}/win_args.txt'])+['nogui']
server=subprocess.Popen(command,cwd=directory,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
result=directory/'protocol-result.json';result.unlink(missing_ok=True)
def clients():
    try:
        with (directory/'protocol-client.log').open('w') as log:
            subprocess.run(['node',str(ROOT/'tools/protocol-check.cjs'),str(port),str(result),args.mc],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,timeout=90,check=True)
    finally:server.stdin.write('stop\n');server.stdin.flush()
timer=threading.Timer(240,lambda:server.kill() if server.poll() is None else None);timer.start()
with (directory/'protocol-server.log').open('w',encoding='utf-8') as log:
    for line in server.stdout:
        log.write(line);log.flush()
        if 'Done (' in line:threading.Thread(target=clients).start()
server.wait();timer.cancel()
print(result.read_text() if result.exists() else 'FAIL: no protocol result')
if server.returncode or not result.exists() or '"PASS"' not in result.read_text():raise SystemExit(1)
