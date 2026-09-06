// A vanilla-protocol client with no Fabric/Forge/addon installation. This is not visual client QA.
const path = require('node:path');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const {once} = require('node:events');
const protocol = require('../.work/protocol/node_modules/minecraft-protocol');
const version = process.argv[4] || '1.20.1';
const data = require('../.work/protocol/node_modules/minecraft-data')(version);
const port = Number(process.argv[2]);
const resultPath = process.argv[3];
const clients=[];
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function waitFor(predicate,label){
  const end=Date.now()+15000;
  while(!predicate()){for(const client of clients)if(client.failure)throw client.failure;if(Date.now()>end)throw new Error('Timed out: '+label);await delay(50);}
}
function connect(name){
  const client=protocol.createClient({host:'127.0.0.1',port,username:name,version,auth:'offline'});
  clients.push(client);client.previewParticles=0;client.menu=null;client.inventory=null;client.messages=[];
  client.on('error',error=>{client.failure=error;});
  client.on('disconnect',packet=>{client.failure=new Error(JSON.stringify(packet));});
  client.on('position',packet=>{client.position=packet;client.write('teleport_confirm',{teleportId:packet.teleportId});});
  client.on('open_window',packet=>{client.menu=packet;});
  client.on('window_items',packet=>{client.inventory=packet;});
  client.on('world_particles',packet=>{if(packet.particleId===data.particlesByName.end_rod.id)client.previewParticles++;});
  client.on('system_chat',packet=>client.messages.push(packet.content));
  client.on('chat',packet=>client.messages.push(packet.message));
  return client;
}
function command(client,text){
  if(version==='1.18.2'){client.write('chat',{message:'/'+text});return;}
  client.write('chat_command',{command:text,timestamp:BigInt(Date.now()),salt:0n,argumentSignatures:[],messageCount:0,acknowledged:Buffer.alloc(3)});
}
(async()=>{
  const observer=connect('CuboidWatch');
  await waitFor(()=>observer.position,'observer finishes vanilla login');
  const selector=connect('CuboidSurvey');
  await waitFor(()=>observer.position&&selector.position,'two clients finish vanilla login');
  await delay(800);
  command(selector,'cuboid tool');
  await waitFor(()=>selector.messages.some(x=>String(x).toLowerCase().includes('surveyor')),'surveyor command reply');
  const p=selector.position,x=Math.floor(p.x),y=Math.floor(p.y),z=Math.floor(p.z);
  command(selector,`cuboid pos1 ${x} ${y} ${z}`);await delay(200);
  command(selector,`cuboid pos2 ${x+4} ${y+4} ${z+4}`);await delay(200);
  command(selector,'cuboid preview');
  await waitFor(()=>selector.previewParticles>0,'selector receives vanilla outline particles');
  await delay(1200);
  assert.equal(observer.previewParticles,0,'observer must not receive private preview');
  command(selector,'cuboid');
  await waitFor(()=>selector.menu&&selector.inventory&&selector.inventory.windowId===selector.menu.windowId,'vanilla chest menu and contents');
  assert.match(selector.menu.windowTitle,/Cuboid Plots/);
  assert.equal(selector.inventory.items.length,90,'54 menu slots and 36 player inventory slots');
  const menu=selector.menu,inventory=selector.inventory;
  selector.write('window_click',{windowId:menu.windowId,stateId:inventory.stateId,slot:0,mouseButton:0,mode:1,changedSlots:[],cursorItem:{present:false}});
  await delay(500);
  assert.equal(selector.inventory.carriedItem.present,false,'shift-click does not steal menu decoration');
  for(const client of clients)if(client.failure)throw client.failure;
  const result={status:'PASS',version,port,clients:'two unmodified-protocol non-operator clients',checks:['login without addon','surveyor command','54-slot chest menu','private particle delivery','no preview packets to observer','shift-click retains empty cursor'],previewPackets:selector.previewParticles,manualVisualVerification:false};
  fs.writeFileSync(resultPath,JSON.stringify(result,null,2));console.log(JSON.stringify(result));
})().catch(error=>{console.error(error);fs.writeFileSync(resultPath,JSON.stringify({status:'FAIL',error:String(error)}));process.exitCode=1;}).finally(()=>{for(const client of clients)client.end();setTimeout(()=>process.exit(process.exitCode||0),500);});
