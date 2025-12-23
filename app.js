const boardEl = document.getElementById('board');
const hintEl = document.getElementById('hint');
const resultScreen = document.getElementById('result-screen');
const resultTitle = document.getElementById('result-title');
const resultMessage = document.getElementById('result-message');
const playAgainBtn = document.getElementById('play-again');
const connectBtn = document.getElementById('connect-telegram');
const telegramStatus = document.getElementById('telegram-status');
const soundToggleBtn = document.getElementById('sound-toggle');

const config = {
  botUsername: null,
  siteUrl: window.location.origin,
};

let board = Array(9).fill(null);
let gameOver = false;
let soundEnabled = true;
let audioCtx = null;

const lines = [
  [0, 1, 2],
  [3, 4, 5],
  [6, 7, 8],
  [0, 3, 6],
  [1, 4, 7],
  [2, 5, 8],
  [0, 4, 8],
  [2, 4, 6],
];

function initBoard() {
  boardEl.innerHTML = '';
  board = Array(9).fill(null);
  gameOver = false;
  hintEl.textContent = 'Ваш ход — ставим крестик.';
  for (let i = 0; i < 9; i += 1) {
    const cell = document.createElement('button');
    cell.className = 'cell';
    cell.type = 'button';
    cell.dataset.index = i;
    cell.addEventListener('click', () => handlePlayerMove(i, cell));
    boardEl.appendChild(cell);
  }
}

function handlePlayerMove(index, cell) {
  if (gameOver || board[index]) {
    return;
  }
  board[index] = 'X';
  cell.textContent = 'X';
  cell.classList.add('taken');
  playSound('click');

  if (checkGameEnd('X')) {
    return;
  }

  hintEl.textContent = 'Ход компьютера...';
  setTimeout(() => {
    computerMove();
    if (!gameOver) {
      hintEl.textContent = 'Ваш ход — ставим крестик.';
    }
  }, 350);
}

function computerMove() {
  const move = chooseMove('O');
  if (move === null) {
    return;
  }
  board[move] = 'O';
  const cell = boardEl.querySelector(`[data-index="${move}"]`);
  if (cell) {
    cell.textContent = 'O';
    cell.classList.add('taken');
  }
  playSound('click');
  checkGameEnd('O');
}

function chooseMove(player) {
  const opponent = player === 'X' ? 'O' : 'X';
  const winning = findWinningMove(player);
  if (winning !== null) return winning;
  const block = findWinningMove(opponent);
  if (block !== null) return block;
  if (!board[4]) return 4;
  const corners = [0, 2, 6, 8].filter((i) => !board[i]);
  if (corners.length) return corners[Math.floor(Math.random() * corners.length)];
  const available = board
    .map((value, idx) => (value ? null : idx))
    .filter((value) => value !== null);
  return available.length ? available[Math.floor(Math.random() * available.length)] : null;
}

function findWinningMove(player) {
  for (const line of lines) {
    const values = line.map((idx) => board[idx]);
    const playerCount = values.filter((val) => val === player).length;
    const emptyIndex = line.find((idx) => !board[idx]);
    if (playerCount === 2 && emptyIndex !== undefined) {
      return emptyIndex;
    }
  }
  return null;
}

function checkGameEnd(player) {
  if (hasWinner(player)) {
    gameOver = true;
    if (player === 'X') {
      showResult('win');
    } else {
      showResult('lose');
    }
    return true;
  }

  if (board.every((cell) => cell)) {
    gameOver = true;
    showResult('draw');
    return true;
  }
  return false;
}

function hasWinner(player) {
  return lines.some((line) => line.every((idx) => board[idx] === player));
}

function showResult(result) {
  let message = '';
  if (result === 'win') {
    const promoCode = generatePromoCode();
    resultTitle.textContent = 'Победа!';
    message = `Вы выиграли 🎉 Ваш промокод: ${promoCode}`;
    resultMessage.textContent = message;
    playSound('win');
    notifyTelegram('win', promoCode);
  } else if (result === 'lose') {
    resultTitle.textContent = 'Увы, сегодня не ваш день';
    message = 'Не сдавайтесь — попробуйте ещё раз!';
    resultMessage.textContent = message;
    playSound('lose');
    notifyTelegram('lose');
  } else {
    resultTitle.textContent = 'Ничья';
    message = 'Вы были очень близки. Хотите сыграть ещё?';
    resultMessage.textContent = message;
  }
  resultScreen.classList.remove('hidden');
}

