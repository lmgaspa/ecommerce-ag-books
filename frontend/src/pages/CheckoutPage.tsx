// src/pages/CheckoutPage.tsx
import { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { useCart } from "../hooks/useCart";
import { useCoupon } from "../hooks/useCoupon";
import { formatCep, formatCpf, formatCelular } from "../utils/masks";
import CheckoutForm from "./CheckoutForm";
import type { CartItem } from "../context/CartTypes";
import { calcularFreteComBaseEmCarrinho } from "../utils/freteUtils";
import { getStockByIds } from "../api/stock";
import { cookieStorage } from "../utils/cookieUtils";
import { analytics, mapCartItems } from "../analytics";

type FormState = {
  firstName: string;
  lastName: string;
  cpf: string;
  country: string;
  cep: string;
  address: string;
  number: string;
  complement: string;
  district: string;
  city: string;
  state: string;
  phone: string;
  email: string;
  note: string;
  delivery: string;
  payment: string;
  shipping?: number;
};

const DEFAULT_FORM: FormState = {
  firstName: "",
  lastName: "",
  cpf: "",
  country: "Brasil",
  cep: "",
  address: "",
  number: "",
  complement: "",
  district: "",
  city: "",
  state: "",
  phone: "",
  email: "",
  note: "",
  delivery: "",
  payment: "pix",
  shipping: 0,
};

const CheckoutPage = () => {
  const navigate = useNavigate();
  const { getCart } = useCart();
  const { 
    applyCoupon, 
    getDiscountAmount, 
    isValid: couponValid, 
    discount: couponDiscount, 
    inputValue, 
    setInputValue,
    isValidating
  } = useCoupon();
  const [cartItems, setCartItems] = useState<CartItem[]>([]);
  const [totalItems, setTotalItems] = useState(0);
  const [shipping, setShipping] = useState(0);
  const [stockById, setStockById] = useState<Record<string, number>>({});
  const [form, setForm] = useState<FormState>(() =>
    cookieStorage.get<FormState>("checkoutForm", DEFAULT_FORM)
  );

  const onNavigateBack = () => navigate("/books");

  useEffect(() => {
    const cart = getCart();
    (async () => {
      const ids = cart.map((c) => c.id);
      const stockMap = await getStockByIds(ids);
      const stockDict = Object.fromEntries(
        ids.map((id) => [id, Math.max(0, stockMap[id]?.stock ?? 0)])
      );
      setStockById(stockDict);

      const fixed = cart
        .map((i) => ({
          ...i,
          quantity: Math.min(i.quantity, Math.max(0, stockDict[i.id] ?? 0)),
        }))
        .filter((i) => i.quantity > 0);

      const sum = fixed.reduce((acc, item) => acc + item.price * item.quantity, 0);

      setCartItems(fixed);
      setTotalItems(sum);
      cookieStorage.set("cart", fixed);

      if (
        fixed.length !== cart.length ||
        JSON.stringify(fixed) !== JSON.stringify(cart)
      ) {
        alert("Atualizamos seu carrinho de acordo com o estoque atual.");
      }
    })();
  }, []);

  const cpfCepInfo = useMemo(() => {
    const cpf = form.cpf.replace(/\D/g, "");
    const cep = form.cep.replace(/\D/g, "");
    const phone = form.phone.replace(/\D/g, "");
    return { cpf, cep, phone };
  }, [form]);

  // Calcula frete e envia GA4: add_shipping_info
  useEffect(() => {
    if (
      cpfCepInfo.cpf === "00000000000" ||
      cpfCepInfo.cep.length !== 8 ||
      cartItems.length === 0
    ) {
      setShipping(0);
      return;
    }

    calcularFreteComBaseEmCarrinho(
      { cpf: cpfCepInfo.cpf, cep: cpfCepInfo.cep },
      cartItems
    )
      .then((v) => {
        setShipping(v);

        // GA4: add_shipping_info (OCP)
        try {
          const itemsPayload = mapCartItems(cartItems);
          const orderValue = cartItems.reduce(
            (acc, i) => acc + i.price * i.quantity,
            0
          );
          analytics.addShippingInfo({
            items: itemsPayload,
            value: Number(orderValue + v),
            shipping: Number(v),
            shipping_tier: "default",
            currency: "BRL",
          });
        } catch {
          // no-op
        }
      })
      .catch(() => setShipping(0));
  }, [cpfCepInfo, cartItems]);

  useEffect(() => {
    cookieStorage.set("checkoutForm", { ...form, shipping });
  }, [form, shipping]);

  const updateQuantity = (id: string, delta: number) => {
    const updated = cartItems
      .map((item) => {
        if (item.id !== id) return item;
        const max = stockById[id] ?? Infinity;
        const next = item.quantity + delta;
        if (next > max) {
          alert("Quantidade excede o estoque disponível.");
          return item;
        }
        if (next <= 0) return null;
        return { ...item, quantity: next };
      })
      .filter(Boolean) as CartItem[];

    setCartItems(updated);
    cookieStorage.set("cart", updated);
    setTotalItems(updated.reduce((acc, it) => acc + it.price * it.quantity, 0));
  };

  const removeItem = (id: string) => {
    const updated = cartItems.filter((i) => i.id !== id);
    setCartItems(updated);
    cookieStorage.set("cart", updated);
    setTotalItems(updated.reduce((acc, it) => acc + it.price * it.quantity, 0));
  };

  const handleApplyCoupon = async () => {
    const success = await applyCoupon(inputValue, totalItems);
    if (success) {
      const appliedDiscount = getDiscountAmount(totalItems);
      alert(`Cupom aplicado com sucesso! Desconto de R$ ${appliedDiscount.toFixed(2)}`);
    }
  };

  const handleChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>
  ) => {
    const { name, value: raw } = e.target;
    let value = raw;
    if (name === "cep") value = formatCep(value);
    if (name === "cpf") value = formatCpf(value);
    if (name === "phone") value = formatCelular(value);
    setForm((prev) => ({ ...prev, [name]: value }));

    if (name === "cep" && value.replace(/\D/g, "").length === 8) {
      fetch(`https://viacep.com.br/ws/${value.replace(/\D/g, "")}/json/`)
        .then((res) => res.json())
        .then((data) => {
          setForm((prev) => ({
            ...prev,
            address: data.logradouro || "",
            district: data.bairro || "",
            city: data.localidade || "",
            state: data.uf || "",
          }));
        })
        .catch(() => {
          // silencioso
        });
    }
  };

  return (
    <CheckoutForm
      cartItems={cartItems}
      total={totalItems + shipping - getDiscountAmount(totalItems)}
      shipping={shipping}
      discount={getDiscountAmount(totalItems)}
      coupon={inputValue}
      setCoupon={setInputValue}
      handleApplyCoupon={handleApplyCoupon}
      couponValid={couponValid}
      couponDiscount={couponDiscount}
      isValidating={isValidating}
      form={form}
      updateQuantity={updateQuantity}
      removeItem={removeItem}
      handleChange={handleChange}
      onNavigateBack={onNavigateBack}
    />
  );
};

export default CheckoutPage;
