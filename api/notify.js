export default async function handler(req, res) {
  if (req.method !== 'POST') {
    res.status(200).json({ ok: false, error: 'Method not allowed' });
    return;
  }

  try {
    const body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body || {};
    const chatId = body.chat_id;
    const event = body.event;
    const promoCode = body.promoCode;

    if (!chatId || !event) {
      res.status(200).json({ ok: false, error: 'Missing chat_id or event' });
      return;
    }

    if (!['win', 'lose'].includes(event)) {
      res.status(200).json({ ok: false, error: 'Invalid event' });
      return;
    }

    if (event === 'win') {
      if (!/^[0-9]{5}$/.test(String(promoCode))) {
        res.status(200).json({ ok: false, error: 'Invalid promoCode' });
        return;
      }
    }

    const botToken = process.env.BOT_TOKEN || '';
    if (!botToken) {
      res.status(200).json({ ok: false, error: 'BOT_TOKEN missing' });
      return;
    }

    const text = event === 'win'
      ? `Победа! Промокод выдан: ${promoCode}`
      : 'Проигрыш';

    await fetch(`https://api.telegram.org/bot${botToken}/sendMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        chat_id: chatId,
        text,
      }),
    });

    res.status(200).json({ ok: true });
  } catch (error) {
    res.status(200).json({ ok: false, error: 'Unexpected error' });
  }
}
