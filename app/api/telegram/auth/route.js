import { NextResponse } from "next/server";
import { verifyTelegramAuth } from "@/lib/telegramAuth";

export async function POST(req) {
  try {
    const botToken = process.env.TELEGRAM_BOT_TOKEN;
    if (!botToken) {
      return NextResponse.json({ error: "Server is not configured" }, { status: 500 });
    }

    const body = await req.json().catch(() => null);
    const initData = String(body?.initData || "");
    if (!initData) {
      return NextResponse.json({ error: "Missing initData" }, { status: 400 });
    }

    const params = Object.fromEntries(new URLSearchParams(initData).entries());
    const verified = verifyTelegramAuth(params, botToken);
    if (!verified.ok) {
      return NextResponse.json({ error: "Bad signature" }, { status: 401 });
    }

    const tgId = verified.data.id;
    const resp = NextResponse.json({ ok: true, tgId });
    resp.cookies.set("tg_uid", String(tgId), {
      httpOnly: true,
      secure: true,
      sameSite: "lax",
      path: "/",
      maxAge: 60 * 60 * 24 * 30
    });
    return resp;
  } catch (e) {
    return NextResponse.json({ error: "Server error", details: String(e?.message || e) }, { status: 500 });
  }
}
