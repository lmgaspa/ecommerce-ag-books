// src/hooks/useCart.ts
import { useContext } from "react";
import type { Book } from "../data/books";
import type { CartItem } from "../context/CartTypes";
import { parsePrice } from "../utils/parsePrice";
import { analytics, mapCartItems } from "../analytics";
import { CartContext } from "../context/CartContext";

export const useCart = () => {
  const cartContext = useContext(CartContext);

  if (!cartContext) {
    throw new Error("useCart must be used within a CartProvider");
  }

  const { cartItems, addToCart: addToCartContext, totalPrice } = cartContext;

  const addToCart = (book: Book, quantity: number = 1) => {
    const price = parsePrice(book.price);

    // Monta o CartItem apenas com o incremento de quantidade.
    // O CartContext é quem decide se soma à existente ou cria novo item.
    const cartItem: CartItem = {
      id: book.id,
      title: book.title,
      imageUrl: book.imageUrl,
      price,
      quantity,
    };

    // Atualiza o estado global do carrinho (e, por consequência, o cookie via CartContext)
    addToCartContext(cartItem);

    // GA4: add_to_cart (OCP via facade)
    try {
      const itemsPayload = mapCartItems([
        {
          id: book.id,
          title: book.title,
          price,
          quantity,
        },
      ]);

      analytics.addToCart({
        items: itemsPayload,
        value: Number(price * quantity),
        currency: "BRL",
      });
    } catch {
      // no-op: não quebrar UX por causa de analytics
    }
  };

  const getCart = () => {
    return cartItems;
  };

  return {
    addToCart,
    getCart,
    items: cartItems,
    totalPrice,
  };
};