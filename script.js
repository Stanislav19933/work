const state = {
  player: 'X',
  bot: 'O',
  board: Array(9).fill(null),
  chatId: null,
  gameOver: false,
  sending: false,
};

const winLines = [
  [0, 1, 2],
  [3, 4, 5],
  [6, 7, 8],
  [0, 3, 6],
  [1, 4, 7],
  [2, 5, 8],
  [0, 4, 8],
  [2, 4, 6],
];

const cells = Array.from(document.querySelectorAll('.cell'));
const turnLabel = document.getElementById('turnLabel');
const instruction = document.getElementById('instruction');
const statusEl = document.getElementById('telegramStatus');
const toastEl = document.getElementById('toast');
const resultEl = document.getElementById('result');
const resultEyebrowEl = document.getElementById('resultEyebrow');
const resultTitleEl = document.getElementById('resultTitle');
const resultTextEl = document.getElementById('resultText');

function playTone(freq, duration = 0.12, type = 'sine') {
  if (!window.AudioContext && !window.webkitAudioContext) return;
  const AudioCtx = window.AudioContext || window.webkitAudioContext;
  const ctx = new AudioCtx();
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  osc.type = type;
  osc.frequency.value = freq;
  osc.connect(gain);
  gain.connect(ctx.destination);
  osc.start();
  gain.gain.setValueAtTime(0.2, ctx.currentTime);
  gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + duration);
  osc.stop(ctx.currentTime + duration + 0.02);
}

function setStatus(message, tone = 'info') {
  statusEl.textContent = message;
  statusEl.className = `status status--${tone}`;
}

function showToast(message, tone = 'success') {
  toastEl.textContent = message;
  toastEl.className = `toast is-visible toast--${tone}`;
  setTimeout(() => toastEl.classList.remove('is-visible'), 2600);
}

function getChatIdFromTelegram() {
  const tg = window.Telegram?.WebApp;
  if (!tg) {
    setStatus('Открой игру через кнопку бота внутри Telegram, чтобы я увидела твой чат.', 'warn');
    return null;
  }

  try {
    tg.ready();
    tg.expand?.();
    const user = tg.initDataUnsafe?.user;
    if (user?.id) {
      state.chatId = user.id;
      setStatus(`Нашла тебя в Telegram: chat_id ${user.id}. Можем играть!`, 'ok');
      instruction.textContent = 'Отлично! Теперь просто выигрывай — и я пришлю подарок.';
      return user.id;
    }
    setStatus('Не вижу chat_id. Нажми «Открыть Telegram бота» и зайди через кнопку.', 'warn');
    return null;
  } catch (error) {
    setStatus('Не получилось подключиться к Telegram. Попробуй обновить страницу из бота.', 'danger');
    console.error('Telegram init error:', error);
    return null;
  }
}

function renderBoard() {
  cells.forEach((cell, index) => {
    cell.textContent = state.board[index] || '';
    cell.disabled = Boolean(state.board[index]) || state.gameOver;
  });
}

function checkWinner(symbol) {
  return winLines.some(line => line.every(idx => state.board[idx] === symbol));
}

function checkDraw() {
  return state.board.every(Boolean);
}

function findLineMove(symbol) {
  for (const line of winLines) {
    const marks = line.map(idx => state.board[idx]);
    const filled = marks.filter(Boolean).length;
    if (filled === 2 && marks.filter(mark => mark === symbol).length === 2) {
      const emptyIndex = line.find(idx => !state.board[idx]);
      if (emptyIndex !== undefined) return emptyIndex;
    }
  }
  return null;
}

function botMove() {
  const winningMove = findLineMove(state.bot);
  if (winningMove !== null) return winningMove;
  const blockingMove = findLineMove(state.player);
  if (blockingMove !== null) return blockingMove;
  if (!state.board[4]) return 4;
  return state.board.findIndex(cell => !cell);
}

