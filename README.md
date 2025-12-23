# Крестики-нолики + Telegram (демо-проект)

Дружелюбный мини-проект: игра «Крестики-нолики» в браузере и опциональная отправка результата в Telegram.

## Что внутри

- **Frontend:** чистые HTML/CSS/JS (без React/Next).
- **Backend:** Node.js + Express + SQLite.
- **Запуск:** `npm i` → `npm run dev`.

## Быстрый старт (локально)

1. Установи зависимости:
   ```bash
   npm i
   ```
2. Создай файл `.env` на основе `.env.example` и заполни значения.
3. Запусти сервер:
   ```bash
   npm run dev
   ```
4. Открой в браузере: [http://localhost:3000](http://localhost:3000)

## Как создать Telegram-бота

1. Открой в Telegram бота [@BotFather](https://t.me/BotFather).
2. Выполни команду `/newbot` и следуй инструкциям.
3. Сохрани **BOT_TOKEN** — он нужен в `.env`.
4. Укажи **BOT_USERNAME** (без `@`).

## Как поставить webhook

Telegram должен знать, куда слать обновления.

```
https://api.telegram.org/bot<BOT_TOKEN>/setWebhook?url=<SITE_URL>/api/telegram-webhook
```

Пример для локалки:
```
https://api.telegram.org/bot123456:ABCDEF/setWebhook?url=http://localhost:3000/api/telegram-webhook
```

> Если нужно тестировать из внешнего интернета, используй ngrok или любой публичный URL и подставь его в `SITE_URL`.

## Как проверить привязку и отправку

1. Нажми кнопку **«Привязать Telegram…»** на главной странице.
2. Откроется бот. Нажми **Start**.
3. Вернись на сайт — статус должен стать «Telegram привязан ✅».
4. Сыграй матч. При победе появится промокод и результат уйдёт в Telegram.

## Переменные окружения

Смотри `.env.example`:

- `BOT_TOKEN` — токен бота.
- `BOT_USERNAME` — username бота без `@`.
- `SITE_URL` — адрес сайта (для локалки `http://localhost:3000`).
- `PORT` — порт сервера.

## Структура проекта

```
public/
  index.html
  styles.css
  app.js
server.js
db.js
telegram.js
```
