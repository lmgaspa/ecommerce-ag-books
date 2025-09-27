import { useMemo } from "react";
import { useSearchParams, Link } from "react-router-dom";

export default function PedidoConfirmado() {
  const [params] = useSearchParams();

  const orderId = useMemo(() => params.get("orderId"), [params]);
  const name = useMemo(() => params.get("name"), [params]);
  const payment = useMemo(() => params.get("payment"), [params]); // "pix" | "card"
  const paid = useMemo(() => params.get("paid") === "true", [params]); // só cartão

  const renderMessage = () => {
    if (payment === "pix") {
      return (
        <>
          <h1 className="text-2xl font-semibold mb-2">Pagamento confirmado 🎉</h1>
          {name && <p className="text-gray-700 mb-1">Obrigado, <strong>{name}</strong>!</p>}
          {orderId && <p className="text-gray-700">Pedido #{orderId}</p>}
          <p className="text-gray-600 mt-2">
            Você receberá um e-mail com os detalhes do pedido em instantes.
          </p>
        </>
      );
    }

    if (payment === "card") {
      if (paid) {
        return (
          <>
            <h1 className="text-2xl font-semibold mb-2">Pagamento aprovado 🎉</h1>
            {orderId && <p className="text-gray-700">Pedido #{orderId}</p>}
            <p className="text-gray-600 mt-2">
              Seu pagamento com cartão foi aprovado. Em breve você receberá um e-mail com os detalhes.
            </p>
          </>
        );
      } else {
        return (
          <>
            <h1 className="text-2xl font-semibold mb-2">Aguardando aprovação do cartão ⏳</h1>
            {orderId && <p className="text-gray-700">Pedido #{orderId}</p>}
            <p className="text-gray-600 mt-2">
              Estamos processando o pagamento com cartão. Você receberá um e-mail assim que houver atualização.
            </p>
          </>
        );
      }
    }

    return <h1 className="text-2xl font-semibold mb-2">Pedido registrado ✅</h1>;
  };

  return (
    <div className="max-w-2xl mx-auto p-6 text-center">
      {renderMessage()}
      <Link to="/" className="inline-block mt-6 bg-black text-white px-4 py-2 rounded">
        Voltar para a loja
      </Link>
    </div>
  );
}