import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import CartTable from "../components/cart/CartTable";
import CouponInput from "../components/cart/CouponInput";
import CartSummary from "../components/cart/CartSummary";
import { getStockByIds } from "../api/stock";
import Cookies from "js-cookie";
import { useCoupon } from "../hooks/useCoupon";

interface CartItem {
  id: string;
  title: string;
  imageUrl: string;
  price: number;
  quantity: number;
  stock?: number;
}

const COOKIE_NAME = "cart";
const COOKIE_DAYS = 7;

const CartPage = () => {
  const [cartItems, setCartItems] = useState<CartItem[]>([]);
  const [subtotal, setSubtotal] = useState(0);
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

  // Utilidades para manipular cookies
  const getCartFromCookies = (): CartItem[] => {
    try {
      const value = Cookies.get(COOKIE_NAME);
      return value ? JSON.parse(value) : [];
    } catch {
      return [];
    }
  };

  const saveCartToCookies = (items: CartItem[]) => {
    Cookies.set(COOKIE_NAME, JSON.stringify(items), {
      expires: COOKIE_DAYS,
      sameSite: "Lax",
      secure: window.location.protocol === "https:",
    });
  };

  const clearCartCookies = () => {
    Cookies.remove(COOKIE_NAME);
  };

  // Carrega carrinho e sincroniza com estoque
  useEffect(() => {
    const storedCart = getCartFromCookies();
    const normalizedCart = storedCart.map((item) => {
      const rawPrice = item.price as unknown;
      const price =
        typeof rawPrice === "string"
          ? parseFloat(rawPrice.replace(/[^\d.,]/g, "").replace(",", ".")) || 0
          : typeof rawPrice === "number"
          ? rawPrice
          : 0;
      return { ...item, price, quantity: typeof item.quantity === "number" ? item.quantity : 1 };
    });

    (async () => {
      if (!normalizedCart.length) {
        setCartItems([]);
        setSubtotal(0);
        clearCartCookies();
        return;
      }

      const ids = normalizedCart.map((i) => i.id);
      const stockMap = await getStockByIds(ids);
      const stockDict = Object.fromEntries(
        ids.map((id) => [id, Math.max(0, stockMap[id]?.stock ?? 0)])
      );
      setStockById(stockDict);

      const fixed = normalizedCart
        .map((i) => {
          const s = stockDict[i.id] ?? 0;
          const qty = Math.min(i.quantity, Math.max(0, s));
          return { ...i, quantity: qty };
        })
        .filter((i) => i.quantity > 0);

      setCartItems(fixed);
      saveCartToCookies(fixed);
      calculateSubtotal(fixed);

      if (
        fixed.length !== normalizedCart.length ||
        JSON.stringify(fixed) !== JSON.stringify(normalizedCart)
      ) {
        alert("Atualizamos seu carrinho de acordo com o estoque atual.");
      }
    })();
  }, []);

  const calculateSubtotal = (items: CartItem[]) => {
    const total = items.reduce((sum, item) => sum + item.price * item.quantity, 0);
    setSubtotal(total);
  };

  const updateQuantity = (itemId: string, amount: number) => {
    const updatedItems = cartItems
      .map((item) => {
        if (item.id !== itemId) return item;
        const max = stockById[item.id] ?? Infinity;
        const next = item.quantity + amount;
        if (amount > 0 && next > max) {
          alert("Quantidade solicitada excede o estoque disponível.");
          return item;
        }
        if (next <= 0) return null;
        return { ...item, quantity: next };
      })
      .filter((item): item is CartItem => item !== null);

    setCartItems(updatedItems);
    saveCartToCookies(updatedItems);
    calculateSubtotal(updatedItems);
  };

  const removeItem = (itemId: string) => {
    const updatedItems = cartItems.filter((item) => item.id !== itemId);
    setCartItems(updatedItems);
    saveCartToCookies(updatedItems);
    calculateSubtotal(updatedItems);
  };

  const handleApplyCoupon = async (): Promise<{ success: boolean; discountAmount?: number }> => {
    const result = await applyCoupon(inputValue, subtotal);
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
