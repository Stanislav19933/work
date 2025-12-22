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
  const audioCtxRef = useRef(null);
  const ambientRef = useRef({ started: false, timer: null });
  const cpuTimerRef = useRef(null);

  const mounted = useRef(false);

  useEffect(() => {
    // Флаг «нажал Start» — чисто для UX, с защитой на случай запрета localStorage
    try {
      const started = localStorage.getItem("bot_started") === "1";
      setBotStartedHint(started);
    } catch {
      setBotStartedHint(false);
    }

    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2200);
    return () => clearTimeout(t);
  }, [toast]);

  useEffect(() => {
    function handleError(e) {
      e.preventDefault();
      setToast("Что-то пошло не так. Обнови страницу и попробуй снова.");
      return false;
    }
    window.addEventListener("error", handleError);
    window.addEventListener("unhandledrejection", handleError);
    return () => {
      window.removeEventListener("error", handleError);
      window.removeEventListener("unhandledrejection", handleError);
    };
  }, []);

  const r = useMemo(() => checkWinner(board), [board]);

  function getAudioCtx() {
    try {
      const ctx = audioCtxRef.current || new (window.AudioContext || window.webkitAudioContext)();
      audioCtxRef.current = ctx;
      return ctx;
    } catch {
      return null;
    }
  }

  function playTone(freq = 520, duration = 0.10, volume = 0.04, type = "sine") {
    const ctx = getAudioCtx();
    if (!ctx) return;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = type;
    osc.frequency.value = freq;
    gain.gain.value = volume;
    osc.connect(gain).connect(ctx.destination);
    const now = ctx.currentTime;
    osc.start(now);
    osc.stop(now + duration);
  }

  function startAmbient() {
    if (ambientRef.current.started) return;
    ambientRef.current.started = true;
    const ctx = getAudioCtx();
    if (!ctx) return;
    const playChord = () => {
      const base = 392; // G4
      [0, 4, 7].forEach((step, idx) => {
        playTone(base * Math.pow(2, step / 12), 0.6, 0.03 - idx * 0.004, "sine");
      });
    };
    playChord();
    ambientRef.current.timer = setInterval(playChord, 6400);
  }

  useEffect(() => {
    if (!mounted.current) return;

    // победы/ничьи
    if (r.winner === HUMAN) {
      setResult("win");
      setStatus("Победа! 💎");
      setWinLine(r.line);
      playTone(640, 0.18, 0.06);
      playTone(820, 0.22, 0.05);
      handleWinOnce();
      return;
    }
    if (r.winner === CPU) {
      setResult("lose");
      setStatus("Упс… давай ещё раз?");
      setWinLine(r.line);
      playTone(310, 0.18, 0.05);
      playTone(260, 0.14, 0.045);
      handleLoseOnce();
      return;
    }
    if (r.winner === "DRAW") {
      setResult("draw");
      setStatus("Ничья. Хочешь реванш?");
      setWinLine(null);
      playTone(520, 0.12, 0.05);
      return;
    }

    // ход компьютера
    if (cpuTimerRef.current) {
      clearTimeout(cpuTimerRef.current);
      cpuTimerRef.current = null;
    }
    if (!connectStepsOk) return;
    if (turn !== CPU) return;

    setBusy(true);
    setStatus("Компьютер думает…");
    cpuTimerRef.current = setTimeout(() => {
      setBoard(prev => {
        // если пока думали кто-то победил — не ходим
        const res = checkWinner(prev);
        if (res.winner) return prev;
        const idx = cpuMove(prev, 0.08);
        if (idx == null || prev[idx] !== EMPTY) return prev;
        const next = prev.slice();
        next[idx] = CPU;
        return next;
      });
      setTurn(HUMAN);
      setBusy(false);
      setStatus("Твой ход ✨");
      cpuTimerRef.current = null;
    }, 420);

    return () => {
      if (cpuTimerRef.current) {
        clearTimeout(cpuTimerRef.current);
        cpuTimerRef.current = null;
      }
    };
  }, [r.winner, turn, connectStepsOk]);

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
      setToast("Моя хорошая, подключи Telegram и нажми Start — тогда бот шепнёт тебе промокод.");
    }
  }

  async function handleLoseOnce() {
    if (outcomeSentRef.current.lose) return;
    outcomeSentRef.current.lose = true;
    try {
      await sendToTelegram({ result: "lose" });
    } catch (e) {
      setToast("Подключи Telegram и нажми Start у бота — он пришлёт тебе результат.");
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
    let moved = false;
    setBoard(prev => {
      if (prev[i] !== EMPTY) return prev;
      moved = true;
      const next = prev.slice();
      next[i] = HUMAN;
      return next;
    });
    if (!moved) return;
    startAmbient();
    playTone(540, 0.08, 0.04);
    setBusy(true);
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
    try {
      localStorage.setItem("bot_started", "1");
    } catch {
      // игнорируем, это только подсказка состояния
    }
    setBotStartedHint(true);
    // Здесь “быстрый юмор”: бот не читает мысли, зато читает /start.
    setToast("Открываем бота. Нажми Start — и вернись играть 💜");
  }

  const connectStepsOk = botStartedHint;
  const showGame = connectStepsOk;

  return (
    <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", padding: 18, background: "radial-gradient(circle at 10% 10%, rgba(255,227,255,0.28), transparent 32%), radial-gradient(circle at 80% 20%, rgba(214,245,255,0.32), transparent 32%), #f7f5ff" }}>
      <Confetti run={confettiRun} />

      {!showGame && (
        <div style={{ width: "min(900px, 100%)", display: "grid", gap: 16, animation: "fadeSlide 280ms ease" }}>
          <div style={{
            background: "linear-gradient(120deg, rgba(192,92,255,0.16), rgba(109,214,255,0.14))",
            border: "1px solid rgba(192,92,255,0.18)",
            borderRadius: "24px",
            boxShadow: "var(--shadow)",
            padding: 18,
            color: "rgba(24,24,28,0.92)",
            backdropFilter: "blur(10px)",
            textAlign: "center"
          }}>
            <div style={{ fontSize: 26, fontWeight: 750, letterSpacing: "-0.02em" }}>
              Открой бота и нажми Start
            </div>
            <div style={{ color: "var(--muted)", marginTop: 6, fontSize: 14.5, lineHeight: 1.45 }}>
              Один шаг, меньше минуты — потом сразу игра и промокод.
            </div>
          </div>

          <div style={{
            background: "var(--card)",
            border: "1px solid var(--cardBorder)",
            borderRadius: "22px",
            boxShadow: "var(--shadow)",
            padding: 18,
            display: "grid",
            gap: 12,
            backdropFilter: "blur(10px)",
            animation: "lift 280ms ease"
          }}>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 10 }}>
              <div style={{ fontSize: 20, fontWeight: 750 }}>Это нужно, чтобы бот написал тебе</div>
              <div style={{ padding: "8px 12px", borderRadius: 999, background: "rgba(192,92,255,0.14)", color: "rgba(99,63,143,0.9)", fontWeight: 700, fontSize: 12 }}>
                1 шаг
              </div>
            </div>

            <div style={{
              padding: 14,
              borderRadius: 18,
              border: "1px solid rgba(27,27,31,0.10)",
              background: "rgba(255,255,255,0.75)",
              boxShadow: "var(--shadow2)",
              animation: botStartedHint ? "pulse 820ms ease" : "fadeIn 240ms ease"
            }}>
              <div style={{ fontWeight: 750 }}>
                {botStartedHint ? "✅ Бот открыт и Start нажат" : "Открой бота и нажми Start"}
              </div>
              <div style={{ color: "var(--muted)", marginTop: 6, lineHeight: 1.4 }}>
                Жми кнопку, открой бота, нажми Start и возвращайся сюда — поле уже готово.
              </div>
              <div style={{ marginTop: 12, display: "flex", gap: 10, flexWrap: "wrap" }}>
                <a
                  href={`https://t.me/${BOT_USERNAME}?start=play`}
                  target="_blank"
                  rel="noreferrer"
                  onClick={markBotStarted}
                  style={{
                    padding: "12px 14px",
                    borderRadius: 16,
                    border: "1px solid rgba(192,92,255,0.28)",
                    background: "linear-gradient(90deg, rgba(192,92,255,0.18), rgba(109,214,255,0.16))",
                    boxShadow: "var(--shadow2)",
                    fontWeight: 750,
                    fontSize: 15
                  }}
                >
                  Открыть бота в Telegram
                </a>
              </div>
            </div>

            <div style={{
              padding: 12,
              borderRadius: 18,
              border: "1px solid rgba(27,27,31,0.10)",
              background: botStartedHint
                ? "linear-gradient(180deg, rgba(43,182,115,0.16), rgba(255,255,255,0.74))"
                : "rgba(255,255,255,0.74)",
              boxShadow: "var(--shadow2)"
            }}>
              <div style={{ fontWeight: 750 }}>
                {botStartedHint ? "Готово! Возвращайся — игра уже открыта ❤️" : "После Start вернись сюда, поле откроется ❤️"}
              </div>
              <div style={{ color: "var(--muted)", marginTop: 6 }}>
                Победа подарит промокод, бот шепнёт его тебе в Telegram.
              </div>
            </div>
          </div>
        </div>
      )}

      {showGame && (
        <div style={{ width: "min(980px, 100%)", display: "grid", gap: 16, animation: "fadeSlide 320ms ease" }}>
          <div style={{
            background: "linear-gradient(120deg, rgba(192,92,255,0.16), rgba(109,214,255,0.12))",
            border: "1px solid rgba(192,92,255,0.18)",
            borderRadius: "24px",
            boxShadow: "var(--shadow)",
            padding: 20,
            color: "rgba(24,24,28,0.92)",
            backdropFilter: "blur(10px)",
            position: "relative",
            overflow: "hidden"
          }}>
            <div style={{ position: "absolute", inset: "-20% 50% auto -20%", height: 160, background: "radial-gradient(circle, rgba(255,255,255,0.35), transparent 45%)", filter: "blur(30px)", opacity: 0.8 }} />
            <div style={{ position: "absolute", inset: "auto -20% -40% 20%", height: 200, background: "radial-gradient(circle, rgba(192,92,255,0.22), transparent 55%)", filter: "blur(32px)", opacity: 0.8 }} />
            <div style={{ position: "relative" }}>
              <div style={{ fontSize: 28, fontWeight: 750, letterSpacing: "-0.02em" }}>
                Крестики-нолики с подарком для тебя
              </div>
              <div style={{ color: "var(--muted)", marginTop: 6, fontSize: 15, lineHeight: 1.45 }}>
                Уже можно играть! Победа дарит промокод, а бот сразу шлёт его в Telegram.
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
            animation: "lift 320ms ease"
          }}>
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
              gap: 12
            }}>
              <div style={{
                display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12,
                padding: 14,
                borderRadius: 18,
                background: "rgba(255,255,255,0.65)",
                border: "1px solid rgba(27,27,31,0.10)",
                animation: "fadeIn 220ms ease"
              }}>
                <div>
                  <div style={{ fontWeight: 650 }}>{status}</div>
                  <div style={{ color: "var(--muted)", marginTop: 4 }}>
                    Ходим по очереди: ты — потом компьютер. Всё честно.
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
                border: "1px solid rgba(27,27,31,0.10)",
                boxShadow: "0 12px 30px rgba(139,92,246,0.12)",
                animation: "pulseBg 1600ms ease-in-out infinite alternate"
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
                        overflow: "hidden",
                        animation: "popSoft 180ms ease"
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
                  gap: 12,
                  animation: "fadeIn 220ms ease"
                }}>
                  <div style={{ color: "var(--muted)" }}>
                    {result === "win" && "Моя хорошая, промокод на экране и уже летит в Telegram. Пользуйся с удовольствием!"}
                    {result === "lose" && "Сегодня не повезло, но бот уже написал в Telegram. Сыграем ещё?"}
                    {result === "draw" && "Ничья — стильный результат. Давай ещё разок?"}
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
      )}

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
        @keyframes fadeIn { from { opacity: 0; transform: translateY(6px);} to { opacity: 1; transform: translateY(0);} }
        @keyframes fadeSlide { from { opacity: 0; transform: translateY(10px);} to { opacity: 1; transform: translateY(0);} }
        @keyframes lift { from { opacity: 0; transform: translateY(12px) scale(0.98);} to { opacity: 1; transform: translateY(0) scale(1);} }
        @keyframes popSoft { from { transform: scale(0.98);} to { transform: scale(1);} }
        @keyframes pulseBg { from { box-shadow: 0 12px 30px rgba(139,92,246,0.10);} to { box-shadow: 0 16px 36px rgba(79,70,229,0.18);} }
        @media (max-width: 820px) {
          button[aria-label^="cell-"] {
            height: 96px !important;
          }
        }
      `}</style>
    </div>
  );
}
