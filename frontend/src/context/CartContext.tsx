// src/context/CartContext.tsx
import { createContext, useState, useEffect } from "react";
import type { ReactNode } from "react";
import type { CartItem, CartContextType } from "./CartTypes";
import { cookieStorage } from "../utils/cookieUtils";

// Re-export types for convenience
export type { CartItem, CartContextType } from "./CartTypes";

export const CartContext = createContext<CartContextType | undefined>(
  undefined
);

export const CartProvider = ({ children }: { children: ReactNode }) => {
  const [cartItems, setCartItems] = useState<CartItem[]>(() =>
    cookieStorage.get<CartItem[]>("cart", [])
  );

  // Persiste o carrinho em cookie sempre que mudar
  useEffect(() => {
    cookieStorage.set("cart", cartItems);
  }, [cartItems]);

  const addToCart = (item: CartItem) => {
    setCartItems((prev) => {
      const exists = prev.find((i) => i.id === item.id);
      if (exists) {
        return prev.map((i) =>
          i.id === item.id
            ? { ...i, quantity: i.quantity + item.quantity }
            : i
        );
      }
      return [...prev, item];
    });
  };

  const removeFromCart = (id: string) => {
    setCartItems((prev) => prev.filter((i) => i.id !== id));
  };

  const updateQuantity = (id: string, delta: number) => {
    setCartItems((prev) => {
      const updated = prev
        .map((item) => {
          if (item.id !== id) return item;
          const next = item.quantity + delta;
          if (next <= 0) return null;
          return { ...item, quantity: next };
        })
        .filter((item): item is CartItem => item !== null);
      return updated;
    });
  };

  const clearCart = () => {
    // Fonte única de verdade: estado
    setCartItems([]);
  };

  const totalPrice = cartItems.reduce(
    (sum, item) => sum + item.price * item.quantity,
    0
  );

  const value: CartContextType = {
    cartItems,
    totalPrice,
    addToCart,
    removeFromCart,
    clearCart,
    updateQuantity,
  };

  return (
    <CartContext.Provider value={value}>{children}</CartContext.Provider>
  );
};
