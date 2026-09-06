"""Compare committed portable-core trees across target branches; never normalize differences away."""
import json, subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
versions=['1.16.5','1.18.2','1.20.1','1.21.1','26.1.2']
trees={}
for version in versions:
    result=subprocess.run(['git','rev-parse',f'mc/{version}:core/src'],cwd=root,text=True,capture_output=True)
    trees[version]=result.stdout.strip() if result.returncode==0 else None
present={value for value in trees.values() if value is not None}
report={'coreTrees':trees,'allBranchesPresent':all(trees.values()),'allPresentTreesIdentical':len(present)==1}
print(json.dumps(report,indent=2))
if not report['allBranchesPresent'] or not report['allPresentTreesIdentical']:raise SystemExit(1)
