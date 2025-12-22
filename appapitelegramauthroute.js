import { NextResponse } from "next/server";
import { verifyTelegramAuth } from "@/lib/telegramAuth";

export async function GET(req) {
  const botToken = process.env.TELEGRAM_BOT_TOKEN;
  const baseUrl = process.env.APP_BASE_URL; // например https://xxx.vercel.app
  if (!botToken || !baseUrl) {
    return NextResponse.json({ error: "Server is not configured" }, { status: 500 });
  }

  const url = new URL(req.url);
  const params = Object.fromEntries(url.searchParams.entries());

  const verified = verifyTelegramAuth(params, botToken);
  if (!verified.ok) {
    return NextResponse.redirect(new URL(`/?tg=fail`, baseUrl));
  }

  const tgId = verified.data.id;
  const res = NextResponse.redirect(new URL(`/?tg=ok`, baseUrl));

  // Cookie с telegram user id. HttpOnly — чтобы не светить в JS. SameSite=Lax достаточно.
  res.cookies.set("tg_uid", String(tgId), {
    httpOnly: true,
    secure: true,
    sameSite: "lax",
    path: "/",
    maxAge: 60 * 60 * 24 * 30
  });

  return res;
}
