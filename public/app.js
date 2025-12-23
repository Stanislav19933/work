const boardEl = document.getElementById('board');
const resultPanel = document.getElementById('resultPanel');
const resultTitle = document.getElementById('resultTitle');
const resultMessage = document.getElementById('resultMessage');
const promoEl = document.getElementById('promo');
const playAgainBtn = document.getElementById('playAgain');
const resetAllBtn = document.getElementById('resetAll');
const linkTelegramBtn = document.getElementById('linkTelegram');
const linkHint = document.getElementById('linkHint');
const telegramStatus = document.getElementById('telegramStatus');
const checkLinkBtn = document.getElementById('checkLink');
const soundToggle = document.getElementById('soundToggle');
const soundLabel = document.getElementById('soundLabel');

const winningCombos = [
  [0, 1, 2],
  [3, 4, 5],
  [6, 7, 8],
  [0, 3, 6],
  [1, 4, 7],
  [2, 5, 8],
  [0, 4, 8],
  [2, 4, 6]
];

let board = Array(9).fill(null);
let gameOver = false;
let token = null;

const audio = {
  context: null,
  enabled: true
};

function initAudio() {
  if (!audio.context) {
    audio.context = new (window.AudioContext || window.webkitAudioContext)();
  }
}

function playTone(frequency, duration, type = 'sine') {
  if (!audio.enabled) return;
  initAudio();
  const oscillator = audio.context.createOscillator();
  const gain = audio.context.createGain();
  oscillator.type = type;
  oscillator.frequency.value = frequency;
  gain.gain.value = 0.08;
  oscillator.connect(gain);
  gain.connect(audio.context.destination);
  oscillator.start();
  gain.gain.exponentialRampToValueAtTime(0.0001, audio.context.currentTime + duration);
  oscillator.stop(audio.context.currentTime + duration);
}

function playSequence(notes) {
  if (!audio.enabled) return;
  initAudio();
  let time = audio.context.currentTime;
  notes.forEach(({ freq, duration, type = 'sine' }) => {
    const oscillator = audio.context.createOscillator();
    const gain = audio.context.createGain();
    oscillator.type = type;
    oscillator.frequency.value = freq;
    gain.gain.value = 0.08;
    oscillator.connect(gain);
    gain.connect(audio.context.destination);
    oscillator.start(time);
    gain.gain.exponentialRampToValueAtTime(0.0001, time + duration);
    oscillator.stop(time + duration);
    time += duration;
  });
}

function playClick() {
  playTone(520, 0.12, 'triangle');
}

function playWin() {
  playSequence([
    { freq: 620, duration: 0.12 },
    { freq: 780, duration: 0.12 },
    { freq: 920, duration: 0.18 }
  ]);
}

function playLose() {
  playSequence([
    { freq: 320, duration: 0.2, type: 'sawtooth' },
    { freq: 260, duration: 0.25, type: 'sawtooth' }
  ]);
}

function playDraw() {
  playSequence([
    { freq: 420, duration: 0.15 },
    { freq: 420, duration: 0.15 },
    { freq: 420, duration: 0.15 }
  ]);
}

function generatePromoCode() {
  return String(Math.floor(Math.random() * 100000)).padStart(5, '0');
}

function renderBoard() {
  boardEl.innerHTML = '';
  board.forEach((value, index) => {
    const cell = document.createElement('button');
    cell.className = 'cell';
    cell.textContent = value || '';
    if (value || gameOver) {
      cell.classList.add('disabled');
      cell.disabled = true;
    }
    cell.addEventListener('click', () => handlePlayerMove(index));
    boardEl.appendChild(cell);
  });
}

function checkWinner(currentBoard) {
  for (const combo of winningCombos) {
    const [a, b, c] = combo;
    if (currentBoard[a] && currentBoard[a] === currentBoard[b] && currentBoard[a] === currentBoard[c]) {
      return currentBoard[a];
    }
  }
  if (currentBoard.every(Boolean)) {
    return 'draw';
  }
  return null;
}

function findBestMove(mark) {
  for (const combo of winningCombos) {
    const [a, b, c] = combo;
    const line = [board[a], board[b], board[c]];
    const emptyIndex = line.indexOf(null);
    if (emptyIndex !== -1) {
      const marksCount = line.filter((cell) => cell === mark).length;
      if (marksCount === 2) {
        const positions = [a, b, c];
        return positions[emptyIndex];
      }
    }
  }
  return null;
}

function getAIMove() {
  let move = findBestMove('O');
  if (move !== null) return move;

  move = findBestMove('X');
  if (move !== null) return move;

  if (!board[4]) return 4;

  const corners = [0, 2, 6, 8].filter((index) => !board[index]);
  if (corners.length) return corners[Math.floor(Math.random() * corners.length)];

  const empties = board.map((value, index) => (value ? null : index)).filter((value) => value !== null);
  return empties[Math.floor(Math.random() * empties.length)];
}

