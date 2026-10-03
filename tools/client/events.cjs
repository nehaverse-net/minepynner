const assert = require('node:assert/strict');
const mineflayer = require('mineflayer');
const {Vec3} = require('vec3');
const bot = mineflayer.createBot({host:'127.0.0.1',port:25591,username:'PynnerTest',auth:'offline',version:'1.21.11'});
const sleep = ms => new Promise(resolve=>setTimeout(resolve,ms));
const timeout = setTimeout(()=>{console.error('Event probe timed out');bot.quit();process.exit(1)},35000);
bot.on('message',m=>console.log(m.toString()));
bot.on('error',e=>{console.error(e);process.exitCode=1});
bot.once('spawn',async()=>{
  try {
    await sleep(1000);
    bot.chat('/gamemode creative');
    bot.chat('/tp @s 0 -60 0');
    await sleep(500);
    bot.chat('Pynner event probe');
    bot.setControlState('forward',true); await sleep(300); bot.clearControlStates();
    bot.chat('/tp @s 0 -60 0'); await sleep(300);
    bot.chat('/give @s stone 3'); await sleep(500);
    const stone = bot.inventory.items().find(item=>item.name==='stone'); assert(stone);
    await bot.equip(stone,'hand');
    const floor = bot.blockAt(new Vec3(1,-61,0)); assert(floor);
    await bot.placeBlock(floor,new Vec3(0,1,0)); await sleep(500);
    const placed = bot.blockAt(new Vec3(1,-60,0)); assert.equal(placed.name,'stone');
    await bot.dig(placed); await sleep(500);
    await bot.clickWindow(stone.slot,0,0); await bot.clickWindow(stone.slot,0,0);
    bot.chat('/summon arrow 0 -58 0 {Motion:[0.0d,-1.0d,0.0d]}'); await sleep(1000);
    bot.chat('/pynner give smoke_sword'); await sleep(500);
    const sword = bot.inventory.items().find(item=>item.name==='iron_sword'); assert(sword);
    await bot.equip(sword,'hand'); await sleep(250);
    bot.activateItem(); await sleep(200); bot.deactivateItem(); bot.swingArm();
    for(let index=0;index<2;index++) {
      const before = new Set(Object.keys(bot.entities));
      bot.chat('/pynner spawn probe_mob'); await sleep(450);
      const mob = Object.values(bot.entities).find(entity=>!before.has(String(entity.id))&&entity.name==='zombie');
      assert(mob,'Spawned probe mob must be visible');
      await bot.activateEntity(mob); await sleep(100);
      bot.attack(mob); await sleep(550);
    }
    console.log('PYNNER_EVENT_CLIENT_OK');
    clearTimeout(timeout); bot.quit();
  } catch(e) { console.error(e); clearTimeout(timeout); bot.quit(); process.exitCode=1; }
});
