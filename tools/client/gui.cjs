const assert = require('node:assert/strict');
const { once } = require('node:events');
const { Vec3 } = require('vec3');
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({host:'127.0.0.1',port:Number(process.argv[2]),username:'GuiTester',auth:'offline',version:'1.21.11'});
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const messages = [];
bot.on('message', message => { messages.push(message.toString()); console.log(message.toString()); });
const timer = setTimeout(() => { console.error('GUI probe timeout'); bot.quit(); process.exit(1); }, 55000);
(async () => {
  try {
    await once(bot, 'spawn');
    await sleep(1000);
    let opened = once(bot, 'windowOpen');
    bot.chat('/cher');
    await opened;
    assert.equal(bot.currentWindow.slots[2].name, 'sunflower');
    assert.equal(bot.currentWindow.slots[6].name, 'water_bucket');
    await bot.clickWindow(2, 0, 1); // Shift-click must not move a menu item.
    await sleep(400);
    assert(bot.currentWindow);
    assert.equal(bot.currentWindow.slots[2].name, 'sunflower');
    assert(!bot.inventory.items().some(item => item.name === 'sunflower'));
    let closed = once(bot, 'windowClose');
    await bot.clickWindow(6, 0, 0);
    await closed;
    await sleep(500);
    assert(bot.isRaining, 'Rain option must set rain');
    opened = once(bot, 'windowOpen'); bot.chat('/cher'); await opened;
    closed = once(bot, 'windowClose'); await bot.clickWindow(2, 0, 0); await closed;
    // The client fades rain strength out over several seconds.
    for (let index = 0; index < 60 && bot.isRaining; index++) await sleep(100);
    assert(!bot.isRaining, 'Clear option must stop rain');
    assert(messages.some(text => text.includes('晴れにしました')));
    assert(messages.some(text => text.includes('雨にしました')));
    // Selection constraints are both tab suggestions and server-side validation.
    const suggestions = await bot.tabComplete('/pynner_weather r');
    assert(suggestions.some(entry => entry.match === 'rain'), 'choices must supply tab suggestions');
    for (const [input, expected] of [
      ['/pynner_weather', '使い方:'], ['/pynner_weather snow', '天候は clear, rain, thunder'],
      ['/pynner_weather clear nope', '秒数は整数'], ['/pynner_weather clear 0', '秒数は 1〜86400'],
      ['/pynner_weather clear 86401', '秒数は 1〜86400'],
      ['/pynner_weather clear 10 extra', '引数が多すぎます'],
    ]) {
      const offset = messages.length; bot.chat(input); await sleep(300);
      assert(messages.slice(offset).some(text => text.includes(expected)), input + ' must show custom error');
    }
    bot.chat('/pynner_weather clear 10'); await sleep(400);
    assert(messages.some(text => text.includes('clear に変更しました（10秒）')));
    opened = once(bot, 'windowOpen'); bot.chat('/cherpages'); await opened;
    assert.equal(bot.currentWindow.slots[2].name, 'sunflower');
    let reopened = false;
    const observeOpen = () => { reopened = true; };
    bot.on('windowOpen', observeOpen);
    await bot.clickWindow(8, 0, 0); await sleep(400);
    assert.equal(bot.currentWindow.slots[2].name, 'water_bucket');
    assert(!reopened, 'update_gui must keep the existing window open');
    bot.removeListener('windowOpen', observeOpen);
    bot.chat('/guipatchprobe'); await sleep(500);
    assert(messages.some(text => text.includes('GUI_ATOMIC_REJECTED')));
    assert.equal(bot.currentWindow.slots[0], null, 'Invalid update must not partially apply slot 0');
    assert.equal(bot.currentWindow.slots[2], null, 'None must remove the menu item');
    assert.equal(bot.currentWindow.slots[4].name, 'stone');
    bot.chat('/guirestoreprobe'); await sleep(400);
    assert.equal(bot.currentWindow.slots[4], null, 'clear=True must remove unspecified slots');
    assert.equal(bot.currentWindow.slots[2].name, 'water_bucket');
    opened = once(bot, 'windowOpen'); await bot.clickWindow(6, 0, 0); await opened;
    assert.equal(bot.currentWindow.slots[2].name, 'lime_wool');
    assert.equal(bot.currentWindow.slots[6].name, 'red_wool');
    closed = once(bot, 'windowClose'); await bot.clickWindow(2, 0, 0); await closed;
    await sleep(400); assert(bot.isRaining, 'Confirmation must apply the chosen page');
    // An old view ID must not update the newly switched menu.
    opened = once(bot, 'windowOpen'); bot.chat('/cherpages'); await opened;
    opened = once(bot, 'windowOpen'); await bot.clickWindow(6, 0, 0); await opened;
    bot.chat('/guistaleprobe'); await sleep(400);
    assert(messages.some(text => text.includes('GUI_STALE_REJECTED')));
    assert.equal(bot.currentWindow.slots[2].name, 'lime_wool');
    closed = once(bot, 'windowClose'); await bot.clickWindow(6, 0, 0); await closed;
    opened = once(bot, 'windowOpen'); bot.chat('/gridprobe'); await opened;
    assert.equal(bot.currentWindow.inventoryStart, 27, 'Three rows must infer 27 cells');
    assert.equal(bot.currentWindow.slots[13].name, 'diamond', 'Row 1 column 4 must be the center');
    assert.equal(bot.currentWindow.slots[26].name, 'barrier', 'Row 2 column 8 must be bottom-right');
    await bot.clickWindow(13, 0, 0); await sleep(400);
    assert(messages.some(text => text.includes('GRID_COORDINATES_OK')));
    bot.closeWindow(bot.currentWindow); await sleep(200);
    bot.chat('/tp @s 0 -60 0');
    bot.chat('/setblock 1 -60 0 chest'); await sleep(400);
    bot.chat('/item replace block 1 -60 0 container.5 with stone 40');
    bot.chat('/item replace block 1 -60 0 container.7 with stone 30');
    bot.chat('/item replace block 1 -60 0 container.10 with dirt 3');
    bot.chat('/item replace block 1 -60 0 container.12 with stone[custom_name="Named"] 5');
    await sleep(700);
    opened = once(bot, 'windowOpen'); await bot.activateBlock(bot.blockAt(new Vec3(1,-60,0))); await opened;
    const before = bot.currentWindow.slots.slice(0, 27).filter(Boolean);
    assert.equal(before.length, 4, 'Fixture must contain four stacks including named stone');
    await bot.clickWindow(0, 0, 0); await sleep(700);
    const after = bot.currentWindow.slots.slice(0, 27).filter(Boolean);
    assert.equal(after.reduce((n,item) => n + item.count, 0), 78, 'Sorting must preserve all items');
    assert.equal(after[0].name, 'dirt');
    assert.equal(after[0].count, 3);
    assert(after.some(item => item.name === 'stone' && item.count === 64));
    assert(after.some(item => item.name === 'stone' && item.count === 6));
    assert(after.some(item => item.name === 'stone' && item.count === 5), 'Named stack must stay separate');
    assert(messages.some(text => text.includes('チェストを整頓しました')));
    await bot.clickWindow(0, 0, 0); // Pick dirt up onto cursor.
    bot.chat('/sortprobe'); await sleep(500);
    assert(messages.some(text => text.includes('SORT_REJECTED')), 'API must reject sorting with occupied cursor');
    await bot.clickWindow(0, 0, 0); // Return dirt to its slot.
    const rejections = messages.filter(text => text.includes('SORT_REJECTED')).length;
    bot.closeWindow(bot.currentWindow); await sleep(300);
    bot.chat('/sortprobe'); await sleep(500);
    assert.equal(messages.filter(text => text.includes('SORT_REJECTED')).length, rejections + 1, 'Closed view token must be rejected');
    console.log('GUI_OK weather, menu updates/switches, stale rejection, constraints/tab/errors, chest metadata preserved');
  } catch (error) { console.error(error); process.exitCode = 1; }
  finally { clearTimeout(timer); bot.quit(); }
})();
