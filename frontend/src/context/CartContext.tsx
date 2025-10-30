import { createContext, useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import type { CartItem } from './CartTypes';
import { cookieStorage } from '../utils/cookieUtils';

export interface CartContextType {
  cartItems: CartItem[];
  addToCart: (item: CartItem) => void;
  removeFromCart: (id: string) => void;
  totalPrice: number;
}

export const CartContext = createContext<CartContextType | undefined>(undefined);

export const CartProvider = ({ children }: { children: ReactNode }) => {
  const [cartItems, setCartItems] = useState<CartItem[]>(() =>
    cookieStorage.get<CartItem[]>('cart', [])
  );

  useEffect(() => {
    cookieStorage.set('cart', cartItems);
  }, [cartItems]);

  const addToCart = (item: CartItem) => {
    setCartItems((prev) => {
      const exists = prev.find((i) => i.id === item.id);
      return exists
        ? prev.map((i) => i.id === item.id ? { ...i, quantity: i.quantity + item.quantity } : i)
        : [...prev, item];
    });
  };

  const removeFromCart = (id: string) => {
    setCartItems((prev) => prev.filter((i) => i.id !== id));
  };

  const totalPrice = cartItems.reduce((sum, item) => sum + item.price * item.quantity, 0);

  return (
    <CartContext.Provider value={{ cartItems, addToCart, removeFromCart, totalPrice }}>
      {children}
    </CartContext.Provider>
  );
};
