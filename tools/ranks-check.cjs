// Exercise the actual FTB Ranks API through an ordinary player's addon commands.
const fs=require('node:fs');
const path=require('node:path');
const protocol=require('../.work/protocol/node_modules/minecraft-protocol');
const [directory,version,portText,phase]=process.argv.slice(2);
const checks=[];const messages=[];let failure;let position;
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const client=protocol.createClient({host:'127.0.0.1',port:Number(portText),username:'CuboidRanks',version,auth:'offline'});
client.on('error',error=>failure=error);
client.on('disconnect',packet=>failure=new Error(JSON.stringify(packet)));
client.on('position',packet=>{position=packet;client.write('teleport_confirm',{teleportId:packet.teleportId});});
client.on('system_chat',packet=>messages.push(packet.content));
client.on('chat',packet=>messages.push(packet.message));
async function until(predicate,label){const end=Date.now()+20000;while(!predicate()){if(failure)throw failure;if(Date.now()>end)throw new Error(label+'; messages='+JSON.stringify(messages));await delay(50);}}
async function command(text,expected){
  const index=messages.length;
  if(version==='1.18.2')client.write('chat',{message:'/'+text});
  else client.write('chat_command',{command:text,timestamp:BigInt(Date.now()),salt:0n,argumentSignatures:[],messageCount:0,acknowledged:Buffer.alloc(3)});
  if(expected)await until(()=>messages.slice(index).some(message=>String(message).includes(expected)),text+' expected '+expected);
  else await delay(300);
}
async function consoleCommand(text){console.log('CONSOLE '+text);await delay(800);}
async function ranks(count,volume,vip=false){
  const nodes=count===null?'':`cuboidplots.max_regions: ${count}\ncuboidplots.max_volume: ${volume}`;
  fs.writeFileSync(path.join(directory,'world/serverconfig/ftbranks/ranks.snbt'),`{member:{name:"Member",power:1,condition:"always_active",${nodes}}${vip?',vip:{name:"VIP",power:50,cuboidplots.max_regions:2,cuboidplots.max_volume:8}':''}}`);
  await consoleCommand('ftbranks reload');
}
async function selection(x,y,z,width=1,height=1,depth=1){await command(`cuboid pos1 ${x} ${y} ${z}`,'Corner 1');await command(`cuboid pos2 ${x+width-1} ${y+height-1} ${z+depth-1}`,'Corner 2');}
function pass(label){checks.push(label);console.log('PASS '+label);}
(async()=>{
  await until(()=>position,'vanilla login');await delay(500);
  if(phase==='restart'){
    await command('cuboid quota','Regions 1 / 1, blocks 8 / 8');pass('rank limits and charged usage survive actual restart');
    await command('cuboid info ranks_probe','ranks_probe:');pass('created cuboid persists');
    await command('cuboid delete ranks_probe','quota released');
    await command('cuboid quota','Regions 0 / 1, blocks 0 / 8');pass('deletion releases restarted quota');
  }else{
    await consoleCommand('tp CuboidRanks -159 120 -159');
    await until(()=>position.x< -150,'teleport to isolated test chunk');
    await command('ftbchunks claim');
    // Remove an interrupted previous probe before measuring this private fixture.
    await command('cuboid delete ranks_probe');
    await ranks(1,8);await command('cuboid quota','Regions 0 / 1, blocks 0 / 8');pass('numeric rank nodes override configuration fallback');
    await selection(-159,120,-159,2,2,2);await command('cuboid create ranks_probe','Created ranks_probe');
    await command('cuboid quota','Regions 1 / 1, blocks 8 / 8');pass('exact count and inclusive volume limits permit creation');
    await ranks(1,100);await selection(-154,120,-154);await command('cuboid create ranks_extra','Region count limit 1 exceeded by 1');pass('count limit enforced independently');
    await ranks(3,8);await command('cuboid create ranks_extra','Block volume limit 8 exceeded by 1');pass('volume limit enforced independently');
    await selection(-159,120,-159,2,2,3);await command('cuboid resize ranks_probe','Block volume limit 8 exceeded by 4');
    await command('cuboid quota','Regions 1 / 3, blocks 8 / 8');pass('failed resize preserves charged usage');
    await ranks(0,0);await command('cuboid info ranks_probe','ranks_probe:');await selection(-159,120,-159);await command('cuboid resize ranks_probe','Updated bounds');
    await command('cuboid quota','Regions 1 / 0, blocks 1 / 0');pass('rank downgrade preserves region and permits volume reduction');
    await selection(-154,120,-154);await command('cuboid create ranks_extra','Region count limit 0 exceeded by 2');pass('zero rank limits reject increases');
    await command('cuboid delete ranks_probe','quota released');pass('deletion permitted while over quota');
    await ranks(2.5,100);await command('cuboid create ranks_extra','Permission provider failed');pass('fractional provider value fails closed');
    await ranks(null,null);await command('cuboid quota','Regions 0 / 32, blocks 0 / 1000000');pass('missing nodes use configured fallback');
    await ranks(-1,-1);await command('cuboid quota','Regions 0 / unlimited, blocks 0 / unlimited');pass('explicit unlimited rank values');
    await ranks(0,0,true);await consoleCommand('ftbranks add CuboidRanks vip');await command('cuboid quota','Regions 0 / 2, blocks 0 / 8');pass('live rank membership addition changes limits');
    await consoleCommand('ftbranks remove CuboidRanks vip');await command('cuboid quota','Regions 0 / 0, blocks 0 / 0');pass('live rank membership removal changes limits');
    await ranks(1,8);await selection(-159,120,-159,2,2,2);await command('cuboid create ranks_probe','Created ranks_probe');
  }
  fs.writeFileSync(path.join(directory,`ranks-${phase}-result.json`),JSON.stringify({status:'PASS',version,checks,manualClient:false},null,2));
})().catch(error=>{console.error(error);fs.writeFileSync(path.join(directory,`ranks-${phase}-result.json`),JSON.stringify({status:'FAIL',version,checks,error:String(error)}));process.exitCode=1;}).finally(()=>{client.end();setTimeout(()=>process.exit(process.exitCode||0),500);});
