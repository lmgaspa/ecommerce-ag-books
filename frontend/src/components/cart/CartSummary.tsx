import React from 'react';

interface CartSummaryProps {
  subtotal: number;
  discount: number;
  onCheckout: () => void;
}

const CartSummary: React.FC<CartSummaryProps> = ({ subtotal, discount, onCheckout }) => {
  const total = subtotal - discount;

  return (
    <div>
      <p className="text-text-primary">Subtotal: R${subtotal.toFixed(2)}</p>
      <p className="text-text-primary">Desconto: -R${discount.toFixed(2)}</p>
      <p className="text-text-primary">Total: R${total.toFixed(2)}</p>
      <button onClick={onCheckout} className="bg-primary text-background px-2 py-4 mt-2 rounded-md shadow-md transition hover:bg-secondary">
        Continuar para Finalização
      </button>
    </div>
  );
};

export default CartSummary;