"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { checkWinner, cpuMove, EMPTY, HUMAN, CPU } from "@/lib/game";
import { generatePromoCode5 } from "@/lib/promo";

const BOT_USERNAME = "cool_woman_bot";

function cls(...a) { return a.filter(Boolean).join(" "); }

function Confetti({ run }) {
  // Честное "вау" без библиотек: простые частицы.
  const [parts, setParts] = useState([]);
  useEffect(() => {
    if (!run) return;
    const p = Array.from({ length: 70 }).map((_, i) => ({
      id: i,
      x: Math.random() * 100,
      y: -10 - Math.random() * 40,
      r: 6 + Math.random() * 10,
      d: 900 + Math.random() * 900,
      delay: Math.random() * 120
    }));
    setParts(p);
    const t = setTimeout(() => setParts([]), 2200);
    return () => clearTimeout(t);
  }, [run]);

  if (parts.length === 0) return null;

  return (
    <div style={{
      position: "fixed", inset: 0, pointerEvents: "none", overflow: "hidden", zIndex: 50
    }}>
      {parts.map(s => (
        <span key={s.id}
          style={{
            position: "absolute",
            left: `${s.x}%`,
            top: `${s.y}px`,
            width: `${s.r}px`,
            height: `${s.r * 0.6}px`,
            borderRadius: "999px",
            background: "linear-gradient(90deg, rgba(192,92,255,0.95), rgba(109,214,255,0.95))",
            transform: "rotate(18deg)",
            animation: `fall ${s.d}ms ease-out ${s.delay}ms forwards`
          }}
        />
      ))}
      <style jsx>{`
        @keyframes fall {
          to {
            transform: translateY(120vh) rotate(210deg);
            opacity: 0;
          }
        }
      `}</style>
    </div>
  );
}

