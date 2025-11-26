// src/components/OrderSummary.tsx
type OrderSummaryProps = {
  subtotal: number;
  shipping: number;
  discount: number;
  total: number;
};

export function OrderSummary({
  subtotal,
  shipping,
  discount,
  total,
}: OrderSummaryProps) {
  return (
    <div className="bg-gray-50 p-4 mb-6 rounded-lg">
      <h3 className="text-lg font-semibold mb-3">Resumo do Pedido</h3>
      <div className="space-y-2">
        <div className="flex justify-between">
          <span>Subtotal:</span>
          <span>R$ {subtotal.toFixed(2).replace(".", ",")}</span>
        </div>
        <div className="flex justify-between">
          <span>Frete:</span>
          <span>R$ {shipping.toFixed(2).replace(".", ",")}</span>
        </div>
        {discount > 0 && (
          <div className="flex justify-between text-green-600">
            <span>Desconto:</span>
            <span>-R$ {discount.toFixed(2).replace(".", ",")}</span>
          </div>
        )}
        <div className="flex justify-between font-bold text-lg border-t pt-2">
          <span>Total:</span>
          <span>R$ {total.toFixed(2).replace(".", ",")}</span>
        </div>
      </div>
    </div>
  );
}
