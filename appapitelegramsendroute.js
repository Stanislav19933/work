import { NextResponse } from "next/server";

function mustEnv(name) {
  const v = process.env[name];
  if (!v) throw new Error(`Missing env: ${name}`);
  return v;
}

export async function POST(req) {
  try {
    const botToken = mustEnv("TELEGRAM_BOT_TOKEN");

    const cookies = req.cookies;
    const tgUid = cookies.get("tg_uid")?.value;
    if (!tgUid) {
      return NextResponse.json({ error: "Telegram is not connected" }, { status: 400 });
    }

    const body = await req.json().catch(() => null);
    if (!body || (body.result !== "win" && body.result !== "lose")) {
      return NextResponse.json({ error: "Bad payload" }, { status: 400 });
    }

    let text = "Проигрыш";
    if (body.result === "win") {
      const code = String(body.code ?? "");
      if (!/^\d{5}$/.test(code)) {
        return NextResponse.json({ error: "Bad promo code" }, { status: 400 });
      }
      text = `Победа! Промокод выдан: ${code}`;
    }

    const apiUrl = `https://api.telegram.org/bot${botToken}/sendMessage`;
    const resp = await fetch(apiUrl, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        chat_id: tgUid,
        text
      })
    });

    const data = await resp.json().catch(() => ({}));
    if (!resp.ok || data.ok !== true) {
      return NextResponse.json({ error: "Telegram send failed", details: data }, { status: 502 });
    }

    return NextResponse.json({ ok: true });
  } catch (e) {
    return NextResponse.json({ error: "Server error", details: String(e?.message || e) }, { status: 500 });
  }
}
