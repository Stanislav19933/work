import Database from 'better-sqlite3';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const dbPath = path.join(__dirname, 'data.sqlite');
const db = new Database(dbPath);

db.pragma('journal_mode = WAL');

db.exec(`
  CREATE TABLE IF NOT EXISTS links (
    token TEXT PRIMARY KEY,
    chat_id TEXT,
    created_at INTEGER NOT NULL
  );
`);

export function createLinkToken(token) {
  const stmt = db.prepare('INSERT INTO links (token, chat_id, created_at) VALUES (?, NULL, ?)');
  stmt.run(token, Date.now());
}

export function getLinkByToken(token) {
  const stmt = db.prepare('SELECT token, chat_id, created_at FROM links WHERE token = ?');
  return stmt.get(token);
}

export function linkChatId(token, chatId) {
  const stmt = db.prepare('UPDATE links SET chat_id = ? WHERE token = ? AND chat_id IS NULL');
  return stmt.run(String(chatId), token);
}

export function isTokenLinked(token) {
  const row = getLinkByToken(token);
  return Boolean(row && row.chat_id);
}

export default db;
