export function formatPrice(price: string | number): string {
  if (typeof price === 'string') {
    price = parseFloat(price.replace('R$', '').replace(',', '.'));
  }
  return `R$ ${price.toFixed(2).replace('.', ',')}`;
}