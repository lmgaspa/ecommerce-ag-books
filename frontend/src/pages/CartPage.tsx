import { useState, useEffect, useContext, useRef } from "react";
import { useNavigate } from "react-router-dom";
import CartTable from "../components/cart/CartTable";
import CouponInput from "../components/cart/CouponInput";
import CartSummary from "../components/cart/CartSummary";
import { getStockByIds } from "../api/stock";
import { useCoupon } from "../hooks/useCoupon";
import { CartContext } from "../context/CartContext";
import type { CartItem } from "../context/CartTypes";

const CartPage = () => {
  const cartContext = useContext(CartContext);
  const [stockById, setStockById] = useState<Record<string, number>>({});
  const navigate = useNavigate();
  const { 
    applyCoupon, 
    getDiscountAmount, 
    isValid: couponValid, 
    inputValue, 
    setInputValue,
    isValidating
  } = useCoupon();

  if (!cartContext) {
    throw new Error("CartPage must be used within a CartProvider");
  }

  const { cartItems, updateQuantity: updateQuantityContext, removeFromCart, totalPrice } = cartContext;
  const subtotal = totalPrice;

  const initializedRef = useRef(false);

  // Carrega estoque e valida carrinho ao montar
  useEffect(() => {
    if (initializedRef.current || cartItems.length === 0) return;
    initializedRef.current = true;

    (async () => {
      const ids = cartItems.map((i) => i.id);
      const stockMap = await getStockByIds(ids);
      const stockDict = Object.fromEntries(
        ids.map((id) => [id, Math.max(0, stockMap[id]?.stock ?? 0)])
      );
      setStockById(stockDict);

      // Valida e ajusta quantidades baseado no estoque
      const fixed = cartItems
        .map((i) => {
          const s = stockDict[i.id] ?? 0;
          const qty = Math.min(i.quantity, Math.max(0, s));
          return { ...i, quantity: qty };
        })
        .filter((i) => i.quantity > 0);

      // Se houve ajustes, atualiza o contexto
      if (
        fixed.length !== cartItems.length ||
        fixed.some((f, idx) => f.quantity !== cartItems[idx]?.quantity)
      ) {
        // Ajusta quantidades no contexto
        fixed.forEach((item) => {
          const current = cartItems.find((c) => c.id === item.id);
          if (current && current.quantity !== item.quantity) {
            const delta = item.quantity - current.quantity;
            updateQuantityContext(item.id, delta);
          }
        });
        // Remove itens que ficaram sem estoque
        cartItems.forEach((item) => {
          if (!fixed.find((f) => f.id === item.id)) {
            removeFromCart(item.id);
          }
        });
        alert("Atualizamos seu carrinho de acordo com o estoque atual.");
      }
    })();
  }, [cartItems, updateQuantityContext, removeFromCart]);

  const updateQuantity = (itemId: string, amount: number) => {
    const max = stockById[itemId] ?? Infinity;
    const currentItem = cartItems.find((i) => i.id === itemId);
    if (!currentItem) return;

    const next = currentItem.quantity + amount;
    if (amount > 0 && next > max) {
      alert("Quantidade solicitada excede o estoque disponível.");
      return;
    }
    if (next <= 0) {
      removeFromCart(itemId);
      return;
    }
    updateQuantityContext(itemId, amount);
  };

  const removeItem = (itemId: string) => {
    removeFromCart(itemId);
  };

  const handleApplyCoupon = async (): Promise<{ success: boolean; discountAmount?: number }> => {
    // Converter cartItems para o formato esperado pelo backend
    const cartItemsForValidation = cartItems.map(item => ({
      id: item.id,
      title: item.title,
      price: item.price,
      quantity: item.quantity,
      imageUrl: item.imageUrl
    }));
    
    const result = await applyCoupon(inputValue, subtotal, cartItemsForValidation);
    if (result.success && result.discountAmount !== undefined) {
      // Usar o valor retornado pela API, mas limitado a R$ 15
      const maxDiscount = 15.00;
      const actualDiscount = Math.min(result.discountAmount, maxDiscount);
      alert(`Cupom aplicado com sucesso! Desconto de R$ ${actualDiscount.toFixed(2)}`);
    }
    return result;
  };

  const handleCheckout = () => {
    alert("Finalizando compra...");
    navigate("/checkout");
  };

  return (
    <div className="container mx-auto mt-0 pt-2 mb-8 px-4 bg-background rounded-md shadow-lg p-6 sm:p-8">
      <h1 className="text-4xl font-bold mb-8 text-text-primary">Carrinho de Compras</h1>
      {cartItems.length === 0 ? (
        <p className="text-text-secondary">Seu carrinho está vazio.</p>
      ) : (
        <>
          <CartTable items={cartItems} onQuantityChange={updateQuantity} onRemoveItem={removeItem} />

          <div className="flex flex-col lg:flex-row gap-4 mb-8">
            <div className="w-full lg:w-1/2">
              <CouponInput 
                value={inputValue} 
                onChange={setInputValue} 
                onApply={handleApplyCoupon}
                isValidating={isValidating}
                isValid={couponValid}
                discount={getDiscountAmount(subtotal)}
              />
              {couponValid && (
                <div className="text-green-600 text-sm mt-2">
                  ✓ Cupom {getDiscountAmount(subtotal) > 0 ? `${getDiscountAmount(subtotal).toFixed(2).replace(".", ",")}` : ''} aplicado
                </div>
              )}
            </div>
            <div className="w-full lg:w-1/2">
              <CartSummary subtotal={subtotal} discount={getDiscountAmount(subtotal)} onCheckout={handleCheckout} />
            </div>
          </div>

          <button
            onClick={() => navigate("/books")}
            className="w-full sm:w-auto px-6 py-2 bg-primary text-background rounded-md shadow-md transition hover:bg-secondary"
          >
            Continuar Comprando
          </button>
        </>
      )}
      <div className="text-right text-xl font-bold mt-4">
        Total: R$ {(subtotal - getDiscountAmount(subtotal)).toFixed(2).replace(".", ",")}
      </div>
    </div>
  );
};

export default CartPage;
