const https = require('https');
const crypto = require('crypto');

function parseInitData(initData = '') {
  const params = new URLSearchParams(initData);
  const map = {};
  params.forEach((value, key) => {
    map[key] = value;
  });
  return map;
}

function verifyInitData(initData, botToken) {
  if (!initData || !botToken) return false;
  const params = new URLSearchParams(initData);
  const hash = params.get('hash');
  if (!hash) return false;

  params.delete('hash');
  const data = [];
  params.forEach((value, key) => {
    data.push(`${key}=${value}`);
  });
  data.sort();
  const dataCheckString = data.join('\n');

  const secretKey = crypto.createHmac('sha256', 'WebAppData').update(botToken).digest();
  const computedHash = crypto.createHmac('sha256', secretKey).update(dataCheckString).digest('hex');
  return computedHash === hash;
}

function extractRecipient(initDataMap) {
  try {
    const chatRaw = initDataMap.chat ? JSON.parse(initDataMap.chat) : null;
    const userRaw = initDataMap.user ? JSON.parse(initDataMap.user) : null;
    return chatRaw?.id ?? userRaw?.id ?? null;
  } catch (err) {
    return null;
  }
}

function sendTelegram(botToken, chatId, text) {
  return new Promise((resolve, reject) => {
    const payload = JSON.stringify({ chat_id: chatId, text });
    const options = {
      hostname: 'api.telegram.org',
      path: `/bot${botToken}/sendMessage`,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(payload),
      },
    };

    const tReq = https.request(options, tRes => {
      let resp = '';
      tRes.on('data', chunk => { resp += chunk; });
      tRes.on('end', () => {
        if (tRes.statusCode >= 200 && tRes.statusCode < 300) {
          resolve(resp);
        } else {
          reject(new Error(`Telegram API error: ${tRes.statusCode} ${resp}`));
        }
      });
    });
    tReq.on('error', reject);
    tReq.write(payload);
    tReq.end();
  });
}

function buildMessage(result) {
  if (result === 'win') {
    const code = Math.floor(Math.random() * 100000).toString().padStart(5, '0');
    return { text: `Ура! Ты выиграла 🎉 Твой промокод: ${code}`, code };
  }
  if (result === 'lose') {
    return { text: 'Сегодня победа за мной, но ты молодец! Загляни ещё раз и забери подарок.' };
  }
  return { text: 'Ничья! Давай сыграем ещё раз — подарок тебя ждёт.' };
}

module.exports = async (req, res) => {
  if (req.method !== 'POST') {
    res.statusCode = 405;
    res.setHeader('Allow', 'POST');
    res.end('Method not allowed');
    return;
  }

  try {
    const token = process.env.BOT_TOKEN;
    if (!token) {
      res.statusCode = 500;
      res.end('Missing BOT_TOKEN');
      return;
    }

    let body = '';
    await new Promise(resolve => {
      req.on('data', chunk => { body += chunk; });
      req.on('end', resolve);
    });

    const data = JSON.parse(body || '{}');
    const { initData, result } = data;
    if (!initData || !result) {
      res.statusCode = 400;
      res.end('initData and result required');
      return;
    }

    if (!verifyInitData(initData, token)) {
      res.statusCode = 401;
      res.end('invalid initData');
      return;
    }

    const parsed = parseInitData(initData);
    const recipientId = extractRecipient(parsed);
    if (!recipientId) {
      res.statusCode = 400;
      res.end('recipient not found');
      return;
    }

    const message = buildMessage(result);
    const sendResult = await sendTelegram(token, recipientId, message.text);

    res.statusCode = 200;
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({ ok: true, code: message.code, telegram: sendResult }));
  } catch (error) {
    console.error('Send API error', error);
    res.statusCode = 500;
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({ error: error.message }));
  }
};
