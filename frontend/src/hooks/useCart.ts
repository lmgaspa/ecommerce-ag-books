import type { Book } from '../data/books';
import type { CartItem } from '../context/CartTypes';
import { parsePrice } from '../utils/parsePrice';
import { cookieStorage } from '../utils/cookieUtils';

export const useCart = () => {
  const getCart = (): CartItem[] => {
    return cookieStorage.get<CartItem[]>('cart', []);
  };

  const saveCart = (items: CartItem[]) => {
    cookieStorage.set('cart', items);
  };

  const addToCart = (book: Book, quantity: number = 1) => {
    const price = parsePrice(book.price);
    const cart = getCart();

    const existingItem = cart.find((item) => item.id === book.id);

    if (existingItem) {
      existingItem.quantity += quantity;
    } else {
      cart.push({
        id: book.id,
        title: book.title,
        imageUrl: book.imageUrl,
        price,
        quantity,
      });
    }

    saveCart(cart);
  };

  return {
    addToCart,
    getCart,
  };
};
