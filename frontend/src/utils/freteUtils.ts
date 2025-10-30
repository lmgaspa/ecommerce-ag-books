import type { CartItem } from '../context/CartTypes';
import { calcularPesoConsiderado } from './pesoLivros';

/**
 * Calcula o frete via regra simulada dos Correios
 * com base na distância (CEP) e no peso dos livros.
 */
export async function calcularFreteViaCorreios(
  cepDestino: string,
  cartItems: { id: string; quantity: number }[]
): Promise<number> {
  try {
    const pesoTotalKg = cartItems.reduce((acc, item) => {
      return acc + calcularPesoConsiderado(item.id) * item.quantity;
    }, 0);

    const origemPrefix = 456;
    const destinoPrefix = parseInt(cepDestino.substring(0, 3));
    const distancia = isNaN(destinoPrefix) ? 0 : Math.abs(origemPrefix - destinoPrefix);

    const base = 20;
    const adicionalPorDistancia = Math.ceil(distancia / 50) * 3;
    const extra = Math.max(0, pesoTotalKg - 0.3);
    const adicionalPorPeso = Math.ceil(extra / 0.1) * 4;

    return base + adicionalPorDistancia + adicionalPorPeso;
  } catch (error) {
    console.error("Erro ao calcular frete:", error);
    return 0;
  }
}

/**
 * Wrapper que valida CPF/CEP e calcula o frete.
 */
export async function calcularFreteComBaseEmCarrinho(
  form: { cep: string; cpf: string },
  cartItems: CartItem[]
): Promise<number> {
  const cep = form.cep.replace(/\D/g, '');
  const cpfNumeros = form.cpf.replace(/\D/g, '');

  if (cpfNumeros === '00000000000') return 0;
  if (cep.length !== 8 || cartItems.length === 0) return 0;

  return await calcularFreteViaCorreios(cep, cartItems);
}
