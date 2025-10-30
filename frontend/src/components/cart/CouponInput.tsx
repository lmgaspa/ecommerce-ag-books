import React from 'react';

interface CouponInputProps {
  value: string;
  onChange: (value: string) => void;
  onApply: (orderTotal: number) => Promise<{ success: boolean; discountAmount?: number }>;
  isValidating?: boolean;
  isValid?: boolean;
  discount?: number;
}

const CouponInput: React.FC<CouponInputProps> = ({ 
  value, 
  onChange, 
  onApply, 
  isValidating = false,
  isValid = false,
  discount = 0
}) => {
  const handleApply = async () => {
    // Para o CouponInput, vamos usar um orderTotal padrão
    // O componente pai deve passar o orderTotal correto
    const result = await onApply(0);
    return result.success;
  };

  return (
    <div className="flex items-center gap-4 mb-4">
      <input
        type="text"
        placeholder="Código do cupom"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="border p-2 w-64 text-text-primary"
        disabled={isValidating}
      />
      <button 
        onClick={handleApply} 
        disabled={isValidating}
        className="px-6 py-2 bg-primary text-background rounded-md shadow-md transition hover:bg-secondary disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {isValidating ? 'Validando...' : 'Aplicar Cupom'}
      </button>
      {isValid && discount > 0 && (
        <span className="text-green-600 font-medium">
          Desconto: R$ {discount.toFixed(2)}
        </span>
      )}
    </div>
  );
};

export default CouponInput;