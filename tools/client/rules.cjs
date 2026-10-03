const assert=require('node:assert/strict');
const mineflayer=require('mineflayer');
const {Vec3}=require('vec3');
const bot=mineflayer.createBot({host:'127.0.0.1',port:25591,username:'PynnerTest',auth:'offline',version:'1.21.11'});
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const messages=[];
bot.on('message',m=>{messages.push(m.toString());console.log(m.toString())});
bot.on('error',e=>{console.error(e);process.exitCode=1});
const timeout=setTimeout(()=>{bot.quit();process.exit(1)},15000);
bot.once('spawn',async()=>{
  try {
    await sleep(700);bot.chat('/gamemode creative');bot.chat('/tp @s 0 -60 0');
    bot.chat('/setblock 1 -60 0 diamond_block');await sleep(600);
    await Promise.race([bot.dig(bot.blockAt(new Vec3(1,-60,0))),sleep(1200)]);
    await sleep(600);
    assert(messages.some(m=>m.includes('Diamond blocks are protected')));
    // Mineflayer may retain its local creative-dig prediction; ask the server for authority.
    bot.chat('/execute if block 1 -60 0 diamond_block run tellraw @s {"text":"PYNNER_RULE_SERVER_OK"}');
    await sleep(500);
    assert(messages.some(m=>m.includes('PYNNER_RULE_SERVER_OK')));
    bot.chat('/setblock 1 -60 0 air');
    console.log('PYNNER_RULE_SMOKE_OK');clearTimeout(timeout);bot.quit();
  } catch(e) {console.error(e);clearTimeout(timeout);bot.quit();process.exitCode=1;}
});
