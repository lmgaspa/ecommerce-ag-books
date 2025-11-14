// src/components/common/BookPrice.tsx
import React from "react";

interface BookPriceProps {
  price: string | number | undefined | null;
  className?: string;
}

/**
 * Componente global para exibir preço de livros
 * Formata como "Preço: R$ xx,xx" com espaço entre R$ e o valor
 */
const BookPrice: React.FC<BookPriceProps> = ({ price, className = "" }) => {
  const formatPrice = (priceValue: string | number | undefined | null): string => {
    // Se for null/undefined, retorna valor padrão
    if (priceValue === null || priceValue === undefined) return "R$ 0,00";
    
    // Converte para string se for número
    const priceStr = typeof priceValue === "number" 
      ? priceValue.toFixed(2).replace(".", ",")
      : String(priceValue);
    
    // Se já tem espaço, retorna como está
    if (priceStr.includes("R$ ")) return priceStr;
    
    // Se tem R$ mas sem espaço, adiciona espaço
    if (priceStr.includes("R$")) {
      return priceStr.replace("R$", "R$ ");
    }
    
    // Se não tem R$, adiciona
    return `R$ ${priceStr}`;
  };

  return (
    <span className={className}>
      Preço: {formatPrice(price)}
    </span>
  );
};

export default BookPrice;

