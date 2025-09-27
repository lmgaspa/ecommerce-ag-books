// src/components/checkout/CheckoutFormView.tsx
import React from "react";
import { formatPrice } from "../../utils/formatPrice";
import type { CartItem } from "../../context/CartTypes";

interface CheckoutFormViewProps {
  cartItems: CartItem[];
  total: number; // já com frete somado
  shipping: number;
  form: {
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
    payment: string; // "pix" | "card"
  };
  handleChange: (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>
  ) => void;
  updateQuantity: (id: string, delta: number) => void;
  removeItem: (id: string) => void;
  handleCheckout: () => void;
  onNavigateBack: () => void;
}

const CheckoutFormView: React.FC<CheckoutFormViewProps> = ({
  cartItems,
  total,
  shipping,
  form,
  handleChange,
  updateQuantity,
  removeItem,
  handleCheckout,
  onNavigateBack, 
}) => (
  <div className="max-w-5xl mx-auto px-4 pt-2 pb-10">
    {" "}
    {/* reduz top padding */}
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mt-0">
      {" "}
      {/* sem margem no topo */}
      {/* Coluna esquerda: dados do cliente */}
      <div className="lg:col-span-2 space-y-6">
        <div className="mb-4">
          <button
            onClick={onNavigateBack}
            type="button"
            className="px-6 py-2 bg-green-600 text-white rounded-md shadow-md transition hover:bg-green-700"
          >
            ← Continuar comprando
          </button>
        </div>

        <h2 className="text-lg font-bold">DADOS DE COBRANÇA E ENTREGA</h2>

        <div className="grid grid-cols-2 gap-4">
          <input
            id="firstName"
            name="firstName"
            autoComplete="given-name"
            value={form.firstName}
            onChange={handleChange}
            placeholder="Nome"
            className="border p-2"
          />
          <input
            id="lastName"
            name="lastName"
            autoComplete="family-name"
            value={form.lastName}
            onChange={handleChange}
            placeholder="Sobrenome"
            className="border p-2"
          />

          {/* CPF */}
          <input
            id="cpf"
            name="cpf"
            autoComplete="on"
            inputMode="numeric"
            value={form.cpf}
            onChange={handleChange}
            placeholder="CPF"
            className="border p-2 col-span-2"
          />

          <input value="Brasil" disabled className="border p-2 col-span-2" />
          <input
            id="cep"
            name="cep"
            autoComplete="postal-code"
            inputMode="numeric"
            value={form.cep}
            onChange={handleChange}
            placeholder="CEP (Ex: 00000-000)"
            className="border p-2 col-span-2"
          />
          <input
            id="address"
            name="address"
            autoComplete="address-line1"
            value={form.address}
            onChange={handleChange}
            placeholder="Endereço"
            className="border p-2 col-span-2"
          />
          <input
            id="number"
            name="number"
            autoComplete="address-line2"
            value={form.number}
            onChange={handleChange}
            placeholder="Número"
            className="border p-2"
          />
          <input
            id="complement"
            name="complement"
            autoComplete="address-line2"
            value={form.complement}
            onChange={handleChange}
            placeholder="Complemento (opcional)"
            className="border p-2"
          />
          <input
            id="district"
            name="district"
            autoComplete="address-level3"
            value={form.district}
            onChange={handleChange}
            placeholder="Bairro"
            className="border p-2"
          />
          <input
            id="city"
            name="city"
            autoComplete="address-level2"
            value={form.city}
            onChange={handleChange}
            placeholder="Cidade"
            className="border p-2"
          />
          <input
            id="state"
            name="state"
            autoComplete="address-level1"
            value={form.state}
            onChange={handleChange}
            placeholder="Estado"
            className="border p-2"
          />
          <input
            id="phone"
            name="phone"
            autoComplete="tel"
            inputMode="tel"
            value={form.phone}
            onChange={handleChange}
            placeholder="Celular"
            className="border p-2 col-span-2"
          />
          <input
            id="email"
            name="email"
            autoComplete="email"
            type="email"
            value={form.email}
            onChange={handleChange}
            placeholder="E-mail"
            className="border p-2 col-span-2"
          />
        </div>

        <div>
          <h2 className="text-lg font-bold mt-6">INFORMAÇÕES ADICIONAIS</h2>
          <textarea
            id="note"
            name="note"
            value={form.note}
            onChange={handleChange}
            placeholder="Observações sobre seu pedido..."
            className="border w-full p-2 h-24"
          />
        </div>
      </div>
      {/* Coluna direita: resumo e forma de pagamento */}
      <div>
        <h2 className="text-lg font-bold">SEU PEDIDO</h2>

        {cartItems.map((item) => (
          <div
            key={item.id}
            className="flex items-center justify-between gap-4 border-b pb-2"
          >
            <img
              src={item.imageUrl}
              alt={item.title}
              className="w-12 h-16 object-cover rounded shadow"
            />
            <div className="flex-1">
              <p className="text-sm font-semibold text-text-primary">
                {item.title}
              </p>
              <div className="flex items-center gap-2 mt-1">
                <button
                  type="button"
                  className="bg-gray-200 px-2 py-1 rounded hover:bg-gray-300"
                  onClick={() => updateQuantity(item.id, -1)}
                >
                  -
                </button>
                <span className="text-sm">{item.quantity}</span>
                <button
                  type="button"
                  className="bg-gray-200 px-2 py-1 rounded hover:bg-gray-300"
                  onClick={() => updateQuantity(item.id, 1)}
                >
                  +
                </button>
                <button
                  type="button"
                  className="ml-2 text-red-500 text-xs hover:underline"
                  onClick={() => removeItem(item.id)}
                >
                  Remover
                </button>
              </div>
            </div>
            <span className="text-sm font-medium">
              {formatPrice(item.price * item.quantity)}
            </span>
          </div>
        ))}

        <div className="border-t my-3" />

        {/* Forma de pagamento — AGORA AQUI */}
        <div className="mb-4">
          <h3 className="text-md font-semibold mb-2">Forma de pagamento</h3>
          <div className="flex items-center gap-6">
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="radio"
                name="payment"
                value="pix"
                checked={form.payment === "pix"}
                onChange={handleChange}
              />
              <span>Pix</span>
            </label>
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="radio"
                name="payment"
                value="card"
                checked={form.payment === "card"}
                onChange={handleChange}
              />
              <span>Cartão de Crédito</span>
            </label>
          </div>
        </div>

        <div className="flex justify-between">
          <span>Entrega</span>
          <span>{shipping > 0 ? formatPrice(shipping) : "---"}</span>
        </div>

        <div className="flex justify-between font-bold">
          <span>Total</span>
          <span>{formatPrice(total)}</span>
        </div>

        <p className="text-xs text-center text-red-600 mt-4">
          {form.payment === "pix"
            ? "Você pagará via Pix na próxima etapa."
            : "Você informará os dados do cartão na próxima etapa."}
        </p>

        <button
          onClick={handleCheckout}
          type="button"
          className="bg-red-600 text-white py-2 w-full mt-4 rounded hover:bg-red-500 transition"
        >
          {form.payment === "pix"
            ? "Finalizar Pagamento por Pix"
            : "Ir para pagamento com Cartão"}
        </button>
      </div>
    </div>
  </div>
);

export default CheckoutFormView;