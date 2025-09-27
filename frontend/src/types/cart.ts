export type CartItem = {
  id: string;
  title: string;
  price: number;
  quantity: number;
};

export type CartContextType = {
  cartItems: CartItem[];
  totalPrice: number;
  addToCart: (item: CartItem) => void;
  removeFromCart: (id: string) => void;
};