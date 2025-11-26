// src/pages/PedidoConfirmado.tsx
import { useMemo, useEffect, useContext } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { CartContext } from "../context/CartContext";
import { analytics } from "../analytics";
import type { AnalyticsPort } from "../analytics/AnalyticsPort";
import Cookies from "js-cookie";

// Usa a própria porta como fonte da verdade do tipo (OCP friendly)
type PurchaseParams = Parameters<AnalyticsPort["purchase"]>[0];

const CART_COOKIE_NAME = "cart";
const GA_KEY = "ga_purchase_payload";

export default function PedidoConfirmado() {
  const [params] = useSearchParams();

  const orderId = useMemo(() => params.get("orderId"), [params]);
  const name = useMemo(() => params.get("name"), [params]);

  // Normaliza o payment para evitar erro de espaço/case
  const paymentRaw = useMemo(() => params.get("payment") ?? "", [params]);
  const payment = useMemo(() => paymentRaw.trim().toLowerCase(), [paymentRaw]);

  const paidParam = useMemo(() => params.get("paid"), [params]);
  const paid = useMemo(
    () => (paidParam ?? "").trim().toLowerCase() === "true",
    [paidParam]
  );

  const isPix = payment === "pix";
  const isCard = payment === "card";
  const isPaid = isPix || (isCard && paid);

  const cartContext = useContext(CartContext);

  // ✅ Limpa o carrinho (contexto + cookie) quando a página representa um pagamento concluído
  useEffect(() => {
    if (!isPaid) return;

    if (cartContext) {
      cartContext.clearCart();
    }

    try {
      Cookies.remove(CART_COOKIE_NAME);
    } catch {
      // melhor esforço, falha silenciosa
    }
  }, [isPaid, cartContext]);

  // ✅ Dispara o purchase do GA4 via fachada `analytics` usando snapshot salvo no sessionStorage
  //    - Só roda em pedido "pago"
  //    - Não envia PII (payload já foi montado só com dados de compra)
  //    - Remove o snapshot depois para evitar reenvio em refresh
  useEffect(() => {
    if (!isPaid) return;
    if (typeof window === "undefined") return;

    try {
      const raw = sessionStorage.getItem(GA_KEY);
      if (!raw) return;

      let parsed: any;
      try {
        parsed = JSON.parse(raw);
      } catch {
        sessionStorage.removeItem(GA_KEY);
        return;
      }

      if (!parsed || typeof parsed !== "object") {
        sessionStorage.removeItem(GA_KEY);
        return;
      }

      const items = Array.isArray(parsed.items)
        ? (parsed.items as PurchaseParams["items"])
        : [];

      const valueNumber = Number(parsed.value);
      const shippingNumber =
        parsed.shipping !== undefined && parsed.shipping !== null
          ? Number(parsed.shipping)
          : undefined;
      const taxNumber =
        parsed.tax !== undefined && parsed.tax !== null
          ? Number(parsed.tax)
          : undefined;

      const payload: PurchaseParams = {
        transaction_id:
          typeof parsed.transaction_id === "string" &&
          parsed.transaction_id.trim().length > 0
            ? parsed.transaction_id
            : orderId
            ? String(orderId)
            : "",
        value: valueNumber,
        currency:
          typeof parsed.currency === "string" ? parsed.currency : "BRL",
        shipping: shippingNumber,
        tax: taxNumber,
        items,
        payment_type:
          typeof parsed.payment_type === "string"
            ? parsed.payment_type
            : undefined,
        author_id:
          typeof parsed.author_id === "number"
            ? parsed.author_id
            : undefined,
      };

      // Validação mínima antes de enviar
      if (!payload.transaction_id || !Number.isFinite(payload.value)) {
        sessionStorage.removeItem(GA_KEY);
        return;
      }

      // ✅ OCP: usamos apenas a fachada `analytics`. Se um dia trocar GA4 por outro, nada muda aqui.
      analytics.purchase(payload);
    } catch {
      // falha silenciosa
    } finally {
      try {
        sessionStorage.removeItem(GA_KEY);
      } catch {
        // ignore
      }
    }
  }, [isPaid, orderId]);

  const renderMessage = () => {
    if (isPix) {
      return (
        <>
          <h1 className="text-2xl font-semibold mb-2">
            Pagamento confirmado via Pix 🎉
          </h1>
          {name && (
            <p className="text-gray-700 mb-1">
              Obrigado, <strong>{name}</strong>!
            </p>
          )}
          {orderId && <p className="text-gray-700">Pedido #{orderId}</p>}
          <p className="text-gray-600 mt-2">
            Você receberá um e-mail com os detalhes do pedido em instantes.
          </p>
        </>
      );
    }

    if (isCard) {
      if (isPaid) {
        return (
          <>
            <h1 className="text-2xl font-semibold mb-2">
              Pagamento aprovado via Cartão de Crédito 🎉
            </h1>
            {orderId && <p className="text-gray-700">Pedido #{orderId}</p>}
            <p className="text-gray-600 mt-2">
              Seu pagamento com cartão foi aprovado. Em breve você receberá um
              e-mail com os detalhes.
            </p>
          </>
        );
      }

      // Fallback "aguardando aprovação"
      return (
        <>
          <h1 className="text-2xl font-semibold mb-2">
            Aguardando aprovação do cartão ⏳
          </h1>
          {orderId && <p className="text-gray-700">Pedido #{orderId}</p>}
          <p className="text-gray-600 mt-2">
            Estamos processando o pagamento com cartão. Você receberá um e-mail
            assim que houver atualização.
          </p>
        </>
      );
    }

    // Fallback quando não veio payment ou veio inválido
    return (
      <h1 className="text-2xl font-semibold mb-2">Pedido registrado ✅</h1>
    );
  };

  return (
    <div className="max-w-2xl mx-auto p-6 text-center">
      {renderMessage()}
      <Link
        to="/"
        className="inline-block mt-6 bg-black text-white px-4 py-2 rounded"
      >
        Voltar para a loja
      </Link>
    </div>
  );
}
