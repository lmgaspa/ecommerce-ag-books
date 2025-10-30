export const parsePrice = (price: string): number => {
  if (!price) return 0;
  return parseFloat(price.replace(/[^\d,.-]/g, "").replace(",", "."));
};