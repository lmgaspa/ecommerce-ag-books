// src/pages/CardPaymentPage.tsx
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

// ---- Efí (via pacote NPM) ----
import {
  tokenize,
  getInstallments,
  verifyBrandFromNumber,
  isScriptBlocked,
  type CardBrand,
  type InstallmentItem,
} from "../services/efiCard";
import { cookieStorage } from "../utils/cookieUtils";

/* ===================== Tipos e helpers locais ===================== */

interface CartItem {
  id: string;
  title: string;
  price: number;
  quantity: number;
  imageUrl: string;
}

interface CheckoutFormData {
  firstName: string;
  lastName: string;
  cpf: string;
  country: string;
  cep: string;
  address: string;
  number: string;
  complement?: string;
  district: string;
  city: string;
  state: string;
  phone: string;
  email: string;
  note?: string;
  shipping?: number;
}

type BrandUI = CardBrand; // "visa" | "mastercard" | "amex" | "elo" | "diners"

// Preferir vir das envs, com fallback no seu payee_code
const PAYEE_CODE =
  (import.meta as unknown as { env?: { VITE_EFI_PAYEE_CODE?: string } }).env?.VITE_EFI_PAYEE_CODE ??
  "cf1a4eb72fb74687e6a95a3da1bd027b";

const EFI_ENV: "production" | "sandbox" = import.meta.env.PROD ? "production" : "sandbox";

const API_BASE =
  ((import.meta as unknown as { env?: { VITE_API_BASE?: string } }).env?.VITE_API_BASE) ??
  "https://ecommerceag-6fa0e6a5edbf.herokuapp.com";

/* ---------- Helpers de formatação ---------- */
function formatCardNumber(value: string, brand: BrandUI): string {
  const digits = value.replace(/\D/g, "");
  if (brand === "amex") {
    const d = digits.slice(0, 15);
    return d
      .replace(/^(\d{1,4})(\d{1,6})?(\d{1,5})?$/, (_, a, b, c) =>
        [a, b, c].filter(Boolean).join(" ")
      )
      .trim();
  }
  return digits.slice(0, 16).replace(/(\d{4})(?=\d)/g, "$1 ").trim();
}

function formatMonthStrict(value: string): string {
  let d = value.replace(/\D/g, "").slice(0, 2);
  if (d.length === 1) {
    if (Number(d) > 1) d = `0${d}`;
  } else if (d.length === 2) {
    const n = Number(d);
    if (n === 0) d = "01";
    else if (n > 12) d = "12";
  }
  return d;
}

// >>> Correção: NÃO faz expansão automática de "20" -> "2020" durante a digitação
function formatYearYYYY(value: string): string {
  return value.replace(/\D/g, "").slice(0, 4); // aceita livremente até 4 dígitos
}

function formatCvv(value: string, brand: BrandUI): string {
  const max = brand === "amex" ? 4 : 3;
  return value.replace(/\D/g, "").slice(0, max);
}

function isValidLuhn(numDigits: string): boolean {
  let sum = 0,
    dbl = false;
  for (let i = numDigits.length - 1; i >= 0; i--) {
    let n = Number(numDigits[i]);
    if (dbl) {
      n *= 2;
      if (n > 9) n -= 9;
    }
    sum += n;
    dbl = !dbl;
  }
  return sum % 10 === 0;
}

function readJson<T>(key: string, fallback: T): T {
  return cookieStorage.get<T>(key, fallback);
}

const toYYYY = (yyOrYYYY: string) => {
  const d = yyOrYYYY.replace(/\D/g, "");
  return d.length === 2 ? `20${d}` : d.slice(0, 4);
};

/* ===================== Componente ===================== */

interface CardData {
  number: string;
  holderName: string;
  expirationMonth: string; // "MM"
  expirationYear: string;  // "YYYY" (ou "YY" durante digitação; normalizamos no blur/envio)
  cvv: string;             // 3/4
  brand: BrandUI;
}

