"""Run a private dedicated server, capture output, stop cleanly after tests or startup."""
import argparse, pathlib, subprocess, threading, time, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
ROOT=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('parent',choices=['ftb','opac']);parser.add_argument('--mc',choices=['1.18.2','1.20.1'],default='1.20.1');parser.add_argument('--loader',choices=['fabric','forge'],default='fabric');parser.add_argument('--startup-only',action='store_true');args=parser.parse_args()
directory=ROOT/f'.work/servers/{args.mc}/{args.loader}/{args.parent}'
java=next((ROOT/'.work/toolchains/jdk17').glob('*/bin/java.exe'))
log=directory/('startup-only.log' if args.startup_only else 'integration.log')
if log.exists():
    import shutil
    shutil.copy2(log, directory/(log.stem+'-'+str(time.time_ns())+'.log'))
command=[str(java),'-Xms256M','-Xmx1536M',f'-Djava.io.tmpdir={ROOT / ".work/tmp"}']
if not args.startup_only:command.append('-Dcuboidplots.test=true')
forge_version='1.20.1-47.4.10' if args.mc=='1.20.1' else '1.18.2-40.3.12'
command+=['-jar','fabric-server-launch.jar','nogui'] if args.loader=='fabric' else [f'@libraries/net/minecraftforge/forge/{forge_version}/win_args.txt','nogui']
process=subprocess.Popen(command,cwd=directory,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
(directory/'test-server.pid').write_text(str(process.pid))
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
        if 'This crash report has been saved to:' in line:
            # Minecraft can hang in shutdown after a failed Mixin left the world half initialized.
            threading.Timer(15,lambda: process.kill() if process.poll() is None else None).start()
        if args.startup_only and 'Done (' in line:
            done=True;process.stdin.write('stop\n');process.stdin.flush()
process.wait();timer.cancel()
print('Server exit',process.returncode,'elapsed',round(time.monotonic()-start,1),'seconds; log',log,flush=True)
if process.returncode or args.startup_only and not done:raise SystemExit(1)
if not args.startup_only:
    report=directory/'world/cuboidplots-acceptance.txt'
    if not report.exists() or 'FAIL' in report.read_text():raise SystemExit('Behaviour verification did not pass; inspect the captured server log')
