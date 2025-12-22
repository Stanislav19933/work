export const EMPTY = null;
export const HUMAN = "X";
export const CPU = "O";

export function checkWinner(b) {
  const lines = [
    [0,1,2],[3,4,5],[6,7,8],
    [0,3,6],[1,4,7],[2,5,8],
    [0,4,8],[2,4,6]
  ];

  for (const [a,c,d] of lines) {
    if (b[a] && b[a] === b[c] && b[a] === b[d]) {
      return { winner: b[a], line: [a,c,d] };
    }
  }
  if (b.every(v => v !== EMPTY)) return { winner: "DRAW", line: null };
  return { winner: null, line: null };
}

function availableMoves(b) {
  const res = [];
  for (let i = 0; i < 9; i++) if (b[i] === EMPTY) res.push(i);
  return res;
}

// Minimax для 3x3 — быстрый и "умный".
// Оценка: CPU выигрывает => +10 - depth, HUMAN выигрывает => -10 + depth, ничья => 0.
function minimax(b, isMaximizing, depth) {
  const r = checkWinner(b);
  if (r.winner === CPU) return { score: 10 - depth };
  if (r.winner === HUMAN) return { score: -10 + depth };
  if (r.winner === "DRAW") return { score: 0 };

  const moves = availableMoves(b);
  let best = { score: isMaximizing ? -Infinity : Infinity, move: moves[0] };

  for (const mv of moves) {
    b[mv] = isMaximizing ? CPU : HUMAN;
    const val = minimax(b, !isMaximizing, depth + 1).score;
    b[mv] = EMPTY;

    if (isMaximizing) {
      if (val > best.score) best = { score: val, move: mv };
    } else {
      if (val < best.score) best = { score: val, move: mv };
    }
  }
  return best;
}

// Ход компьютера: "умный", но с маленьким шансом неидеального хода — выглядит человечнее.
export function cpuMove(board, mistakeChance = 0.08) {
  const moves = availableMoves(board);
  if (moves.length === 0) return null;

  // Лёгкая "неидеальность" — вау-эффект: игрок иногда побеждает, не чувствуя себя статистом.
  if (Math.random() < mistakeChance) {
    return moves[Math.floor(Math.random() * moves.length)];
  }

  const b = board.slice();
  return minimax(b, true, 0).move ?? moves[0];
}
