import crypto from "crypto";

// Проверка подписи данных Telegram Login Widget.
// https://core.telegram.org/widgets/login#checking-authorization
export function verifyTelegramAuth(query, botToken) {
  const { hash, ...data } = query;
  if (!hash) return { ok: false, reason: "missing_hash" };

  const keys = Object.keys(data).sort();
  const dataCheckString = keys.map(k => `${k}=${data[k]}`).join("\n");

  const secretKey = crypto.createHash("sha256").update(botToken).digest();
  const hmac = crypto.createHmac("sha256", secretKey).update(dataCheckString).digest("hex");

  if (hmac !== hash) return { ok: false, reason: "bad_hash" };

  // auth_date можно проверять на свежесть, но для тестового не обязательно.
  return { ok: true, data };
}
