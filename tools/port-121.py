"""Initial 1.21.1 dependency setup. Platform API changes are made explicitly afterward."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'.work/ports/1.21.1'
for name in ['gradle.properties','fabric/build.gradle','fabric/src/main/resources/fabric.mod.json']:
    file=root/name
    text=file.read_text()
    for old,new in {'1.20.1':'1.21.1','2001.3.8':'2101.1.22','2001.3.2':'2101.1.11','2001.2.13':'2101.1.35','2001.1.7':'2101.1.4','9.2.14':'13.0.11','11.1.136':'15.0.140','0.92.6':'0.116.17','VERSION_17':'VERSION_21','release = 17':'release = 21','>=17':'>=21'}.items():text=text.replace(old,new)
    file.write_text(text)
file=root/'platform/src/main/resources/cuboidplots.mixins.json'
file.write_text(file.read_text().replace('JAVA_17','JAVA_21'))
