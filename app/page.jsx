"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { checkWinner, cpuMove, EMPTY, HUMAN, CPU } from "@/lib/game";
import { generatePromoCode5 } from "@/lib/promo";

const BOT_USERNAME = "cool_woman_bot";

function Confetti({ run }) {
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
    <div style={{ position: "fixed", inset: 0, pointerEvents: "none", overflow: "hidden", zIndex: 50 }}>
      {parts.map(s => (
        <span
          key={s.id}
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

function useAudio() {
  const ctxRef = useRef(null);

  function ensure() {
    if (!ctxRef.current) {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return null;
      ctxRef.current = new AudioCtx();
    }
    if (ctxRef.current.state === "suspended") ctxRef.current.resume();
    return ctxRef.current;
  }

  function playTone(freq, duration = 0.12, volume = 0.1, type = "triangle") {
    const ctx = ensure();
    if (!ctx) return;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = type;
    osc.frequency.value = freq;
    gain.gain.value = volume;
    osc.connect(gain).connect(ctx.destination);
    const now = ctx.currentTime;
    osc.start(now);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + duration);
    osc.stop(now + duration + 0.02);
  }

  function playChord(freqs, duration = 0.22, volume = 0.08) {
    freqs.forEach(f => playTone(f, duration, volume, "sine"));
  }

  return { playTone, playChord };
}

export default function Page() {
  const [board, setBoard] = useState(Array(9).fill(EMPTY));
  const [turn, setTurn] = useState(HUMAN);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState("Нажми на кнопку и подключи Telegram");
  const [result, setResult] = useState(null); // "win" | "lose" | "draw"
  const [winLine, setWinLine] = useState(null);
  const [promo, setPromo] = useState(null);
  const [toast, setToast] = useState(null);
  const [confettiRun, setConfettiRun] = useState(false);
  const [connected, setConnected] = useState(false);
  const [botStarted, setBotStarted] = useState(false);

  const mounted = useRef(false);
  const cpuTimer = useRef(null);
  const { playTone, playChord } = useAudio();

  const r = useMemo(() => checkWinner(board), [board]);

  const hasTgCookie = () => (typeof document !== "undefined" && document.cookie.includes("tg_uid="));

  // Показываем всплывашки коротко
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2200);
    return () => clearTimeout(t);
  }, [toast]);

  // Глобальный cleanup таймера CPU
  useEffect(() => () => {
    if (cpuTimer.current) clearTimeout(cpuTimer.current);
  }, []);

  // Подключение через Telegram WebApp: проверяем initData и сохраняем chat_id на сервере
  useEffect(() => {
    async function initWebApp() {
      try {
        const tg = window.Telegram?.WebApp;
        if (!tg || !tg.initData) return;
        const resp = await fetch("/api/telegram/auth", {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify({ initData: tg.initData })
        });
        const data = await resp.json().catch(() => ({}));
        if (!resp.ok) throw new Error(data?.error || "auth_failed");
        setConnected(true);
        setBotStarted(true); // WebApp запускается после Start
        setStatus("Подключено. Можно играть!");
        try { localStorage.setItem("bot_started", "1"); } catch { /* ignore */ }
        setToast("Telegram подключён через WebApp.");
      } catch (e) {
        setToast("Не удалось подключить через Telegram WebApp. Открой бота и попробуй снова.");
      }
    }

    try {
      const started = localStorage.getItem("bot_started") === "1";
      setBotStarted(started);
    } catch {
      setBotStarted(false);
    }

    initWebApp();
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  // Регулярно проверяем, не появился ли cookie после запуска в Telegram
  useEffect(() => {
    if (connected) return;
    const t = setInterval(() => {
      if (hasTgCookie()) {
        setConnected(true);
        setStatus("Подключено. Можно играть!");
      }
    }, 1500);
    return () => clearInterval(t);
  }, [connected]);

  // Игра: реакции на победу/проигрыш/ничью и ход бота
  useEffect(() => {
    if (!mounted.current) return undefined;

    let cleanup;

    if (r.winner === HUMAN) {
      setResult("win");
      setStatus("Победа! Промокод уже на экране");
      setWinLine(r.line);
      playChord([880, 1175, 1568], 0.3, 0.1);
      handleWinOnce();
      return cleanup;
    }
    if (r.winner === CPU) {
      setResult("lose");
      setStatus("Компьютер взял этот раунд. Попробуем ещё?");
      setWinLine(r.line);
      playTone(220, 0.4, 0.08, "sawtooth");
      handleLoseOnce();
      return cleanup;
    }
    if (r.winner === "DRAW") {
      setResult("draw");
      setStatus("Ничья. Можно играть ещё!");
      setWinLine(null);
      playChord([523, 659], 0.18, 0.07);
      return cleanup;
    }

    if (turn === CPU && !result) {
      setBusy(true);
      setStatus("Компьютер думает…");
      const t = setTimeout(() => {
        setBoard(prev => {
          const idx = cpuMove(prev, 0.08);
          const fallback = prev.findIndex(cell => cell === EMPTY);
          const move = (idx != null && prev[idx] === EMPTY) ? idx : fallback;
          if (move == null || move < 0) return prev;
          const next = prev.slice();
          next[move] = CPU;
          playTone(520, 0.12, 0.07);
          return next;
        });
        setTurn(HUMAN);
        setBusy(false);
        setStatus("Твой ход ✨");
        cpuTimer.current = null;
      }, 420);
      cpuTimer.current = t;
      cleanup = () => clearTimeout(t);
    }

    return cleanup;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [r.winner, turn, result]);

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

    try {
      await sendToTelegram({ result: "win", code });
    } catch (e) {
      setToast("Бот не смог написать. Убедись, что ты нажал Start в Telegram.");
    }
  }

  async function handleLoseOnce() {
    if (outcomeSentRef.current.lose) return;
    outcomeSentRef.current.lose = true;
    try {
      await sendToTelegram({ result: "lose" });
    } catch (e) {
      setToast("Чтобы бот написал, открой его и нажми Start.");
    }
  }

  function openBotForStart() {
    const url = `https://t.me/${BOT_USERNAME}?start=play`;
    const win = window.open(url, "_blank", "noopener,noreferrer");
    if (!win) setToast("Открой бота вручную: https://t.me/cool_woman_bot");
    try {
      localStorage.setItem("bot_started", "1");
      setBotStarted(true);
      setStatus("Открываю бота... Подтверди запуск в Telegram.");
      setConnected(hasTgCookie());
    } catch {
      setBotStarted(true);
    }
  }

  function resetGame() {
    if (cpuTimer.current) {
      clearTimeout(cpuTimer.current);
      cpuTimer.current = null;
    }
    setBoard(Array(9).fill(EMPTY));
    setTurn(HUMAN);
    setBusy(false);
    setStatus(connected ? "Твой ход ✨" : "Нажми на кнопку и подключи Telegram");
    setResult(null);
    setWinLine(null);
    setPromo(null);
    setConfettiRun(false);
    outcomeSentRef.current = { win: false, lose: false };
  }

  function onCell(i) {
    if (busy) return;
    if (result) return;
    if (turn !== HUMAN) return;
    if (!connected) {
      setToast("Подключи Telegram, тогда бот пришлёт результат.");
      return;
    }
    setBoard(prev => {
      if (prev[i] !== EMPTY) return prev;
      const next = prev.slice();
      next[i] = HUMAN;
      playTone(420, 0.1, 0.08);
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

  const connectedText = connected
    ? "Telegram подключён. Бот сможет прислать результат."
    : "Открой бота через кнопку ниже, Telegram сам подтвердит подключение.";

  return (
    <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", padding: 18, background: "radial-gradient(900px 700px at 20% 10%, rgba(192, 92, 255, 0.18), transparent 60%), radial-gradient(900px 700px at 80% 20%, rgba(109, 214, 255, 0.20), transparent 60%), radial-gradient(900px 700px at 60% 85%, rgba(255, 77, 109, 0.14), transparent 60%), linear-gradient(180deg, #0b1021, #0c1429)" }}>
      <Confetti run={confettiRun} />

      <div style={{ width: "min(1200px, 100%)", display: "grid", gap: 18, gridTemplateColumns: "1.1fr 0.9fr" }}>
        <div style={{ background: "rgba(255,255,255,0.07)", border: "1px solid rgba(255,255,255,0.12)", borderRadius: 22, boxShadow: "0 15px 60px rgba(0,0,0,0.35)", padding: 18, backdropFilter: "blur(12px)", position: "relative", overflow: "hidden" }}>
          <div style={{ position: "absolute", inset: 0, background: "radial-gradient(circle at 20% 20%, rgba(192,92,255,0.18), transparent 40%), radial-gradient(circle at 80% 10%, rgba(109,214,255,0.18), transparent 45%)", pointerEvents: "none" }} />
          <div style={{ position: "relative", display: "flex", justifyContent: "space-between", gap: 12, alignItems: "center" }}>
            <div>
              <div style={{ fontSize: 30, fontWeight: 760, letterSpacing: "-0.02em", color: "#f5f7ff" }}>Крестики-нолики 2.0</div>
              <div style={{ color: "rgba(255,255,255,0.75)", marginTop: 4, fontSize: 15 }}>
                Один клик — бот подключён. Победа = промокод, бот пришлёт сообщение сам.
              </div>
            </div>
            <button
              onClick={resetGame}
              style={{ padding: "10px 14px", borderRadius: 14, border: "1px solid rgba(255,255,255,0.16)", background: "rgba(255,255,255,0.12)", color: "white", boxShadow: "0 10px 30px rgba(0,0,0,0.25)", cursor: "pointer", backdropFilter: "blur(6px)" }}
            >
              Сбросить
            </button>
          </div>

          <div style={{ position: "relative", marginTop: 16, display: "grid", gridTemplateColumns: "1fr", gap: 12 }}>
            <div style={{ padding: 14, borderRadius: 16, border: "1px solid rgba(255,255,255,0.18)", background: "rgba(255,255,255,0.08)", boxShadow: "0 12px 30px rgba(0,0,0,0.25)", display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: 17, color: "#f7f8ff" }}>{status}</div>
                <div style={{ color: "rgba(255,255,255,0.7)", marginTop: 4, fontSize: 14 }}>{connectedText}</div>
              </div>

              {promo && (
                <button
                  onClick={copyPromo}
                  style={{ padding: "10px 12px", borderRadius: 14, border: "1px solid rgba(192,92,255,0.28)", background: "linear-gradient(90deg, rgba(192,92,255,0.2), rgba(109,214,255,0.18))", cursor: "pointer", color: "#120b1f", fontWeight: 750, boxShadow: "0 10px 35px rgba(192,92,255,0.25)", animation: "glow 1.4s ease-in-out infinite" }}
                  title="Скопировать промокод"
                >
                  {promo} · копировать
                </button>
              )}
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 12, padding: 14, borderRadius: 22, background: "rgba(255,255,255,0.06)", border: "1px solid rgba(255,255,255,0.14)", boxShadow: "inset 0 1px 0 rgba(255,255,255,0.12)" }}>
              {board.map((v, i) => {
                const isWin = winLine?.includes(i);
                const isAvailable = v === EMPTY && !result && turn === HUMAN && !busy;
                return (
                  <button
                    key={i}
                    onClick={() => onCell(i)}
                    disabled={busy || !!result || turn !== HUMAN}
                    style={{
                      height: 120,
                      borderRadius: 18,
                      border: "1px solid rgba(255,255,255,0.16)",
                      background: isWin
                        ? "linear-gradient(180deg, rgba(192,92,255,0.3), rgba(109,214,255,0.18))"
                        : "rgba(255,255,255,0.08)",
                      boxShadow: isWin
                        ? "0 0 0 2px rgba(192,92,255,0.35), 0 14px 30px rgba(0,0,0,0.25)"
                        : "0 12px 26px rgba(0,0,0,0.22)",
                      cursor: isAvailable ? "pointer" : "not-allowed",
                      position: "relative",
                      overflow: "hidden",
                      transition: "transform 120ms ease, filter 120ms ease, box-shadow 140ms ease",
                      transform: isAvailable ? "translateY(-1px)" : "none",
                      color: "white"
                    }}
                    aria-label={`cell-${i}`}
                  >
                    <span style={{ display: "inline-block", fontSize: 54, fontWeight: 780, letterSpacing: "-0.05em", transform: v ? "scale(1)" : "scale(0.92)", opacity: v ? 1 : 0, animation: v ? "pop 140ms ease-out" : "none", color: v === HUMAN ? "#fdf5ff" : "#b98eff", textShadow: v ? "0 4px 20px rgba(0,0,0,0.35)" : "none" }}>
                      {v ?? ""}
                    </span>

                    {!v && !result && turn === HUMAN && !busy && (
                      <span style={{ position: "absolute", inset: 0, opacity: 0.12, background: "linear-gradient(120deg, rgba(192,92,255,0.25), rgba(109,214,255,0.22))" }} />
                    )}

                    {isWin && (
                      <span style={{ position: "absolute", left: "-40%", top: 0, width: "40%", height: "100%", background: "rgba(255,255,255,0.35)", transform: "skewX(-18deg)", animation: "shimmer 900ms ease-in-out infinite" }} />
                    )}
                  </button>
                );
              })}
            </div>

            {result && (
              <div style={{ padding: 14, borderRadius: 16, border: "1px solid rgba(255,255,255,0.16)", background: "rgba(255,255,255,0.08)", display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, color: "rgba(255,255,255,0.78)" }}>
                <div>
                  {result === "win" && "Промокод на экране. Я отправил его и в Telegram (если подключён)."}
                  {result === "lose" && "Отправил сообщение о проигрыше в Telegram (если подключён)."}
                  {result === "draw" && "Ничья — отличный повод сыграть ещё."}
                </div>
                <button
                  onClick={resetGame}
                  style={{ padding: "10px 14px", borderRadius: 14, border: "1px solid rgba(255,255,255,0.16)", background: "rgba(255,255,255,0.14)", color: "white", cursor: "pointer", boxShadow: "0 10px 30px rgba(0,0,0,0.22)" }}
                >
                  Реванш
                </button>
              </div>
            )}
          </div>
        </div>

        <div style={{ background: "rgba(255,255,255,0.07)", border: "1px solid rgba(255,255,255,0.12)", borderRadius: 22, boxShadow: "0 15px 60px rgba(0,0,0,0.35)", padding: 18, backdropFilter: "blur(12px)", color: "#f2f5ff", display: "grid", gap: 12, alignSelf: "start" }}>
          <div style={{ fontSize: 18, fontWeight: 760 }}>Подключение через Bot WebApp</div>
          <div style={{ color: "rgba(255,255,255,0.75)", lineHeight: 1.5 }}>
            Открой бота в Telegram. WebApp сам передаст данные — никаких номеров и СМС.
          </div>

          <div style={{ display: "grid", gap: 10, padding: 14, borderRadius: 18, border: "1px solid rgba(255,255,255,0.18)", background: "linear-gradient(135deg, rgba(192,92,255,0.18), rgba(109,214,255,0.16))", boxShadow: "0 14px 35px rgba(0,0,0,0.28)" }}>
            <div style={{ fontWeight: 700, color: "#120b1f" }}>Шаг 1. Открыть бота</div>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 10 }}>
              <button
                onClick={() => {
                  openBotForStart();
                  setToast("Открыл бота. Telegram сам подтвердит подключение.");
                }}
                style={{ padding: "10px 12px", borderRadius: 14, border: "1px solid rgba(192,92,255,0.28)", background: "linear-gradient(90deg, rgba(192,92,255,0.2), rgba(109,214,255,0.18))", color: "#120b1f", fontWeight: 750, cursor: "pointer", boxShadow: "0 10px 30px rgba(0,0,0,0.25)" }}
              >
                Открыть бота
              </button>
              <a
                href={`https://t.me/${BOT_USERNAME}?startapp=play`}
                target="_blank"
                rel="noreferrer"
                style={{ padding: "10px 12px", borderRadius: 14, border: "1px solid rgba(192,92,255,0.28)", background: "rgba(255,255,255,0.7)", color: "#120b1f", fontWeight: 750, boxShadow: "0 10px 30px rgba(0,0,0,0.18)" }}
              >
                Запустить игру в Telegram
              </a>
            </div>
            <div style={{ fontWeight: 700, color: "#120b1f" }}>Шаг 2. Подтверждение</div>
            <div style={{ color: "rgba(15,12,30,0.8)", fontSize: 13 }}>
              Если кнопка не сработала, открой вручную: <a href={`https://t.me/${BOT_USERNAME}?start=play`} target="_blank" rel="noreferrer" style={{ color: "#120b1f", fontWeight: 760 }}>@{BOT_USERNAME}</a>. После запуска внутри Telegram статус станет зелёным.
            </div>
            <button
              onClick={() => {
                try {
                  localStorage.removeItem("bot_started");
                } catch { /* ignore */ }
                setConnected(false);
                setBotStarted(false);
                setStatus("Нажми на кнопку и подключи Telegram");
                setToast("Сбросили подключение. Открой бота снова.");
              }}
              style={{ padding: "10px 12px", borderRadius: 14, border: "1px solid rgba(0,0,0,0.18)", background: "rgba(255,255,255,0.6)", color: "#120b1f", cursor: "pointer" }}
            >
              Сбросить подключение
            </button>
            <div style={{ fontSize: 13, color: "rgba(255,255,255,0.6)", lineHeight: 1.4 }}>
              После запуска внутри Telegram статус подключения станет зелёным, и бот сможет писать.
            </div>
          </div>

          <div style={{ display: "grid", gap: 10, fontSize: 14, color: "rgba(255,255,255,0.75)" }}>
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <span style={{ width: 10, height: 10, borderRadius: 10, background: connected ? "#2bb673" : "#ffb347", boxShadow: connected ? "0 0 12px #2bb673" : "0 0 12px #ffb347" }} />
              {connected ? "Telegram подключён" : "Ждём входа через Telegram"}
            </div>
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <span style={{ width: 10, height: 10, borderRadius: 10, background: botStarted ? "#2bb673" : "#ffb347", boxShadow: botStarted ? "0 0 12px #2bb673" : "0 0 12px #ffb347" }} />
              {botStarted ? "Бот открыт (Start нажат)" : "Нажми Start у бота"}
            </div>
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <span style={{ width: 10, height: 10, borderRadius: 10, background: result ? "#8dc6ff" : "#fff", boxShadow: "0 0 12px rgba(255,255,255,0.55)" }} />
              {result ? "Игра завершена — можно начать новую" : "Сыграй и поймай промокод"}
            </div>
          </div>

            <div style={{ fontSize: 13, color: "rgba(255,255,255,0.6)", lineHeight: 1.4 }}>
              После запуска внутри Telegram статус подключения станет зелёным, и бот сможет писать.
            </div>
          </div>
        </div>

      {toast && (
        <div style={{ position: "fixed", bottom: 18, left: "50%", transform: "translateX(-50%)", padding: "10px 14px", borderRadius: 14, background: "rgba(15,15,20,0.9)", color: "white", boxShadow: "0 10px 30px rgba(0,0,0,0.35)", animation: "pop 120ms ease-out", zIndex: 60 }}>
          {toast}
        </div>
      )}

      <style jsx>{`
        @keyframes pop { 0% { transform: scale(0.94); opacity: 0; } 100% { transform: scale(1); opacity: 1; } }
        @keyframes glow { 0% { box-shadow: 0 0 0 rgba(192, 92, 255, 0.0); } 50% { box-shadow: 0 0 24px rgba(192, 92, 255, 0.45); } 100% { box-shadow: 0 0 0 rgba(192, 92, 255, 0.0); } }
        @keyframes shimmer { 0% { transform: translateX(-60%) skewX(-18deg); opacity: 0; } 30% { opacity: 1; } 100% { transform: translateX(60%) skewX(-18deg); opacity: 0; } }
        @media (max-width: 1024px) { div[style*="grid-template-columns: 1.1fr 0.9fr"] { grid-template-columns: 1fr !important; } button[aria-label^="cell-"] { height: 100px !important; } }
      `}</style>
    </div>
  );
}
