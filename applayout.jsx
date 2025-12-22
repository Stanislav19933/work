import "./globals.css";

export const metadata = {
  title: "Крестики-нолики — промокод за победу",
  description: "Игра против компьютера. Победа — промокод и сообщение в Telegram."
};

export default function RootLayout({ children }) {
  return (
    <html lang="ru">
      <body>{children}</body>
    </html>
  );
}
