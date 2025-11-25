// src/pages/PedidoConfirmado.tsx
import { useMemo } from "react";
import { useSearchParams, Link } from "react-router-dom";

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

  // Regras de exibição:
  // - PIX => "Pagamento confirmado via Pix 🎉"
  // - Cartão pago => "Pagamento aprovado via Cartão de Crédito 🎉"
  // - Cartão não pago (caso algum dia essa página seja usada antes da aprovação) => "Aguardando aprovação"
  // - Caso sem parâmetro válido => "Pedido registrado"
  const isPix = payment === "pix";
  const isCard = payment === "card";
  const isPaid = isPix || (isCard && paid);

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

      // Fallback "aguardando aprovação" mantido por segurança/OCP,
      // embora na regra atual essa página só deva ser acessada após aprovação.
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
      <h1 className="text-2xl font-semibold mb-2">
        Pedido registrado ✅
      </h1>
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
