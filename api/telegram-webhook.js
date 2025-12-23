export default async function handler(req, res) {
  try {
    if (req.method === 'GET' && req.query?.config) {
      res.status(200).json({
        botUsername: process.env.BOT_USERNAME || null,
        siteUrl: process.env.SITE_URL || null,
      });
      return;
    }

    if (req.method !== 'POST') {
      res.status(200).json({ ok: true });
      return;
    }

    const body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body || {};
    const message = body?.message;
    const text = message?.text || '';
    const chatId = message?.chat?.id;

    if (text.startsWith('/start') && chatId) {
      const parts = text.split(' ');
      const state = parts[1] || '';
      const siteUrl = process.env.SITE_URL || '';
      const botToken = process.env.BOT_TOKEN || '';

      if (siteUrl && botToken) {
        const replyMarkup = {
          inline_keyboard: [
            [
              {
                text: 'Вернуться на сайт',
                url: `${siteUrl}/?chat_id=${chatId}&state=${encodeURIComponent(state)}`,
              },
            ],
          ],
        };

        await fetch(`https://api.telegram.org/bot${botToken}/sendMessage`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            chat_id: chatId,
            text: 'Готово! Вернись на сайт ✨',
            reply_markup: replyMarkup,
          }),
        });
      }
    }
  } catch (error) {
    console.error('Telegram webhook error', error);
  }

  res.status(200).json({ ok: true });
}
