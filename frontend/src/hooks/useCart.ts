// src/hooks/useCart.ts
import type { Book } from '../data/books';
import type { CartItem } from '../context/CartTypes';
import { parsePrice } from '../utils/parsePrice';
import { cookieStorage } from '../utils/cookieUtils';
import { analytics, mapCartItems } from '../analytics';

const CART_KEY = "cart";

export const useCart = () => {
  const getCart = (): CartItem[] => {
    return cookieStorage.get<CartItem[]>(CART_KEY, []);
  };

  const saveCart = (items: CartItem[]) => {
    cookieStorage.set(CART_KEY, items);
  };

  const addToCart = (book: Book, quantity: number = 1) => {
    const price = parsePrice(book.price);
    const cart = getCart();

    const existingItem = cart.find((item) => item.id === book.id);
    let addedQty = quantity;

    if (existingItem) {
      existingItem.quantity += quantity;
      addedQty = quantity; // incremento
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

    // GA4: add_to_cart (OCP via facade)
    try {
      const itemsPayload = mapCartItems([{ id: book.id, title: book.title, price, quantity: addedQty }]);
      analytics.addToCart({
        items: itemsPayload,
        value: Number(price * addedQty),
        currency: "BRL",
      });
    } catch {
      // no-op
    }
  };

  return {
    addToCart,
    getCart,
  };
};
