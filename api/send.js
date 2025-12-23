const https = require('https');

module.exports = async (req, res) => {
  if (req.method !== 'POST') {
    res.statusCode = 405;
    res.setHeader('Allow', 'POST');
    res.end('Method not allowed');
    return;
  }

  try {
    const token = process.env.TELEGRAM_BOT_TOKEN;
    if (!token) {
      res.statusCode = 500;
      res.end('Missing TELEGRAM_BOT_TOKEN');
      return;
    }

    let body = '';
    await new Promise(resolve => {
      req.on('data', chunk => { body += chunk; });
      req.on('end', resolve);
    });

    const data = JSON.parse(body || '{}');
    const { chatId, text } = data;
    if (!chatId || !text) {
      res.statusCode = 400;
      res.end('chatId and text required');
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

    const result = await new Promise((resolve, reject) => {
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

    res.statusCode = 200;
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({ ok: true, result }));
  } catch (error) {
    console.error('Send API error', error);
    res.statusCode = 500;
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({ error: error.message }));
  }
};
