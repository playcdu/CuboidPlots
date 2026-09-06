"""One-time mechanical adapter port; portable core files are deliberately untouched."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
dest=ROOT/'.work/ports/1.18.2'
replacements={
    'net.minecraft.core.registries.BuiltInRegistries':'net.minecraft.core.Registry',
    'net.minecraft.core.registries.Registries':'net.minecraft.core.Registry',
    'net.minecraft.core.registries.*':'net.minecraft.core.Registry',
    'BuiltInRegistries.':'Registry.', 'Registries.':'Registry.',
    'BlockPos.containing(':'new BlockPos(', '.serverLevel()':'.getLevel()', '.level()':'.level',
    'net.minecraft.network.chat.Component.literal(':'new net.minecraft.network.chat.TextComponent(',
    'Component.literal(':'new net.minecraft.network.chat.TextComponent(',
    'dev.ftb.mods.ftbchunks.api.FTBChunksAPI':'dev.ftb.mods.ftbchunks.FTBChunksAPI',
    'dev.ftb.mods.ftbchunks.api.Protection':'dev.ftb.mods.ftbchunks.Protection',
    'dev.ftb.mods.ftbteams.api.FTBTeamsAPI':'dev.ftb.mods.ftbteams.FTBTeamsAPI',
    'FTBChunksAPI.api()':'FTBChunksAPI', 'FTBTeamsAPI.api()':'FTBTeamsAPI',
    'ClaimedChunkManagerImpl':'ClaimedChunkManager', 'shouldPreventInteraction':'protect',
    'ChunkTeamDataImpl':'FTBChunksTeamData', 'ClaimedChunkImpl':'ClaimedChunk',
    'TeamManagerImpl':'TeamManager', '.getOrCreateData(' : '.getData(',
}
for folder in ['platform','testhost','forge/src','fabric/src']:
    for path in (dest/folder).rglob('*.java'):
        text=path.read_text()
        for old,new in replacements.items():text=text.replace(old,new)
        if path.name=='PlayerGameModeMixin.java':
            text=text.replace('int height,int sequence,Operation<Void>','int height,Operation<Void>').replace('original.call(pos,action,face,height,sequence)','original.call(pos,action,face,height)')
        if path.name=='ServerAcceptance.java':text=text.replace('level.getMaxBuildHeight(),0);','level.getMaxBuildHeight());')
        path.write_text(text)
for path in [dest/'gradle.properties',dest/'fabric/build.gradle',dest/'fabric/src/main/resources/fabric.mod.json',dest/'forge/build.gradle',dest/'forge/src/main/resources/META-INF/mods.toml']:
    text=path.read_text().replace('1.20.1','1.18.2').replace('1.20.2','1.19')
    for old,new in {'2001.3.8':'1802.3.19-build.362','2001.3.2':'1802.2.11-build.152','2001.2.13':'1802.3.12-build.726','2001.1.7':'1802.1.11-build.71','9.2.14':'4.12.94','11.1.136':'6.5.116','0.92.6+1.18.2':'0.77.0+1.18.2','47.4.10':'40.3.12','[47,)':'[40,)','[40.3.12,48)':'[40.3.12,41)'}.items():text=text.replace(old,new)
    path.write_text(text)
print('Applied explicit 1.18.2 mapping and dependency changes; compile next to audit remaining API differences.')
