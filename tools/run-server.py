"""Run a private dedicated server, capture output, stop cleanly after tests or startup."""
import argparse, pathlib, subprocess, threading, time
ROOT=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('parent',choices=['ftb','opac']);parser.add_argument('--startup-only',action='store_true');args=parser.parse_args()
directory=ROOT/f'.work/servers/1.20.1/fabric/{args.parent}'
java=next((ROOT/'.work/toolchains/jdk17').glob('*/bin/java.exe'))
log=directory/('startup-only.log' if args.startup_only else 'integration.log')
command=[str(java),'-Xms256M','-Xmx1536M',f'-Djava.io.tmpdir={ROOT / ".work/tmp"}']
if not args.startup_only:command.append('-Dcuboidplots.test=true')
command+=['-jar','fabric-server-launch.jar','nogui']
process=subprocess.Popen(command,cwd=directory,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
start=time.monotonic();done=False
def timeout():
    if process.poll() is None:
        print('Server timeout; asking it to stop',flush=True)
        try:process.stdin.write('stop\n');process.stdin.flush()
        except OSError:pass
        try:process.wait(30)
        except subprocess.TimeoutExpired:process.kill()
timer=threading.Timer(360,timeout);timer.start()
with log.open('w',encoding='utf-8') as out:
    for line in process.stdout:
        out.write(line);out.flush()
        if any(marker in line for marker in ['ERROR','Exception','CuboidPlots','Done (','test result','FAIL','Mixin']):print(line.rstrip(),flush=True)
        if args.startup_only and 'Done (' in line:
            done=True;process.stdin.write('stop\n');process.stdin.flush()
process.wait();timer.cancel()
print('Server exit',process.returncode,'elapsed',round(time.monotonic()-start,1),'seconds; log',log,flush=True)
if process.returncode or args.startup_only and not done:raise SystemExit(1)
