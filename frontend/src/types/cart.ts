export type CartItem = {
  id: string;
  title: string;
  imageUrl: string; // ✅ importante pro checkout/listagem
  price: number;
  quantity: number;
};

export type CartContextType = {
  cartItems: CartItem[];
  totalPrice: number;
  addToCart: (item: CartItem) => void;
  removeFromCart: (id: string) => void;
  clearCart: () => void; // ✅ para esvaziar o carrinho depois da compra
};
