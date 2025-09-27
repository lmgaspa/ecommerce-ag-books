import { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { useCart } from "../hooks/useCart";
import { formatCep, formatCpf, formatCelular } from "../utils/masks";
import CheckoutForm from "./CheckoutForm";
import type { CartItem } from "../context/CartTypes";
import { calcularFreteComBaseEmCarrinho } from "../utils/freteUtils";
import { getStockByIds } from "../api/stock";

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

  const [cartItems, setCartItems] = useState<CartItem[]>([]);
  const [totalItems, setTotalItems] = useState(0); // soma dos itens
  const [shipping, setShipping] = useState(0);
  const [stockById, setStockById] = useState<Record<string, number>>({});

  // Formulário com reidratação e SEM reinit ao mudar carrinho
  const [form, setForm] = useState<FormState>(() => {
    try {
      const saved = localStorage.getItem("checkoutForm");
      return saved ? { ...DEFAULT_FORM, ...JSON.parse(saved) } : DEFAULT_FORM;
    } catch {
      return DEFAULT_FORM;
    }
  });

  const onNavigateBack = () => navigate("/books");

  // Carrinho + estoque (não toca no form)
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
        .map((i) => {
          const s = stockDict[i.id] ?? 0;
          const qty = Math.min(i.quantity, Math.max(0, s));
          return { ...i, quantity: qty };
        })
        .filter((i) => i.quantity > 0);

      const sum = fixed.reduce((acc, item) => acc + item.price * item.quantity, 0);

      setCartItems(fixed);
      setTotalItems(sum);
      localStorage.setItem("cart", JSON.stringify(fixed));

      if (fixed.length !== cart.length || JSON.stringify(fixed) !== JSON.stringify(cart)) {
        alert("Atualizamos seu carrinho de acordo com o estoque atual.");
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Normaliza p/ frete
  const cpfCepInfo = useMemo(() => {
    const cpf = form.cpf.replace(/\D/g, "");
    const cep = form.cep.replace(/\D/g, "");
    const phone = form.phone.replace(/\D/g, "");
    return { cpf, cep, phone };
  }, [form.cpf, form.cep, form.phone]);

  // Frete (não reseta form)
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
      .then(setShipping)
      .catch(() => setShipping(0));
  }, [cpfCepInfo, cartItems]);

  // Persistência do form + shipping a cada alteração de qualquer campo
  useEffect(() => {
    localStorage.setItem("checkoutForm", JSON.stringify({ ...form, shipping }));
  }, [form, shipping]);

  // ++ / -- (não reinit form)
  const updateQuantity = (id: string, delta: number) => {
    const updated = cartItems
      .map((item) => {
        if (item.id !== id) return item;
        const max = stockById[id] ?? Infinity;
        const next = item.quantity + delta;
        if (delta > 0 && next > max) {
          alert("Quantidade solicitada excede o estoque disponível.");
          return item;
        }
        if (next <= 0) return null;
        return { ...item, quantity: next };
      })
      .filter((it): it is CartItem => it !== null);

    setCartItems(updated);
    localStorage.setItem("cart", JSON.stringify(updated));
    const sum = updated.reduce((acc, it) => acc + it.price * it.quantity, 0);
    setTotalItems(sum);
    if (!updated.length) setShipping(0);
  };

  const removeItem = (id: string) => {
    const updated = cartItems.filter((item) => item.id !== id);
    setCartItems(updated);
    localStorage.setItem("cart", JSON.stringify(updated));
    const sum = updated.reduce((acc, it) => acc + it.price * it.quantity, 0);
    setTotalItems(sum);
    if (!updated.length) setShipping(0);
  };

  // Mudanças de campos (inputs/textarea) + máscaras + ViaCEP
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
        .catch(() => {});
    }
  };

  return (
    <CheckoutForm
      cartItems={cartItems}
      total={totalItems + shipping}
      shipping={shipping}
      form={form}
      updateQuantity={updateQuantity}
      removeItem={removeItem}
      handleChange={handleChange}
      onNavigateBack={onNavigateBack}
    />
  );
};

export default CheckoutPage;