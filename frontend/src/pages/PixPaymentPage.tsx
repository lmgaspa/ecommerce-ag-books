// src/pages/PixPaymentPage.tsx
import { useEffect, useMemo, useRef, useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import type { CartItem } from "../context/CartTypes";
import { formatPrice } from "../utils/formatPrice";
import { calcularFreteComBaseEmCarrinho } from "../utils/freteUtils";
import { cookieStorage } from "../utils/cookieUtils";
import type { CheckoutFormData } from "../types/CheckoutTypes";
import { mapCartItems } from "../analytics";
import { useCoupon } from "../hooks/useCoupon";

const RAW_API_BASE = import.meta.env.VITE_API_BASE;
const API_BASE = RAW_API_BASE ? String(RAW_API_BASE).replace(/\/+$/, "") : "";

if (!API_BASE) {
  throw new Error("VITE_API_BASE não configurado. Configure a variável de ambiente.");
}

function formatMMSS(totalSec: number) {
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

// Interface para resposta do backend (OCP)
interface PixCheckoutResponse {
  qrCode: string;
  qrCodeBase64: string;
  message: string;
  orderId: string;
  txid: string;
  reserveExpiresAt?: string;
  ttlSeconds?: number;
  warningAt?: number;
  securityWarningAt?: number;
}

// Hook para gerenciar avisos de segurança (SRP)
const useSecurityWarnings = (
  remainingSec: number,
  warningAt?: number,
  securityWarningAt?: number
) => {
  const [warning, setWarning] = useState<string | null>(null);
  const [securityWarning, setSecurityWarning] = useState<string | null>(null);

  useEffect(() => {
    if (securityWarningAt && remainingSec <= securityWarningAt) {
      setSecurityWarning("🔒 Por questões de segurança, o PIX foi invalidado!");
    } else if (warningAt && remainingSec <= warningAt) {
      setWarning("⚠️ PIX será invalidado em 10 segundos! Pague agora!");
    } else {
      setWarning(null);
      setSecurityWarning(null);
    }
  }, [remainingSec, warningAt, securityWarningAt]);

  return { warning, securityWarning };
};

// Componente para exibir avisos (SRP)
const SecurityWarning = ({
  warning,
  securityWarning,
}: {
  warning: string | null;
  securityWarning: string | null;
}) => {
  if (securityWarning) {
    return (
      <div className="bg-red-100 text-red-800 p-3 rounded border border-red-300 font-bold mb-4">
        {securityWarning}
      </div>
    );
  }

  if (warning) {
    return (
      <div className="bg-orange-50 text-orange-700 p-3 rounded border border-orange-200 mb-4">
        {warning}
      </div>
    );
  }

  return null;
};

export default function PixPaymentPage() {
  const navigate = useNavigate();
  const { getDiscountAmount, couponCode } = useCoupon();

  const initialCart: CartItem[] = cookieStorage.get<CartItem[]>("cart", []);

  const [cartItems, setCartItems] = useState<CartItem[]>(initialCart);
  const [frete, setFrete] = useState<number | null>(null);
  const [qrCodeImg, setQrCodeImg] = useState("");
  const [pixCopiaECola, setPixCopiaECola] = useState("");
  const [loading, setLoading] = useState(false);
  const [orderId, setOrderId] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [checkoutData, setCheckoutData] = useState<PixCheckoutResponse | null>(
    null
  );

  const sseRef = useRef<EventSource | null>(null);
  const retryTimerRef = useRef<number | null>(null);
  const backoffRef = useRef(1500);
  const isMountedRef = useRef(true);

  // Helpers (SRP)
  const closeSSE = () => {
    if (sseRef.current) {
      sseRef.current.close();
      sseRef.current = null;
    }
  };

  const clearRetryTimer = () => {
    if (retryTimerRef.current) {
      window.clearTimeout(retryTimerRef.current);
      retryTimerRef.current = null;
    }
  };

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
      closeSSE();
      clearRetryTimer();
    };
  }, []);

  const totalProdutos = useMemo(
    () => cartItems.reduce((sum, item) => sum + item.price * item.quantity, 0),
    [cartItems]
  );
  const desconto = useMemo(
    () => getDiscountAmount(totalProdutos),
    [totalProdutos, getDiscountAmount]
  );
  const totalComFrete = useMemo(
    () => totalProdutos + (frete ?? 0) - desconto,
    [totalProdutos, frete, desconto]
  );

  useEffect(() => {
    if (cartItems.length === 0) {
      const storedCart = cookieStorage.get<CartItem[]>("cart", []);
      if (storedCart.length > 0) setCartItems(storedCart);
    }
  }, [cartItems.length]);

  useEffect(() => {
    const form = cookieStorage.get<CheckoutFormData | null>(
      "checkoutForm",
      null
    );
    if (!form || cartItems.length === 0) return;
    calcularFreteComBaseEmCarrinho({ cep: form.cep, cpf: form.cpf }, cartItems)
      .then((v) => {
        if (isMountedRef.current) setFrete(v);
      })
      .catch(() => {
        if (isMountedRef.current) setFrete(0);
      });
  }, [cartItems]);

  // TTL / expiração do QR/reserva
  const [expiresAtMs, setExpiresAtMs] = useState<number | null>(null);
  const [remainingSec, setRemainingSec] = useState<number>(0);
  const timerRef = useRef<number | null>(null);

  // Hook para avisos de segurança
  const { warning, securityWarning } = useSecurityWarnings(
    remainingSec,
    checkoutData?.warningAt,
    checkoutData?.securityWarningAt
  );

  useEffect(() => {
    const run = async () => {
      if (frete === null || cartItems.length === 0 || orderId) return;

      const savedForm = cookieStorage.get<CheckoutFormData | null>(
        "checkoutForm",
        null
      );
      if (!savedForm) {
        navigate("/checkout");
        return;
      }
      const form = savedForm;

      setLoading(true);
      setErrorMsg(null);
      try {
        const payload = {
          firstName: form.firstName,
          lastName: form.lastName,
          cpf: form.cpf,
          country: form.country,
          cep: form.cep,
          address: form.address,
          number: form.number,
          complement: form.complement,
          district: form.district,
          city: form.city,
          state: form.state,
          phone: form.phone,
          email: form.email,
          note: form.note,
          payment: "pix",
          shipping: frete,
          cartItems,
          total: totalProdutos + (frete ?? 0), // Total original SEM desconto
          discount: desconto,
          couponCode: couponCode || null,
        };

        const res = await fetch(`${API_BASE}/api/checkout/pix`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        });

        if (!res.ok) {
          const text = await res.text();
          if (res.status === 409 || res.status === 422) {
            setErrorMsg(
              "Indisponível no momento. Outro cliente reservou este item."
            );
            setTimeout(() => navigate("/"), 2000);
            return;
          }
          throw new Error(text || `Erro HTTP ${res.status}`);
        }

        const data: PixCheckoutResponse = await res.json();
        setCheckoutData(data);

        const img = (data.qrCodeBase64 || "").startsWith("data:image")
          ? data.qrCodeBase64
          : `data:image/png;base64,${data.qrCodeBase64 || ""}`;

        if (!isMountedRef.current) return;

        setQrCodeImg(img);
        setPixCopiaECola(data.qrCode || "");
        setOrderId(String(data.orderId || ""));

        // Expiração
        let expMs: number | null = null;
        if (data.reserveExpiresAt) {
          const t = Date.parse(data.reserveExpiresAt);
          if (!Number.isNaN(t)) expMs = t;
        } else if (typeof data.ttlSeconds === "number" && data.ttlSeconds > 0) {
          expMs = Date.now() + data.ttlSeconds * 1000;
        } else {
          expMs = Date.now() + 15 * 60 * 1000;
        }
        setExpiresAtMs(expMs);
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : String(e);
        console.error(msg);
        setErrorMsg(msg || "Erro ao gerar QR Code.");
      } finally {
        if (isMountedRef.current) setLoading(false);
      }
    };
    run();
  }, [frete, cartItems, totalProdutos, desconto, totalComFrete, navigate, orderId]);

  // Contador regressivo
  useEffect(() => {
    if (!expiresAtMs) return;
    if (timerRef.current) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }

    const tick = () => {
      const sec = Math.max(0, Math.floor((expiresAtMs - Date.now()) / 1000));
      setRemainingSec(sec);
      if (sec <= 0) {
        closeSSE();
        if (timerRef.current) {
          window.clearInterval(timerRef.current);
          timerRef.current = null;
        }
      }
    };
    tick();
    timerRef.current = window.setInterval(tick, 1000);

    return () => {
      if (timerRef.current) {
        window.clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [expiresAtMs]);

  /**
   * Conexão SSE estável (useCallback) para não quebrar o exhaustive-deps.
   */
  const connectSSE = useCallback(
    (id: string) => {
      closeSSE();
      const url = `${API_BASE}/api/orders/${id}/events`;
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
          };
          sessionStorage.setItem(
            "ga_purchase_payload",
            JSON.stringify(payload)
          );
        } catch {
          /* no-op */
        }

        closeSSE();
        if (timerRef.current) {
          window.clearInterval(timerRef.current);
          timerRef.current = null;
        }
        cookieStorage.remove("cart");
        const checkoutForm = cookieStorage.get<CheckoutFormData | null>(
          "checkoutForm",
          null
        );
        const fullName = checkoutForm
          ? [checkoutForm.firstName, checkoutForm.lastName]
              .filter(Boolean)
              .join(" ")
              .trim()
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
    [navigate, expiresAtMs, cartItems, frete, desconto, totalComFrete]
  );

  useEffect(() => {
    if (!orderId) return;
    connectSSE(orderId);
    return () => {
      closeSSE();
      clearRetryTimer();
    };
  }, [orderId, connectSSE]);

  const handleReviewClick = () => {
    navigate("/checkout");
  };

  const isExpired = expiresAtMs !== null && remainingSec <= 0;

  return (
    <div className="max-w-3xl mx-auto p-6">
      <h2 className="text-2xl font-semibold mb-4">Resumo da Compra (Pix)</h2>

      {errorMsg && (
        <div className="mb-4 p-3 rounded bg-red-50 text-red-700 border border-red-200">
          {errorMsg}
        </div>
      )}

      {/* Avisos de Segurança */}
      <SecurityWarning warning={warning} securityWarning={securityWarning} />

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

      <div className="mt-6 text-right space-y-2">
        <p className="text-lg">Subtotal: {formatPrice(totalProdutos)}</p>
        <p className="text-lg">Frete: {formatPrice(frete ?? 0)}</p>
        {desconto > 0 && (
          <p className="text-lg text-green-600">
            Desconto: -{formatPrice(desconto)}
          </p>
        )}
        <p className="text-xl font-bold">
          Total: {formatPrice(totalComFrete)}
        </p>
      </div>

      <div className="mt-8 flex justify-between">
        <button
          onClick={handleReviewClick}
          className="bg-gray-300 hover:bg-gray-400 text-black px-4 py-2 rounded"
        >
          Revisar compra
        </button>
        <span className="text-sm text-gray-500 self-center" />
      </div>

      {loading && (
        <p className="text-center mt-8 text-gray-600">Gerando QR Code Pix...</p>
      )}

      {qrCodeImg && (
        <div className="mt-10 text-center space-y-3">
          {!isExpired ? (
            <>
              <p className="text-lg font-medium">
                Escaneie o QR Code com seu app do banco:
              </p>
              <img src={qrCodeImg} alt="QR Code Pix" className="mx-auto" />
              {pixCopiaECola && (
                <div className="max-w-xl mx-auto">
                  <p className="mt-4 text-sm text-gray-700">
                    Ou copie e cole no seu app:
                  </p>
                  <div className="flex gap-2 items-center mt-1">
                    <input
                      readOnly
                      value={pixCopiaECola}
                      className="flex-1 border rounded px-2 py-1 text-xs"
                    />
                    <button
                      onClick={() =>
                        navigator.clipboard.writeText(pixCopiaECola)
                      }
                      className="bg-black text-white px-3 py-1 rounded text-sm"
                    >
                      Copiar
                    </button>
                  </div>
                </div>
              )}
              {expiresAtMs && (
                <p className="text-sm text-gray-600 mt-2">
                  Este QR expira em{" "}
                  <span className="font-semibold">
                    {formatMMSS(remainingSec)}
                  </span>
                  .
                </p>
              )}
            </>
          ) : (
            <div className="p-4 border rounded bg-red-50 text-red-800 inline-block">
              <p className="font-medium">
                PIX invalidado por questões de segurança
              </p>
              <p className="text-sm">
                O PIX foi invalidado por questões de segurança aos 10 segundos
                restantes. Gere um novo pedido para tentar novamente.
              </p>
              <button
                className="mt-3 bg-black text-white px-4 py-2 rounded"
                onClick={() => navigate("/checkout")}
              >
                Voltar ao checkout
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
