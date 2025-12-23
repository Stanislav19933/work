const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = process.env.PORT || 3000;
const PUBLIC_DIR = __dirname;

function parseInitData(initData = '') {
  const params = new URLSearchParams(initData);
  const map = {};
  params.forEach((value, key) => { map[key] = value; });
  return map;
}

function verifyInitData(initData, botToken) {
  if (!initData || !botToken) return false;
  const params = new URLSearchParams(initData);
  const hash = params.get('hash');
  if (!hash) return false;

  params.delete('hash');
  const data = [];
  params.forEach((value, key) => data.push(`${key}=${value}`));
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

function sendTelegram(chatId, text) {
  return new Promise((resolve, reject) => {
    const token = process.env.BOT_TOKEN;
    if (!token) {
      reject(new Error('Отсутствует BOT_TOKEN'));
      return;
    }

    const payload = JSON.stringify({ chat_id: chatId, text });
    const options = {
      hostname: 'api.telegram.org',
      path: `/bot${token}/sendMessage`,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(payload),
      },
    };

    const req = https.request(options, res => {
      let body = '';
      res.on('data', chunk => { body += chunk; });
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(body);
        } else {
          reject(new Error(`Telegram API error: ${res.statusCode} ${body}`));
        }
      });
    });

    req.on('error', reject);
    req.write(payload);
    req.end();
  });
}

function serveStatic(req, res) {
  const safePath = req.url.split('?')[0];
  let filePath = path.join(PUBLIC_DIR, safePath === '/' ? 'index.html' : safePath.slice(1));

  if (!filePath.startsWith(PUBLIC_DIR)) {
    res.writeHead(400);
    res.end('Bad request');
    return;
  }

  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404);
      res.end('Not found');
      return;
    }

    const ext = path.extname(filePath);
    const map = {
      '.html': 'text/html',
      '.css': 'text/css',
      '.js': 'application/javascript',
      '.json': 'application/json',
    };
    res.writeHead(200, { 'Content-Type': map[ext] || 'text/plain' });
    res.end(data);
  });
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'POST' && req.url === '/api/send') {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', async () => {
      try {
        const data = JSON.parse(body || '{}');
        const { initData, result } = data;
        if (!initData || !result) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'initData и result обязательны' }));
          return;
        }

        if (!verifyInitData(initData, process.env.BOT_TOKEN)) {
          res.writeHead(401, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'invalid initData' }));
          return;
        }

        const parsed = parseInitData(initData);
        const recipientId = extractRecipient(parsed);
        if (!recipientId) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'recipient not found' }));
          return;
        }

        let code = null;
        let text = '';
        if (result === 'win') {
          code = Math.floor(Math.random() * 100000).toString().padStart(5, '0');
          text = `Ура! Ты выиграла 🎉 Твой промокод: ${code}`;
        } else if (result === 'lose') {
          text = 'Сегодня победа за мной, но ты молодец! Загляни ещё раз и забери подарок.';
        } else {
          text = 'Ничья! Давай сыграем ещё раз — подарок тебя ждёт.';
        }

        await sendTelegram(recipientId, text);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ ok: true, code }));
      } catch (error) {
        console.error(error);
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: error.message }));
      }
    });
    return;
  }

  serveStatic(req, res);
});

server.listen(PORT, () => {
  console.log(`Dev server listening on http://localhost:${PORT}`);
});
