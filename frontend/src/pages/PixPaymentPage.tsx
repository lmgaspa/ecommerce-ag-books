// src/pages/PixPaymentPage.tsx - REFATORADO
import { useEffect, useMemo, useState, useContext } from "react";
import type { CartItem } from "../context/CartTypes";
import { formatPrice } from "../utils/formatPrice";
import { cookieStorage } from "../utils/cookieUtils";
import { useCoupon } from "../hooks/useCoupon";
import { CartContext } from "../context/CartContext";

// Custom Hooks
import { usePixCheckout } from "../hooks/usePixCheckout";
import { usePixSSE } from "../hooks/usePixSSE";
import { usePixTimer } from "../hooks/usePixTimer";

// Components
import { OrderSummary } from "../components/OrderSummary";
import ReviewPurchaseButton from "../components/common/ReviewPurchaseButton";
import { PixQRCodeDisplay } from "../components/payment/pix/PixQRCodeDisplay";
import { PixPaymentWarnings } from "../components/payment/pix/PixPaymentWarnings";

export default function PixPaymentPage() {
  const { getDiscountAmount, couponCode, clearCoupon, isValid: isCouponValid } = useCoupon();
  const cartContext = useContext(CartContext);

  // Fonte de verdade do carrinho:
  // 1) Se CartContext existir, usamos cartContext.cartItems.
  // 2) Se não existir (fallback), lemos do cookie "cart".
  const initialCart: CartItem[] = useMemo(
    () => cartContext?.cartItems ?? cookieStorage.get<CartItem[]>("cart", []),
    [cartContext?.cartItems]
  );

  const [cartItems, setCartItems] = useState<CartItem[]>(initialCart);

  // Cálculos
  const totalProdutos = useMemo(
    () => cartItems.reduce((sum, item) => sum + item.price * item.quantity, 0),
    [cartItems]
  );
  const desconto = useMemo(
    () => getDiscountAmount(totalProdutos),
    [totalProdutos, getDiscountAmount]
  );

  // Custom Hooks
  const checkout = usePixCheckout({
    cartItems,
    totalProdutos,
    desconto,
    couponCode,
    isCouponValid,
  });

  const totalComFrete = useMemo(
    () => totalProdutos + (checkout.frete ?? 0) - desconto,
    [totalProdutos, checkout.frete, desconto]
  );

  const timer = usePixTimer({
    expiresAtMs: checkout.expiresAtMs,
    onExpire: () => {
      // SSE será fechado automaticamente pelo hook
    },
  });

  usePixSSE({
    orderId: checkout.orderId,
    cartItems,
    frete: checkout.frete,
    desconto,
    totalComFrete,
    couponCode,
    clearCoupon,
    expiresAtMs: checkout.expiresAtMs,
    cartContext,
  });

  // Atualizar carrinho se estiver vazio
  useEffect(() => {
    if (cartItems.length === 0) {
      const storedCart = cookieStorage.get<CartItem[]>("cart", []);
      if (storedCart.length > 0) setCartItems(storedCart);
    }
  }, [cartItems.length]);

  // Sincronizar com CartContext
  useEffect(() => {
    if (cartContext?.cartItems) {
      setCartItems(cartContext.cartItems);
    }
  }, [cartContext?.cartItems]);

  return (
    <div className="max-w-3xl mx-auto p-6">
      <h2 className="text-2xl font-semibold mb-4">Resumo da Compra (Pix)</h2>

      {checkout.errorMsg && (
        <div className="mb-4 p-3 rounded bg-red-50 text-red-700 border border-red-200">
          {checkout.errorMsg}
        </div>
      )}

      {/* Avisos de Segurança */}
      <PixPaymentWarnings
        remainingSec={timer.remainingSec}
        warningAt={checkout.checkoutData?.warningAt}
        securityWarningAt={checkout.checkoutData?.securityWarningAt}
      />

      {/* Cards do carrinho */}
      <div className="space-y-4">
        {cartItems.map((item) => (
          <div
            key={item.id}
            className="border p-4 rounded shadow-sm flex gap-4 items-center"
          >
            <img
              src={item.imageUrl}
              alt={item.title}
              className="w-24 h-auto object-contain"
            />
            <div>
              <p className="font-medium">{item.title}</p>
              <p>Quantidade: {item.quantity}</p>
              <p>Preço unitário: {formatPrice(item.price)}</p>
              <p>Subtotal: {formatPrice(item.price * item.quantity)}</p>
            </div>
          </div>
        ))}
      </div>

      {/* 🔁 Usa o componente compartilhado de resumo */}
      <OrderSummary
        subtotal={totalProdutos}
        shipping={checkout.frete ?? 0}
        discount={desconto}
        total={totalComFrete}
      />

      {/* 🔁 Botão global reutilizável */}
      <ReviewPurchaseButton />

      {checkout.loading && (
        <p className="text-center mt-8 text-gray-600">Gerando QR Code Pix...</p>
      )}

      <PixQRCodeDisplay
        qrCodeImg={checkout.qrCodeImg}
        pixCopiaECola={checkout.pixCopiaECola}
        isExpired={timer.isExpired}
        formattedTime={timer.formattedTime}
        expiresAtMs={checkout.expiresAtMs}
      />
    </div>
  );
}
