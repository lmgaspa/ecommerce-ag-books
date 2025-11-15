export const parsePrice = (price: string | number | undefined | null): number => {
  // Se for número, retorna direto
  if (typeof price === "number") return price;
  
  // Se for null/undefined ou string vazia, retorna 0
  if (!price) return 0;
  
  // Converte string para número
  const priceStr = String(price);
  return parseFloat(priceStr.replace(/[^\d,.-]/g, "").replace(",", ".")) || 0;
};