function generatePromoCode() {
  return Math.floor(Math.random() * 100000)
    .toString()
    .padStart(5, '0');
}

function resetGame() {
  resultScreen.classList.add('hidden');
  initBoard();
}

function getStoredChatId() {
  return localStorage.getItem('tg_chat_id');
}

function updateTelegramStatus() {
  const chatId = getStoredChatId();
  if (chatId) {
    telegramStatus.textContent = 'Telegram подключён ✅';
  } else {
    telegramStatus.textContent = 'Telegram не подключён';
  }
}

function notifyTelegram(event, promoCode) {
  const chatId = getStoredChatId();
  if (!chatId) return;

  const payload = { chat_id: chatId, event };
  if (event === 'win') {
    payload.promoCode = promoCode;
  }

  fetch('/api/notify', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }).catch(() => {});
}

function parseTelegramParams() {
  const params = new URLSearchParams(window.location.search);
  const chatId = params.get('chat_id');
  const state = params.get('state');
  if (chatId && state) {
    localStorage.setItem('tg_chat_id', chatId);
    localStorage.setItem('tg_state', state);
    history.replaceState({}, document.title, window.location.pathname);
  }
  updateTelegramStatus();
}

function generateState() {
  const length = 20;
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  const base64 = btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return base64.slice(0, length + 2);
}

function connectTelegram() {
  if (!config.botUsername) {
    alert('Бот пока не настроен. Проверьте BOT_USERNAME.');
    return;
  }
  const state = generateState();
  const url = `https://t.me/${config.botUsername}?start=${state}`;
  window.open(url, '_blank', 'noopener');
}

function setupAudio() {
  if (!audioCtx) {
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
  }
}

function playTone({ frequency, duration, type }) {
  if (!soundEnabled) return;
  setupAudio();
  const oscillator = audioCtx.createOscillator();
  const gainNode = audioCtx.createGain();
  oscillator.type = type;
  oscillator.frequency.value = frequency;
  gainNode.gain.setValueAtTime(0.0001, audioCtx.currentTime);
  gainNode.gain.exponentialRampToValueAtTime(0.08, audioCtx.currentTime + 0.02);
  gainNode.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + duration);
  oscillator.connect(gainNode);
  gainNode.connect(audioCtx.destination);
  oscillator.start();
  oscillator.stop(audioCtx.currentTime + duration);
}

function playSound(kind) {
  if (kind === 'click') {
    playTone({ frequency: 440, duration: 0.08, type: 'sine' });
  }
  if (kind === 'win') {
    playTone({ frequency: 523, duration: 0.12, type: 'triangle' });
    setTimeout(() => playTone({ frequency: 659, duration: 0.12, type: 'triangle' }), 140);
  }
  if (kind === 'lose') {
    playTone({ frequency: 220, duration: 0.2, type: 'sawtooth' });
  }
}

function toggleSound() {
  soundEnabled = !soundEnabled;
  soundToggleBtn.textContent = soundEnabled ? 'Вкл' : 'Выкл';
}

async function loadConfig() {
  try {
    const response = await fetch('/api/telegram-webhook?config=1');
    if (!response.ok) return;
    const data = await response.json();
    if (data.botUsername) {
      config.botUsername = data.botUsername;
    }
    if (data.siteUrl) {
      config.siteUrl = data.siteUrl;
    }
  } catch (error) {
    console.warn('Не удалось загрузить конфиг Telegram.');
  }
}

connectBtn.addEventListener('click', connectTelegram);
playAgainBtn.addEventListener('click', resetGame);
soundToggleBtn.addEventListener('click', toggleSound);

(async () => {
  await loadConfig();
  parseTelegramParams();
  initBoard();
})();