export default function Page() {
  const [tgConnectedHint, setTgConnectedHint] = useState(false);
  const [botStartedHint, setBotStartedHint] = useState(false);

  const [board, setBoard] = useState(Array(9).fill(EMPTY));
  const [turn, setTurn] = useState(HUMAN);
  const [busy, setBusy] = useState(false);

  const [status, setStatus] = useState("Твой ход ✨");
  const [result, setResult] = useState(null); // "win" | "lose" | "draw"
  const [winLine, setWinLine] = useState(null);

  const [promo, setPromo] = useState(null);
  const [toast, setToast] = useState(null);
  const [confettiRun, setConfettiRun] = useState(false);

  const mounted = useRef(false);

  // Telegram Login Widget: вставляется скриптом
  useEffect(() => {
    // Подсказки статуса из URL-параметров
    const url = new URL(window.location.href);
    const tg = url.searchParams.get("tg");
    if (tg === "ok") setTgConnectedHint(true);
    if (tg === "fail") setToast("Не удалось подключить Telegram. Попробуй ещё раз.");

    // Уберём хвост ?tg=... чтобы выглядело аккуратно
    if (tg) {
      url.searchParams.delete("tg");
      window.history.replaceState({}, "", url.toString());
    }

    // Флаг "нажимал открыть бота" — чисто для UX
    const started = localStorage.getItem("bot_started") === "1";
    setBotStartedHint(started);

    // Подключим виджет только на клиенте
    const script = document.createElement("script");
    script.async = true;
    script.src = "https://telegram.org/js/telegram-widget.js?22";
    script.setAttribute("data-telegram-login", BOT_USERNAME);
    script.setAttribute("data-size", "large");
    script.setAttribute("data-radius", "14");
    script.setAttribute("data-userpic", "false");
    script.setAttribute("data-request-access", "write");
    script.setAttribute("data-auth-url", "/api/telegram/auth");
    script.setAttribute("data-lang", "ru");

    const mount = document.getElementById("tg-widget-mount");
    mount?.appendChild(script);

    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2200);
    return () => clearTimeout(t);
  }, [toast]);

  const r = useMemo(() => checkWinner(board), [board]);

  useEffect(() => {
    if (!mounted.current) return;

    if (r.winner === HUMAN) {
      setResult("win");
      setStatus("Победа! 💎");
      setWinLine(r.line);
      handleWinOnce();
      return;
    }
    if (r.winner === CPU) {
      setResult("lose");
      setStatus("Упс… давай ещё раз?");
      setWinLine(r.line);
      handleLoseOnce();
      return;
    }
    if (r.winner === "DRAW") {
      setResult("draw");
      setStatus("Ничья. Хочешь реванш?");
      setWinLine(null);
      return;
    }

    // если игра не закончена — управление ходом
    if (turn === CPU) {
      if (!busy) setBusy(true);
      setStatus("Компьютер думает…");
      const t = setTimeout(() => {
        setBoard(prev => {
          const idx = cpuMove(prev, 0.08);
          if (idx == null || prev[idx] !== EMPTY) return prev;
          const next = prev.slice();
          next[idx] = CPU;
          return next;
        });
        setTurn(HUMAN);
        setBusy(false);
        setStatus("Твой ход ✨");
      }, 420);
      return () => clearTimeout(t);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [r.winner, turn]);

  const outcomeSentRef = useRef({ win: false, lose: false });

  async function sendToTelegram(payload) {
    const resp = await fetch("/api/telegram/send", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload)
    });
    const data = await resp.json().catch(() => ({}));
    if (!resp.ok) throw new Error(data?.error || "send_failed");
    return data;
  }

  async function handleWinOnce() {
    if (outcomeSentRef.current.win) return;
    outcomeSentRef.current.win = true;

    const code = generatePromoCode5();
    setPromo(code);
    setConfettiRun(false);
    setTimeout(() => setConfettiRun(true), 50);

    // Попробуем отправить. Если не подключён Telegram — покажем понятную подсказку.
    try {
      await sendToTelegram({ result: "win", code });
    } catch (e) {
      setToast("Подключи Telegram и нажми Start у бота — иначе он не сможет написать тебе.");
    }
  }

  async function handleLoseOnce() {
    if (outcomeSentRef.current.lose) return;
    outcomeSentRef.current.lose = true;
    try {
      await sendToTelegram({ result: "lose" });
    } catch (e) {
      setToast("Чтобы получить сообщение в Telegram, подключи Telegram и нажми Start у бота.");
    }
  }

  function resetGame() {
    setBoard(Array(9).fill(EMPTY));
    setTurn(HUMAN);
    setBusy(false);
    setStatus("Твой ход ✨");
    setResult(null);
    setWinLine(null);
    setPromo(null);
    outcomeSentRef.current = { win: false, lose: false };
  }

  function onCell(i) {
    if (busy) return;
    if (result) return;
    if (turn !== HUMAN) return;
    setBusy(true);
    setBoard(prev => {
      if (prev[i] !== EMPTY) return prev;
      const next = prev.slice();
      next[i] = HUMAN;
      return next;
    });
    setTurn(CPU);
  }

  async function copyPromo() {
    if (!promo) return;
    try {
      await navigator.clipboard.writeText(promo);
      setToast("Промокод скопирован");
      if (navigator.vibrate) navigator.vibrate(20);
    } catch {
      setToast("Не удалось скопировать");
    }
  }

  function markBotStarted() {
    localStorage.setItem("bot_started", "1");
    setBotStartedHint(true);
    // Здесь “быстрый юмор”: бот не читает мысли, зато читает /start.
    setToast("Отлично. Теперь бот не стесняется писать первым 🙂");
  }

  const connectStepsOk = tgConnectedHint && botStartedHint;

  return (
    <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", padding: 18, background: "radial-gradient(circle at 10% 10%, rgba(255,227,255,0.28), transparent 32%), radial-gradient(circle at 80% 20%, rgba(214,245,255,0.32), transparent 32%), #f7f5ff" }}>
      <Confetti run={confettiRun} />

      <div style={{ width: "min(1100px, 100%)", display: "grid", gap: 16 }}>
        <div style={{
          background: "linear-gradient(120deg, rgba(192,92,255,0.16), rgba(109,214,255,0.12))",
          border: "1px solid rgba(192,92,255,0.18)",
          borderRadius: "24px",
          boxShadow: "var(--shadow)",
          padding: 20,
          color: "rgba(24,24,28,0.92)",
          backdropFilter: "blur(10px)"
        }}>
          <div style={{ fontSize: 28, fontWeight: 750, letterSpacing: "-0.02em" }}>
            Крестики-нолики с подарком для тебя
          </div>
          <div style={{ color: "var(--muted)", marginTop: 6, fontSize: 15, lineHeight: 1.45 }}>
            Сделай два простых шага в Telegram — и игра откроется. Победа дарит промокод, а бот сразу пришлёт его в личные сообщения.
          </div>
        </div>

        <div style={{
          display: "grid",
          gap: 16,
          gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))"
        }}>
          <div style={{
            background: "var(--card)",
            border: "1px solid var(--cardBorder)",
            borderRadius: "var(--radius)",
            boxShadow: "var(--shadow)",
            padding: 18,
            backdropFilter: "blur(10px)"
          }}>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 10 }}>
              <div>
                <div style={{ fontSize: 20, fontWeight: 750 }}>1. Подключи Telegram</div>
                <div style={{ color: "var(--muted)", marginTop: 6 }}>
                  Нужно всего 2 шага, чтобы бот знал, куда отправить подарок.
                </div>
              </div>
              <div style={{ padding: "8px 12px", borderRadius: 999, background: "rgba(192,92,255,0.12)", color: "rgba(99,63,143,0.9)", fontWeight: 700, fontSize: 12 }}>
                обязательно перед игрой
              </div>
            </div>

            <div style={{ marginTop: 14, display: "grid", gap: 12 }}>
              <div style={{
                padding: 12,
                borderRadius: 18,
                border: "1px solid rgba(27,27,31,0.10)",
                background: "rgba(255,255,255,0.65)"
              }}>
                <div style={{ fontWeight: 700 }}>
                  {tgConnectedHint ? "✅ Шаг 1: Telegram подключён" : "Шаг 1: войти через Telegram"}
                </div>
                <div style={{ color: "var(--muted)", marginTop: 6, lineHeight: 1.35 }}>
                  Нажми кнопку ниже, подтвердить вход — и всё. Это занимает пару секунд.
                </div>
                <div id="tg-widget-mount" style={{ marginTop: 10 }} />
              </div>

              <div style={{
                padding: 12,
                borderRadius: 18,
                border: "1px solid rgba(27,27,31,0.10)",
                background: "rgba(255,255,255,0.65)"
              }}>
                <div style={{ fontWeight: 700 }}>
                  {botStartedHint ? "✅ Шаг 2: Нажатие Start в боте" : "Шаг 2: нажми Start у бота"}
                </div>
                <div style={{ color: "var(--muted)", marginTop: 6, lineHeight: 1.35 }}>
                  Telegram не даёт боту писать первой. Сделай Start — и бот сможет прислать твой результат.
                </div>
                <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginTop: 10 }}>
                  <a
                    href={`https://t.me/${BOT_USERNAME}?start=play`}
                    target="_blank"
                    rel="noreferrer"
                    onClick={markBotStarted}
                    style={{
                      padding: "10px 12px",
                      borderRadius: 14,
                      border: "1px solid rgba(192,92,255,0.28)",
                      background: "linear-gradient(90deg, rgba(192,92,255,0.16), rgba(109,214,255,0.14))",
                      boxShadow: "var(--shadow2)",
                      fontWeight: 700
                    }}
                  >
                    Открыть бота
                  </a>

                  <button
                    onClick={() => { localStorage.removeItem("bot_started"); setBotStartedHint(false); setToast("Сбросили шаг 2"); }}
                    style={{
                      padding: "10px 12px",
                      borderRadius: 14,
                      border: "1px solid rgba(27,27,31,0.12)",
                      background: "rgba(255,255,255,0.75)",
                      boxShadow: "var(--shadow2)",
                      cursor: "pointer"
                    }}
                  >
                    Сброс шага 2
                  </button>
                </div>
              </div>

              <div style={{
                padding: 12,
                borderRadius: 18,
                border: "1px solid rgba(27,27,31,0.10)",
                background: connectStepsOk
                  ? "linear-gradient(180deg, rgba(43,182,115,0.12), rgba(255,255,255,0.65))"
                  : "rgba(255,255,255,0.65)"
              }}>
                <div style={{ fontWeight: 750 }}>
                  {connectStepsOk ? "Шаги сделаны! Игра доступна ниже." : "Сделай оба шага — и игра откроется ниже."}
                </div>
                <div style={{ color: "var(--muted)", marginTop: 6 }}>
                  Победа подарит промокод, а бот пришлёт его в твою переписку.
                </div>
              </div>
            </div>
          </div>

          <div style={{
            background: "var(--card)",
            border: "1px solid var(--cardBorder)",
            borderRadius: "var(--radius)",
            boxShadow: "var(--shadow)",
            padding: 18,
            backdropFilter: "blur(10px)",
            position: "relative",
            overflow: "hidden"
          }}>
            {!connectStepsOk && (
              <div style={{
                position: "absolute",
                inset: 0,
                background: "linear-gradient(180deg, rgba(255,255,255,0.78), rgba(247,245,255,0.92))",
                display: "grid",
                placeItems: "center",
                textAlign: "center",
                padding: 18,
                zIndex: 5
              }}>
                <div style={{ maxWidth: 360 }}>
                  <div style={{ fontWeight: 750, fontSize: 18 }}>Сначала Telegram, потом игра</div>
                  <div style={{ color: "var(--muted)", marginTop: 8, lineHeight: 1.4 }}>
                    Пройди два шага слева: вход и Start. Как только оба кружочка станут зелёными, поле откроется и можно играть.
                  </div>
                </div>
              </div>
            )}

            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: 12 }}>
              <div>
                <div style={{ fontSize: 26, fontWeight: 720, letterSpacing: "-0.02em" }}>
                  Крестики-нолики
                </div>
                <div style={{ color: "var(--muted)", marginTop: 6 }}>
                  Победа — промокод. Результат придёт в твой Telegram.
                </div>
              </div>

              <button
                onClick={resetGame}
                style={{
                  padding: "10px 14px",
                  borderRadius: 14,
                  border: "1px solid rgba(27,27,31,0.12)",
                  background: "rgba(255,255,255,0.75)",
                  boxShadow: "var(--shadow2)",
                  cursor: "pointer"
                }}
              >
                Сбросить
              </button>
            </div>

            <div style={{
              marginTop: 14,
              display: "grid",
              gridTemplateColumns: "1fr",
              gap: 12,
              opacity: connectStepsOk ? 1 : 0.45,
              transition: "opacity 200ms ease"
            }}>
              <div style={{
                display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12,
                padding: 14,
                borderRadius: 18,
                background: "rgba(255,255,255,0.65)",
                border: "1px solid rgba(27,27,31,0.10)"
              }}>
                <div>
                  <div style={{ fontWeight: 650 }}>{status}</div>
                  <div style={{ color: "var(--muted)", marginTop: 4 }}>
                    {connectStepsOk ? "Ходим по очереди. Компьютер думает сразу после твоего шага." : "Сделай два шага слева, чтобы начать игру."}
                  </div>
                </div>

                {promo && (
                  <button
                    onClick={copyPromo}
                    style={{
                      padding: "10px 12px",
                      borderRadius: 14,
                      border: "1px solid rgba(192,92,255,0.28)",
                      background: "linear-gradient(90deg, rgba(192,92,255,0.16), rgba(109,214,255,0.14))",
                      cursor: "pointer",
                      animation: "glow 1.4s ease-in-out infinite"
                    }}
                    title="Скопировать промокод"
                  >
                    {promo} · Скопировать
                  </button>
                )}
              </div>

              <div style={{
                display: "grid",
                gridTemplateColumns: "repeat(3, 1fr)",
                gap: 10,
                padding: 12,
                borderRadius: 22,
                background: "rgba(255,255,255,0.65)",
                border: "1px solid rgba(27,27,31,0.10)"
              }}>
                {board.map((v, i) => {
                  const isWin = winLine?.includes(i);
                  const disabled = busy || !!result || turn !== HUMAN || !connectStepsOk;
                  return (
                    <button
                      key={i}
                      onClick={() => onCell(i)}
                      disabled={disabled}
                      style={{
                        height: 110,
                        borderRadius: 18,
                        border: "1px solid rgba(27,27,31,0.10)",
                        background: isWin
                          ? "linear-gradient(180deg, rgba(192,92,255,0.18), rgba(109,214,255,0.12))"
                          : "rgba(255,255,255,0.78)",
                        boxShadow: isWin ? "0 0 0 2px rgba(192,92,255,0.18), var(--shadow2)" : "var(--shadow2)",
                        cursor: disabled ? "not-allowed" : "pointer",
                        transition: "transform 120ms ease, filter 120ms ease",
                        filter: busy ? "saturate(0.95)" : "none",
                        position: "relative",
                        overflow: "hidden"
                      }}
                      onMouseEnter={(e) => { if (!disabled) e.currentTarget.style.transform = "translateY(-2px)"; }}
                      onMouseLeave={(e) => { e.currentTarget.style.transform = "translateY(0px)"; }}
                      aria-label={`cell-${i}`}
                    >
                      <span style={{
                        display: "inline-block",
                        fontSize: 54,
                        fontWeight: 780,
                        letterSpacing: "-0.05em",
                        transform: v ? "scale(1)" : "scale(0.92)",
                        opacity: v ? 1 : 0,
                        animation: v ? "pop 140ms ease-out" : "none",
                        color: v === HUMAN ? "rgba(27,27,31,0.90)" : "rgba(192,92,255,0.92)"
                      }}>
                        {v ?? ""}
                      </span>

                      {!v && !result && turn === HUMAN && !busy && connectStepsOk && (
                        <span style={{
                          position: "absolute",
                          inset: 0,
                          opacity: 0,
                          transition: "opacity 120ms ease",
                          background: "linear-gradient(90deg, rgba(192,92,255,0.12), rgba(109,214,255,0.10))"
                        }} />
                      )}

                      {isWin && (
                        <span style={{
                          position: "absolute",
                          left: "-40%",
                          top: 0,
                          width: "40%",
                          height: "100%",
                          background: "rgba(255,255,255,0.35)",
                          transform: "skewX(-18deg)",
                          animation: "shimmer 900ms ease-in-out infinite"
                        }} />
                      )}
                    </button>
                  );
                })}
              </div>

              {result && (
                <div style={{
                  padding: 14,
                  borderRadius: 18,
                  border: "1px solid rgba(27,27,31,0.10)",
                  background: "rgba(255,255,255,0.65)",
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  gap: 12
                }}>
                  <div style={{ color: "var(--muted)" }}>
                    {result === "win" && "Промокод на экране и в Telegram. Пользуйся с удовольствием!"}
                    {result === "lose" && "Бот пришлёт сообщение о результате. Можно попробовать снова."}
                    {result === "draw" && "Ничья — иногда это про выдержку. Попробуем ещё раз?"}
                  </div>
                  <button
                    onClick={resetGame}
                    style={{
                      padding: "10px 14px",
                      borderRadius: 14,
                      border: "1px solid rgba(27,27,31,0.12)",
                      background: "rgba(255,255,255,0.75)",
                      boxShadow: "var(--shadow2)",
                      cursor: "pointer"
                    }}
                  >
                    Сыграть ещё раз
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {toast && (
        <div style={{
          position: "fixed",
          bottom: 18,
          left: "50%",
          transform: "translateX(-50%)",
          padding: "10px 14px",
          borderRadius: 14,
          background: "rgba(27,27,31,0.82)",
          color: "white",
          boxShadow: "var(--shadow2)",
          animation: "pop 120ms ease-out",
          zIndex: 60
        }}>
          {toast}
        </div>
      )}

      <style jsx>{`
        @media (max-width: 820px) {
          button[aria-label^="cell-"] {
            height: 96px !important;
          }
        }
      `}</style>
    </div>
  );
}
