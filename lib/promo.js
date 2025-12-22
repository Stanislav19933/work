export function generatePromoCode5() {
  const n = Math.floor(Math.random() * 100000); // 0..99999
  return String(n).padStart(5, "0");
}
