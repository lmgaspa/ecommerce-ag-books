import { useEffect, useRef, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import type { CartItem } from "../context/CartTypes";
import type { CheckoutFormData } from "../types/CheckoutTypes";
import { cookieStorage } from "../utils/cookieUtils";
import { buildApiUrl } from "../api/http";
import { analytics, mapCartItems } from "../analytics";
import type { CartContextType } from "../context/CartContext";

interface UsePixSSEParams {
  orderId: string | null;
  cartItems: CartItem[];
  frete: number | null;
  desconto: number;
  totalComFrete: number;
  couponCode: string | null;
  clearCoupon: () => void;
  expiresAtMs: number | null;
  cartContext?: CartContextType | null;
  onCloseSSE?: () => void;
}

/**
 * Hook para gerenciar conexão SSE para Pix
 */
export const usePixSSE = ({
  orderId,
  cartItems,
  frete,
  desconto,
  totalComFrete,
  couponCode,
  clearCoupon,
  expiresAtMs,
  cartContext,
  onCloseSSE,
}: UsePixSSEParams) => {
  const navigate = useNavigate();
  const sseRef = useRef<EventSource | null>(null);
  const retryTimerRef = useRef<number | null>(null);
  const backoffRef = useRef(1500);
  const isMountedRef = useRef(true);

  const closeSSE = useCallback(() => {
    if (sseRef.current) {
      sseRef.current.close();
      sseRef.current = null;
    }
    if (onCloseSSE) onCloseSSE();
  }, [onCloseSSE]);

  const clearRetryTimer = useCallback(() => {
    if (retryTimerRef.current) {
      window.clearTimeout(retryTimerRef.current);
      retryTimerRef.current = null;
    }
  }, []);

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
      closeSSE();
      clearRetryTimer();
    };
  }, [closeSSE, clearRetryTimer]);

  const connectSSE = useCallback(
    (id: string) => {
      closeSSE();
      const url = buildApiUrl(`/orders/${id}/events`);
      const es = new EventSource(url, { withCredentials: false });
      sseRef.current = es;

      const resetBackoff = () => {
        backoffRef.current = 1500;
      };

      es.addEventListener("open", resetBackoff);
      es.addEventListener("ping", () => {
        // keep-alive
      });

      es.addEventListener("paid", () => {
        // Salva snapshot do purchase para a tela de confirmação disparar
        try {
          const itemsPayload = mapCartItems(cartItems);
          const shippingVal = Number(frete ?? 0);
          const payload = {
            transaction_id: id,
            value: Number(totalComFrete),
            currency: "BRL",
            shipping: shippingVal,
            tax: 0,
            discount: desconto,
            items: itemsPayload,
            // 🔎 isso sobe até PedidoConfirmado → analytics.purchase
            payment_type: "pix",
          };
          sessionStorage.setItem("ga_purchase_payload", JSON.stringify(payload));
        } catch {
          /* no-op */
        }

        closeSSE();
        if (retryTimerRef.current) {
          window.clearTimeout(retryTimerRef.current);
          retryTimerRef.current = null;
        }

        // 🔑 Limpeza do carrinho e do cupom:
        // - Preferimos o CartContext (clearCart), se estiver disponível.
        // - Mantemos fallback para remover o cookie diretamente, para não quebrar nada.
        if (cartContext?.clearCart) {
          cartContext.clearCart();
        } else {
          cookieStorage.remove("cart");
        }

        // Limpa o cupom após compra bem-sucedida (idempotência)
        if (couponCode) {
          clearCoupon();
        }

        const checkoutForm = cookieStorage.get<CheckoutFormData | null>("checkoutForm", null);
        const fullName = checkoutForm
          ? [checkoutForm.firstName, checkoutForm.lastName].filter(Boolean).join(" ").trim()
          : "";
        navigate(
          `/pedido-confirmado?orderId=${id}${
            fullName ? `&name=${encodeURIComponent(fullName)}` : ""
          }&payment=pix&paid=true`
        );
      });

      es.onerror = () => {
        closeSSE();
        const wait = Math.min(backoffRef.current, 10000);
        clearRetryTimer();
        retryTimerRef.current = window.setTimeout(() => {
          backoffRef.current = Math.min(backoffRef.current * 2, 10000);
          const notExpired = expiresAtMs === null || Date.now() < expiresAtMs;
          if (isMountedRef.current && notExpired) {
            connectSSE(id); // reabre usando o mesmo id
          }
        }, wait);
      };
    },
    [navigate, expiresAtMs, cartItems, frete, desconto, totalComFrete, couponCode, clearCoupon, cartContext, closeSSE, clearRetryTimer]
  );

  useEffect(() => {
    if (!orderId) return;
    connectSSE(orderId);
    return () => {
      closeSSE();
      clearRetryTimer();
    };
  }, [orderId, connectSSE, closeSSE, clearRetryTimer]);

  return { closeSSE };
};

