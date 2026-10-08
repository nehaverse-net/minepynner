const assert = require('node:assert/strict');
const mineflayer = require('mineflayer');
const { once } = require('node:events');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const bots = ['ChatSender', 'ChatReceiver'].map(username => mineflayer.createBot({
  host: '127.0.0.1', port: Number(process.argv[2]), username, auth: 'offline', version: '1.21.11', respawn: false,
}));
const received = [[], []];
bots.forEach((bot, index) => bot.on('message', message => received[index].push(message.toString())));
const deadline = setTimeout(() => { console.error('Chat filter timeout'); process.exit(1); }, 30000);
(async () => {
  try {
    await Promise.all(bots.map(bot => once(bot, 'spawn')));
    await sleep(1500);
    bots[0].chat('こんにちは CHAT_ALLOW');
    await sleep(1000);
    assert(received[1].some(text => text.includes('CHAT_ALLOW')), 'Ordinary chat must reach receiver');
    for (const [index, word] of ['イキスギ', 'お前やりませんねぇすぎぃ'].entries()) {
      bots[0].chat(`${word} CHAT_BLOCK_${index}`);
      await sleep(1000);
      assert(!received[1].some(text => text.includes(`CHAT_BLOCK_${index}`)), 'Forbidden chat must not reach receiver');
    }
    assert.equal(received[0].filter(text => text.includes('CHAT_WARNING')).length, 2);
    assert(!received[1].some(text => text.includes('CHAT_WARNING')), 'Warning is private to sender');
    console.log('CHAT_FILTER_OK ordinary delivered, both forbidden phrases blocked, sender warned');
    const died = once(bots[0], 'death');
    bots[0].chat('/kill @s');
    await died;
    await sleep(1000);
    assert(bots[0].health <= 0, 'Sender must still be dead, without automatic respawn');
    assert(received[0].some(text => text.includes('DEATH_NOTICE')), 'Dead player must receive death callback message');
    assert(!received[1].some(text => text.includes('DEATH_NOTICE')), 'Death message is private');
    console.log('DEATH_MESSAGE_OK private message received before respawn');
  } catch (error) {
    console.error(error);
    process.exitCode = 1;
  } finally {
    clearTimeout(deadline);
    bots.forEach(bot => bot.quit());
  }
})();
