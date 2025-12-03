// src/pages/CheckoutPage.tsx
import { useState, useEffect, useMemo, useCallback, useRef, useContext } from "react";
import { useNavigate } from "react-router-dom";
import { useCoupon } from "../hooks/useCoupon";
import { formatCep, formatCpf, formatCelular } from "../utils/masks";
import CheckoutForm from "./CheckoutForm";
import type { CartItem } from "../context/CartTypes";
import { calcularFreteComBaseEmCarrinho } from "../utils/freteUtils";
import { getStockByIds } from "../api/stock";
import { cookieStorage } from "../utils/cookieUtils";
import { analytics, mapCartItems } from "../analytics";
import { CartContext } from "../context/CartContext";

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
  const cartContext = useContext(CartContext);
  const { 
    applyCoupon, 
    getDiscountAmount, 
    isValid: couponValid, 
    inputValue, 
    setInputValue,
    isValidating
  } = useCoupon();
  const [stockById, setStockById] = useState<Record<string, number>>({});
  const [form, setForm] = useState<FormState>(() =>
    cookieStorage.get<FormState>("checkoutForm", DEFAULT_FORM)
  );
  const [shipping, setShipping] = useState(() => {
    const savedForm = cookieStorage.get<FormState>("checkoutForm", DEFAULT_FORM);
    return savedForm.shipping ?? 0;
  });

  if (!cartContext) {
    throw new Error("CheckoutPage must be used within a CartProvider");
  }

  const { cartItems, updateQuantity: updateQuantityContext, removeFromCart, totalPrice } = cartContext;
  const totalItems = totalPrice;

  const onNavigateBack = () => navigate("/books");

  // GA4: controla se já disparamos o begin_checkout nesta visita.
  // Extensão de comportamento (analytics) sem alterar o fluxo principal (OCP).
  const beginCheckoutTrackedRef = useRef(false);
  const initializedRef = useRef(false);
  
  // Carrega estoque e valida carrinho ao montar
  useEffect(() => {
    if (initializedRef.current || cartItems.length === 0) return;
    initializedRef.current = true;

    (async () => {
      const ids = cartItems.map((c) => c.id);
      const stockMap = await getStockByIds(ids);
      const stockDict = Object.fromEntries(
        ids.map((id) => [id, Math.max(0, stockMap[id]?.stock ?? 0)])
      );
      setStockById(stockDict);

      // Valida e ajusta quantidades baseado no estoque
      const fixed = cartItems
        .map((i) => ({
          ...i,
          quantity: Math.min(i.quantity, Math.max(0, stockDict[i.id] ?? 0)),
        }))
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

  const cpfCepInfo = useMemo(() => {
    const cpf = form.cpf.replace(/\D/g, "");
    const cep = form.cep.replace(/\D/g, "");
    const phone = form.phone.replace(/\D/g, "");
    return { cpf, cep, phone };
  }, [form]);

  const cartItemsRef = useRef<CartItem[]>([]);
  
  // Atualizar ref quando cartItems mudar
  useEffect(() => {
    cartItemsRef.current = cartItems;
  }, [cartItems]);

  // GA4: begin_checkout — “Início da finalização da compra”
  // Disparamos UMA VEZ quando o usuário chega à página de checkout com itens no carrinho.
  // Isso estende o comportamento com analytics, mantendo o fluxo de negócio intacto (OCP).
  useEffect(() => {
    if (beginCheckoutTrackedRef.current) return;
    if (!cartItems || cartItems.length === 0) return;

    try {
      const itemsPayload = mapCartItems(cartItems);
      const value = totalItems + shipping - getDiscountAmount(totalItems);

      analytics.beginCheckout({
        items: itemsPayload,
        value: Number(value),
        currency: "BRL",
        // no futuro podemos passar cupom, se quiser:
        // coupon: inputValue || undefined,
      });

      beginCheckoutTrackedRef.current = true;
    } catch {
      // nunca deixamos o fluxo de checkout quebrar por causa de tracking
    }
  }, [cartItems, totalItems, shipping, getDiscountAmount]);

  // Sincronizar shipping com o valor salvo no formulário
  useEffect(() => {
    if (form.shipping !== undefined && form.shipping !== shipping) {
      setShipping(form.shipping);
    }
  }, [form.shipping, shipping]);

  // Função estabilizada para calcular frete
  const calcularFrete = useCallback(async () => {
    const currentCartItems = cartItemsRef.current;
    
    // Se já temos um frete válido salvo, usar ele
    if (form.shipping && form.shipping > 0) {
      setShipping(form.shipping);
      return;
    }
    
    if (
      cpfCepInfo.cpf === "00000000000" ||
      cpfCepInfo.cep.length !== 8 ||
      currentCartItems.length === 0
    ) {
      setShipping(0);
      return;
    }

    // Se já temos um frete calculado e válido, não recalcular
    if (shipping > 0 && form.shipping === shipping) {
      return;
    }

    try {
      const v = await calcularFreteComBaseEmCarrinho(
        { cpf: cpfCepInfo.cpf, cep: cpfCepInfo.cep },
        currentCartItems
      );
      
      setShipping(v);

      // GA4: add_shipping_info (mantido para uso presente/futuro)
      try {
        const itemsPayload = mapCartItems(currentCartItems);
        const orderValue = currentCartItems.reduce(
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
    } catch {
      setShipping(0);
    }
  }, [cpfCepInfo, shipping, form.shipping]);

  // Calcula frete e envia GA4: add_shipping_info
  useEffect(() => {
    calcularFrete();
  }, [calcularFrete]);

  // Salvar formulário no cookie (sem causar loop)
  useEffect(() => {
    const timeoutId = setTimeout(() => {
      cookieStorage.set("checkoutForm", { ...form, shipping });
    }, 100); // Debounce para evitar loops
    
    return () => clearTimeout(timeoutId);
  }, [form, shipping]);

  const updateQuantity = useCallback((id: string, delta: number) => {
    const max = stockById[id] ?? Infinity;
    const currentItem = cartItems.find((i) => i.id === id);
    if (!currentItem) return;

    const next = currentItem.quantity + delta;
    if (next > max) {
      alert("Quantidade excede o estoque disponível.");
      return;
    }
    if (next <= 0) {
      removeFromCart(id);
      return;
    }
    updateQuantityContext(id, delta);
  }, [stockById, cartItems, updateQuantityContext, removeFromCart]);

  const removeItem = useCallback((id: string) => {
    removeFromCart(id);
  }, [removeFromCart]);

  const handleApplyCoupon = async (): Promise<{ success: boolean; discountAmount?: number }> => {
    const result = await applyCoupon(inputValue, totalItems);
    if (result.success && result.discountAmount !== undefined) {
      // Usar o valor retornado pela API, mas limitado a R$ 15
      const maxDiscount = 15.00;
      const actualDiscount = Math.min(result.discountAmount, maxDiscount);
      alert(`Cupom aplicado com sucesso! Desconto de R$ ${actualDiscount.toFixed(2)}`);
    }
    return result;
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