function endGame(result) {
  gameOver = true;
  let promoCode = '';

  if (result === 'win') {
    promoCode = generatePromoCode();
    resultTitle.textContent = 'Победа!';
    resultMessage.textContent = 'Ты умничка ✨ Хочешь забрать промокод?';
    promoEl.textContent = `Твой промокод: ${promoCode}`;
    playWin();
  }

  if (result === 'lose') {
    resultTitle.textContent = 'Сегодня победа за мной';
    resultMessage.textContent = 'Ничего, в следующий раз обязательно получится.';
    promoEl.textContent = '';
    playLose();
  }

  if (result === 'draw') {
    resultTitle.textContent = 'Ничья!';
    resultMessage.textContent = 'Давай попробуем снова и выиграем вместе.';
    promoEl.textContent = '';
    playDraw();
  }

  resultPanel.classList.add('show');
  sendResult(result, promoCode);
}

function handlePlayerMove(index) {
  if (board[index] || gameOver) return;
  board[index] = 'X';
  playClick();
  renderBoard();

  const result = checkWinner(board);
  if (result) {
    endGame(result === 'X' ? 'win' : 'draw');
    return;
  }

  setTimeout(() => {
    const aiMove = getAIMove();
    if (aiMove === undefined || aiMove === null) return;
    board[aiMove] = 'O';
    renderBoard();
    const newResult = checkWinner(board);
    if (newResult) {
      endGame(newResult === 'O' ? 'lose' : 'draw');
    }
  }, 400);
}

function resetGame() {
  board = Array(9).fill(null);
  gameOver = false;
  resultPanel.classList.remove('show');
  promoEl.textContent = '';
  renderBoard();
}

function hardReset() {
  localStorage.removeItem('linkToken');
  token = null;
  updateTelegramStatus(false, false);
  linkHint.textContent = '';
  checkLinkBtn.classList.add('hidden');
  resetGame();
}

function updateTelegramStatus(linked, pending) {
  if (linked) {
    telegramStatus.textContent = 'Telegram привязан ✅';
  } else if (pending) {
    telegramStatus.textContent = 'Жду, пока бот подтвердит привязку...';
  } else {
    telegramStatus.textContent = 'Telegram пока не привязан.';
  }
}

async function requestLink() {
  try {
    linkTelegramBtn.disabled = true;
    linkHint.textContent = 'Готовлю ссылку...';
    const response = await fetch('/api/link', { method: 'POST' });
    const data = await response.json();
    if (!response.ok || !data.ok) {
      throw new Error(data.message || 'Не удалось создать ссылку');
    }
    token = data.token;
    localStorage.setItem('linkToken', token);
    linkHint.textContent = 'Нажми Start в боте, потом вернись сюда.';
    checkLinkBtn.classList.remove('hidden');
    updateTelegramStatus(false, true);
    window.open(data.botLink, '_blank');
  } catch (error) {
    linkHint.textContent = 'Что-то пошло не так. Попробуй позже.';
  } finally {
    linkTelegramBtn.disabled = false;
  }
}

async function checkLinkStatus() {
  if (!token) return;
  try {
    const response = await fetch(`/api/link-status?token=${token}`);
    const data = await response.json();
    if (!response.ok || !data.ok) {
      throw new Error('Ошибка проверки');
    }
    updateTelegramStatus(data.linked, !data.linked);
  } catch (error) {
    updateTelegramStatus(false, false);
  }
}

async function sendResult(result, promoCode) {
  try {
    await fetch('/api/result', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token, result, promoCode })
    });
  } catch (error) {
    // silent for user
  }
}

function initFromUrl() {
  const params = new URLSearchParams(window.location.search);
  const urlToken = params.get('token');
  if (urlToken) {
    token = urlToken;
    localStorage.setItem('linkToken', token);
    window.history.replaceState({}, document.title, '/');
  } else {
    token = localStorage.getItem('linkToken');
  }

  if (token) {
    updateTelegramStatus(false, true);
    checkLinkBtn.classList.remove('hidden');
    checkLinkStatus();
  }
}

soundToggle.addEventListener('change', (event) => {
  audio.enabled = event.target.checked;
  soundLabel.textContent = audio.enabled ? 'Вкл' : 'Выкл';
});

linkTelegramBtn.addEventListener('click', requestLink);
checkLinkBtn.addEventListener('click', checkLinkStatus);
playAgainBtn.addEventListener('click', resetGame);
resetAllBtn.addEventListener('click', hardReset);

initFromUrl();
renderBoard();
