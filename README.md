# Нежные крестики-нолики + Telegram

Дружелюбная игра «Крестики-нолики» с уведомлениями в Telegram. Подходит для деплоя на Vercel без базы данных.

## Что внутри

- Frontend: чистые HTML/CSS/JS.
- Backend: Vercel Serverless Functions в папке `/api`.
- Telegram-уведомления о победе/проигрыше.

## Переменные окружения

Создайте переменные в Vercel (Project Settings → Environment Variables):

- `BOT_TOKEN` — токен бота от @BotFather
- `BOT_USERNAME` — username бота без `@` (например: `cool_woman_bot`)
- `SITE_URL` — публичный URL проекта (например: `https://your-project.vercel.app`)

Шаблон есть в `.env.example`.

## Деплой на Vercel

1. Создайте новый проект в Vercel и подключите репозиторий.
2. Убедитесь, что в корне есть файлы `index.html`, `styles.css`, `app.js`, папка `api` и `package.json`.
3. Добавьте переменные окружения (см. выше).
4. Нажмите Deploy.

## Установка webhook Telegram

Скопируйте строку ниже и подставьте свои значения:

```
https://api.telegram.org/bot<BOT_TOKEN>/setWebhook?url=<SITE_URL>/api/telegram-webhook
```

Откройте её в браузере — Telegram ответит `{"ok":true,"result":true}`.

## Как проверить

1. Откройте сайт.
2. Нажмите «Подключить Telegram».
3. В Telegram нажмите **Start** у бота.
4. Вернитесь на сайт по кнопке из бота.
5. Сыграйте: при победе/проигрыше должно прийти сообщение в Telegram.

## Как это работает (простыми словами)

- Кнопка «Подключить Telegram» открывает бота с параметром `start`.
- Бот отвечает сообщением с кнопкой «Вернуться на сайт».
- Сайт сохраняет `chat_id` в `localStorage` и после игры отправляет уведомление через `/api/notify`.
- Никаких БД и хранилищ не используется.

Если что-то не получается — просто напишите, помогу разобраться ✨