export default function CardPaymentPage() {
  const navigate = useNavigate();

  const cart: CartItem[] = useMemo(() => readJson<CartItem[]>("cart", []), []);
  const form: CheckoutFormData = useMemo(
    () => readJson<CheckoutFormData>("checkoutForm", {} as CheckoutFormData),
    []
  );

  const shipping = Number(form?.shipping ?? 0);
  const subtotal = cart.reduce((acc, i) => acc + i.price * i.quantity, 0);
  const total = subtotal + shipping;

  useEffect(() => {
    if (!Array.isArray(cart) || cart.length === 0 || total <= 0) {
      navigate("/checkout");
    }
  }, [cart, total, navigate]);

  const [brand, setBrand] = useState<BrandUI>("visa");
  const [card, setCard] = useState<CardData>({
    number: "",
    holderName: "",
    expirationMonth: "",
    expirationYear: "",
    cvv: "",
    brand: "visa",
  });

  const [installments, setInstallments] = useState<number>(1);
  const [installmentOptions, setInstallmentOptions] = useState<InstallmentItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const numberDigits = card.number.replace(/\D/g, "");
  const cvvLen = brand === "amex" ? 4 : 3;

  // Checa bloqueio de fingerprint (opcional)
  useEffect(() => {
    (async () => {
      try {
        const blocked = await isScriptBlocked();
        if (blocked) console.warn("Fingerprint/script da Efí bloqueado por extensão!");
      } catch {
        // silencioso
      }
    })();
  }, []);

  // Detecta bandeira real quando o número tem BIN suficiente
  useEffect(() => {
    (async () => {
      if (numberDigits.length < 6) return;
      try {
        const b = await verifyBrandFromNumber(numberDigits);
        if (b !== "unsupported" && b !== "undefined") {
          setBrand(b as CardBrand);
          setCard(prev => ({
            ...prev,
            brand: b as BrandUI,
            number: formatCardNumber(prev.number, b as BrandUI),
            cvv: formatCvv(prev.cvv, b as BrandUI),
          }));
        }
      } catch {
        // ignora
      }
    })();
  }, [numberDigits]);

  // Carrega parcelas oficiais conforme conta/brand/total
  useEffect(() => {
    (async () => {
      try {
        const cents = Math.round(total * 100);
        if (cents <= 0) {
          setInstallmentOptions([]);
          setInstallments(1);
          return;
        }
        const resp = await getInstallments(PAYEE_CODE, EFI_ENV, brand as CardBrand, cents);
        setInstallmentOptions(resp.installments || []);
        if (resp.installments?.length) {
          setInstallments(resp.installments[0].installment);
        } else {
          setInstallments(1);
        }
      } catch (e) {
        console.error("Falha ao buscar parcelas:", e);
        setInstallmentOptions([]);
        setInstallments(1);
      }
    })();
  }, [brand, total]);

  // Handlers de input
  const onChangeBrand = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const newBrand = e.target.value as BrandUI;
    setBrand(newBrand);
    setCard((prev) => ({
      ...prev,
      brand: newBrand,
      number: formatCardNumber(prev.number, newBrand),
      cvv: formatCvv(prev.cvv, newBrand),
    }));
  };
  const onChangeNumber = (e: React.ChangeEvent<HTMLInputElement>) => {
    const b = brand;
    setCard((prev) => ({ ...prev, number: formatCardNumber(e.target.value, b) }));
  };
  const onChangeHolder = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({ ...prev, holderName: e.target.value }));
  };
  const onChangeMonth = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({ ...prev, expirationMonth: formatMonthStrict(e.target.value) }));
  };
  const onChangeYear = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({ ...prev, expirationYear: formatYearYYYY(e.target.value) }));
  };
  // Normaliza "YY" -> "20YY" apenas quando o campo perde foco (não trava na digitação)
  const onBlurYear = () => {
    setCard(prev => ({ ...prev, expirationYear: toYYYY(prev.expirationYear) }));
  };
  const onChangeCvv = (e: React.ChangeEvent<HTMLInputElement>) => {
    const b = brand;
    setCard((prev) => ({ ...prev, cvv: formatCvv(e.target.value, b) }));
  };

  // Validações básicas
  const lenOk =
    (brand === "amex" && numberDigits.length === 15) ||
    (brand !== "amex" && numberDigits.length >= 14 && numberDigits.length <= 16);
  const luhnOk = lenOk && isValidLuhn(numberDigits);
  const holderOk = card.holderName.trim().length > 0;
  const monthOk =
    /^\d{2}$/.test(card.expirationMonth) &&
    Number(card.expirationMonth) >= 1 &&
    Number(card.expirationMonth) <= 12;
  const yearOk = /^\d{4}$/.test(card.expirationYear); // YYYY obrigatório no estado final
  const cvvOk = new RegExp(`^\\d{${cvvLen}}$`).test(card.cvv);

  const holderDocument = (form.cpf ?? "").replace(/\D/g, "");
  const docOk = holderDocument.length >= 11; // regra simples p/ CPF

  const canPay =
    !loading && luhnOk && holderOk && monthOk && yearOk && cvvOk && docOk && total > 0;

  // Valor da parcela selecionada (usa oficial quando disponível)
  const selectedInstallment = installmentOptions.find(
    (opt) => opt.installment === installments
  );
  const perInstallment = useMemo(() => {
    if (selectedInstallment) return selectedInstallment.value / 100;
    if (installments <= 1) return total;
    return Math.round((total / installments) * 100) / 100;
  }, [selectedInstallment, installments, total]);

  // Pagar
  const handlePay = async () => {
    if (!canPay) return;
    setLoading(true);
    setErrorMsg(null);

    try {
      const tokenResp = await tokenize(PAYEE_CODE, EFI_ENV, {
        brand: brand as CardBrand,
        number: numberDigits,
        cvv: card.cvv,
        expirationMonth: card.expirationMonth,
        expirationYear: toYYYY(card.expirationYear), // garante YYYY no envio
        holderName: card.holderName,
        holderDocument,
        reuse: false,
      });

      const res = await fetch(`${API_BASE}/api/checkout/card`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...form,
          payment: "card",
          paymentToken: tokenResp.payment_token,
          installments,
          cartItems: cart,
          total,
          shipping,
        }),
      });

      if (!res.ok) {
        const txt = await res.text();
        throw new Error(txt || `Erro HTTP ${res.status}`);
      }

      const data: {
        success?: boolean;
        message?: string;
        orderId?: string;
        chargeId?: string | null;
        status?: string | null;
      } = await res.json();

      cookieStorage.remove("cart");

      const paidStatuses = ["PAID", "APPROVED", "CAPTURED", "CONFIRMED"];
      const isPaid = data.status
        ? paidStatuses.includes(String(data.status).toUpperCase())
        : false;

      navigate(
        `/pedido-confirmado?orderId=${data.orderId}&payment=card&paid=${isPaid ? "true" : "false"}`
      );
    } catch (e) {
      setErrorMsg(e instanceof Error ? e.message : "Falha no pagamento.");
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-md mx-auto p-6">
      <h2 className="text-xl font-semibold mb-4 text-center">Pagamento com Cartão</h2>

      <label className="block text-sm font-medium mb-1">Bandeira</label>
      <select value={brand} onChange={onChangeBrand} className="border p-2 w-full mb-4 rounded">
        <option value="visa">Visa</option>
        <option value="mastercard">Mastercard</option>
        <option value="amex">American Express</option>
        <option value="elo">Elo</option>
        <option value="diners">Diners</option>
        {/* Removido Hipercard: não consta como suportado na doc que você enviou */}
      </select>

      <input
        value={card.number}
        onChange={onChangeNumber}
        placeholder={
          brand === "amex"
            ? "Número do cartão (ex.: 3714 496353 98431)"
            : "Número do cartão (ex.: 4111 1111 1111 1111)"
        }
        className="border p-2 w-full mb-2 rounded"
        inputMode="numeric"
        autoComplete="cc-number"
      />

      <input
        value={card.holderName}
        onChange={onChangeHolder}
        placeholder="Nome impresso"
        className="border p-2 w-full mb-2 rounded"
        autoComplete="cc-name"
      />

      <div className="flex gap-2">
        <input
          value={card.expirationMonth}
          onChange={onChangeMonth}
          placeholder="MM"
          className="border p-2 w-1/2 mb-2 rounded"
          inputMode="numeric"
          autoComplete="cc-exp-month"
        />
        <input
          value={card.expirationYear}
          onChange={onChangeYear}
          onBlur={onBlurYear}
          placeholder="AAAA"
          className="border p-2 w-1/2 mb-2 rounded"
          inputMode="numeric"
          autoComplete="cc-exp-year"
        />
      </div>

      <input
        value={card.cvv}
        onChange={onChangeCvv}
        placeholder={`CVV (${cvvLen} dígitos)`}
        className="border p-2 w-full mb-4 rounded"
        inputMode="numeric"
        autoComplete="cc-csc"
      />

      <label className="block text-sm font-medium mb-1">Parcelas</label>
      <select
        className="border p-2 w-full rounded mb-2"
        value={installments}
        onChange={(e) => setInstallments(Number(e.target.value))}
      >
        {installmentOptions.length > 0
          ? installmentOptions.map((opt) => (
              <option value={opt.installment} key={opt.installment}>
                {opt.installment}x de R$ {(opt.value / 100).toFixed(2)}{" "}
                {opt.has_interest ? " (c/ juros)" : " (s/ juros)"}
              </option>
            ))
          : [1, 2, 3, 4, 5, 6].map((n) => (
              <option value={n} key={n}>
                {n}x
              </option>
            ))}
      </select>
      <p className="text-sm text-gray-600 mb-4">
        {installments}x de R$ {perInstallment.toFixed(2)} (total R$ {total.toFixed(2)})
      </p>

      {!docOk && (
        <div className="bg-yellow-50 text-yellow-700 p-2 mb-3 rounded">
          Informe um CPF válido no passo anterior para continuar.
        </div>
      )}

      {errorMsg && (
        <div className="bg-red-50 text-red-600 p-2 mb-4 rounded">
          {errorMsg}
        </div>
      )}

      <button
        disabled={!canPay}
        onClick={handlePay}
        className={`bg-blue-600 text-white py-2 w-full rounded ${
          canPay ? "hover:bg-blue-500" : "opacity-50 cursor-not-allowed"
        }`}
      >
        {loading ? "Processando..." : "Pagar com Cartão"}
      </button>
    </div>
  );
}