async function handleResult(outcome) {
  state.gameOver = true;
  let eyebrow = 'Молодец!';
  let title = '';
  let text = '';

  if (outcome === 'win') {
    const code = Math.floor(10000 + Math.random() * 90000).toString();
    eyebrow = 'Ты победила!';
    title = 'Лови твой промокод ✨';
    text = `Вот подарок: ${code}. Я уже отправила его в Telegram.`;
    await sendTelegramMessage(`Ура! Ты выиграла 🎉 Твой промокод: ${code}`);
    playTone(880, 0.18, 'triangle');
  } else if (outcome === 'lose') {
    eyebrow = 'Ничего страшного';
    title = 'Сегодня не вышло';
    text = 'Я уже написала тебе в Telegram, что жду реванш. Ты сможешь!';
    await sendTelegramMessage('Сегодня победа за мной, но ты молодец! Загляни ещё раз и забери подарок.');
    playTone(320, 0.2, 'sine');
  } else {
    eyebrow = 'Почти!';
    title = 'Ничья';
    text = 'Мы сыграли ровно. Попробуем ещё?';
    await sendTelegramMessage('Ничья! Давай сыграем ещё раз — подарок тебя ждёт.');
    playTone(540, 0.15, 'sine');
  }

  resultEyebrowEl.textContent = eyebrow;
  resultTitleEl.textContent = title;
  resultTextEl.textContent = text;
  resultEl.classList.add('is-visible');
}

async function sendTelegramMessage(text) {
  if (!state.chatId) {
    showToast('Сначала открой игру через бота, чтобы я увидела твой chat_id.', 'warn');
    return;
  }
  if (state.sending) return;

  state.sending = true;
  try {
    const response = await fetch('/api/send', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chatId: state.chatId, text }),
    });

    if (!response.ok) {
      throw new Error('Ошибка отправки');
    }
    showToast('Сообщение улетело в Telegram 💌', 'success');
  } catch (error) {
    console.error(error);
    showToast('Не получилось отправить. Попробуй снова чуть позже.', 'danger');
  } finally {
    state.sending = false;
  }
}

function handleCellClick(index) {
  if (state.gameOver || state.board[index]) return;
  if (!state.chatId) {
    showToast('Мне нужен твой chat_id — открой игру через бота.', 'warn');
    return;
  }

  state.board[index] = state.player;
  renderBoard();
  playTone(660, 0.1, 'sine');

  if (checkWinner(state.player)) {
    turnLabel.textContent = 'Ты молодец!';
    handleResult('win');
    return;
  }

  if (checkDraw()) {
    turnLabel.textContent = 'Ничья — сыграем ещё?';
    handleResult('draw');
    return;
  }

  turnLabel.textContent = 'Мой ход...';
  setTimeout(() => {
    const move = botMove();
    if (move >= 0) {
      state.board[move] = state.bot;
      renderBoard();
      playTone(480, 0.1, 'square');
    }

    if (checkWinner(state.bot)) {
      turnLabel.textContent = 'Сегодня я выиграла';
      handleResult('lose');
      return;
    }

    if (checkDraw()) {
      turnLabel.textContent = 'Ничья — реванш?';
      handleResult('draw');
      return;
    }

    turnLabel.textContent = 'Твой ход: ставь крестик';
  }, 300);
}

function resetGame() {
  state.board = Array(9).fill(null);
  state.gameOver = false;
  renderBoard();
  turnLabel.textContent = 'Твой ход: ставь крестик';
  resultEl.classList.remove('is-visible');
}

function wireEvents() {
  cells.forEach(cell => {
    cell.addEventListener('click', () => handleCellClick(Number(cell.dataset.index)));
  });

  document.getElementById('restart').addEventListener('click', resetGame);
  document.getElementById('playAgain').addEventListener('click', resetGame);
  document.getElementById('refreshTelegram').addEventListener('click', getChatIdFromTelegram);
}

function init() {
  wireEvents();
  renderBoard();
  getChatIdFromTelegram();
}

document.addEventListener('DOMContentLoaded', init);
