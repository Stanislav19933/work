import express from 'express';
import dotenv from 'dotenv';
import crypto from 'crypto';
import path from 'path';
import { fileURLToPath } from 'url';
import { sendMessage } from './telegram.js';
import { kv } from '@vercel/kv';

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;
const BOT_USERNAME = process.env.BOT_USERNAME;
const SITE_URL = process.env.SITE_URL || `http://localhost:${PORT}`;

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

function generateToken() {
  return crypto.randomBytes(32).toString('hex');
}

function generatePromoCode() {
  return String(Math.floor(Math.random() * 100000)).padStart(5, '0');
}

app.post('/api/link', (req, res) => {
  if (!BOT_USERNAME) {
    return res.status(500).json({ ok: false, message: 'BOT_USERNAME не задан в .env' });
  }

  const token = generateToken();
  try {
    kv.set(`link:${token}`, '', { ex: 60 * 60 * 24 * 7 });
    const botLink = `https://t.me/${BOT_USERNAME}?start=${token}`;
    return res.json({ ok: true, botLink, token });
  } catch (error) {
    return res.status(500).json({ ok: false, message: 'Не удалось создать токен' });
  }
});

app.get('/api/link-status', (req, res) => {
  const { token } = req.query;
  if (!token) {
    return res.status(400).json({ ok: false, message: 'Token обязателен' });
  }

  kv.get(`link:${String(token)}`)
    .then((value) => res.json({ ok: true, linked: Boolean(value && value.length > 0) }))
    .catch(() => res.status(500).json({ ok: false, message: 'Не удалось проверить статус' }));
});

app.post('/api/result', async (req, res) => {
  const { token, result, promoCode } = req.body;

  if (!['win', 'lose', 'draw'].includes(result)) {
    return res.status(400).json({ ok: false, message: 'Некорректный результат' });
  }

  if (!token) {
    return res.json({ ok: true, telegramSent: false });
  }

  let chatId = null;
  try {
    chatId = await kv.get(`link:${token}`);
  } catch (error) {
    return res.status(500).json({ ok: false, message: 'Не удалось получить данные привязки' });
  }

  if (!chatId) {
    return res.json({ ok: true, telegramSent: false });
  }

  let message = '';
  let serverPromoCode = promoCode;

  if (result === 'win') {
    if (!serverPromoCode) {
      serverPromoCode = generatePromoCode();
    }
    message = `Победа! Твой промокод: ${serverPromoCode}`;
  }

  if (result === 'lose') {
    message = 'Увы, сегодня не твой день. Попробуешь ещё раз?';
  }

  if (result === 'draw') {
    message = 'Ничья! Давай реванш?';
  }

  try {
    await sendMessage(chatId, message);
    return res.json({ ok: true, telegramSent: true, promoCode: serverPromoCode });
  } catch (error) {
    return res.status(500).json({ ok: false, message: 'Не удалось отправить сообщение в Telegram' });
  }
});

app.all('/api/telegram-webhook', async (req, res) => {
  if (req.method !== 'POST') {
    return res.status(200).send('OK');
  }

  try {
    const update = req.body;
    const message = update.message;

    if (!message || !message.text || !message.chat) {
      return res.json({ ok: true });
    }

    if (!message.text.startsWith('/start')) {
      return res.json({ ok: true });
    }

    const parts = message.text.split(' ');
    const token = parts[1];
    if (!token) {
      return res.json({ ok: true });
    }

    await kv.set(`link:${token}`, String(message.chat.id), { ex: 60 * 60 * 24 * 7 });

    const url = `${SITE_URL}/?token=${token}`;
    await sendMessage(message.chat.id, 'Готово! Возвращайся на сайт.', {
      reply_markup: {
        inline_keyboard: [[{ text: 'Открыть игру', url }]]
      }
    });
  } catch (error) {
    return res.status(200).json({ ok: true });
  }

  return res.json({ ok: true });
});

app.get(/^(?!\/api).*/, (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

export default app;
