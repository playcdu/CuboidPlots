"""Private regression fixture reproducing a pack mod's MixinExtras 0.5.4 requirement."""
import hashlib,io,json,zipfile
from prepare_servers import ROOT,get
url='https://repo.maven.apache.org/maven2/io/github/llamalad7/mixinextras-forge/0.5.4/mixinextras-forge-0.5.4.jar'
payload=get(url)
folder=ROOT/'.work/compat-fixture';folder.mkdir(parents=True,exist_ok=True)
metadata={'jars':[{'identifier':{'group':'io.github.llamalad7','artifact':'mixinextras-forge'},'version':{'range':'[0.5.4,)','artifactVersion':'0.5.4'},'path':'META-INF/jarjar/mixinextras-forge-0.5.4.jar','isObfuscated':False}]}
with zipfile.ZipFile(folder/'require-mixinextras-0.5.4.jar','w',zipfile.ZIP_DEFLATED) as jar:
    jar.writestr('META-INF/mods.toml','modLoader="lowcodefml"\nloaderVersion="[40,)"\nlicense="MIT"\n[[mods]]\nmodId="cuboidplots_compat_fixture"\nversion="1.0.0"\ndisplayName="CuboidPlots dependency regression fixture"\n')
    jar.writestr('META-INF/jarjar/metadata.json',json.dumps(metadata))
    jar.writestr('META-INF/jarjar/mixinextras-forge-0.5.4.jar',payload)
(folder/'dependency.json').write_text(json.dumps({'url':url,'sha256':hashlib.sha256(payload).hexdigest(),'requiredRange':'[0.5.4,)'},indent=2))
print('Created private JarJar compatibility fixture; not an installable addon artifact')
