import dotenv from 'dotenv';

dotenv.config();

const BOT_TOKEN = process.env.BOT_TOKEN;

export async function sendMessage(chatId, text, options = {}) {
  if (!BOT_TOKEN) {
    throw new Error('BOT_TOKEN не задан в .env');
  }

  const payload = {
    chat_id: chatId,
    text,
    ...options
  };

  const response = await fetch(`https://api.telegram.org/bot${BOT_TOKEN}/sendMessage`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const data = await response.text();
    throw new Error(`Ошибка Telegram API: ${data}`);
  }

  return response.json();
}
