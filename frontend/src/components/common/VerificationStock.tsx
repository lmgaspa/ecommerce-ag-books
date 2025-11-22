import React from "react";

interface VerificationStockProps {
  text?: string;
  className?: string;
  size?: "sm" | "md";
}

const VerificationStock: React.FC<VerificationStockProps> = ({ 
  text = "Verificando estoque…", 
  className = "",
  size = "md"
}) => {
  const sizeClasses = {
    sm: "px-3 py-1.5 text-sm",
    md: "px-6 py-2"
  };

  return (
    <button
      disabled
      className={`${sizeClasses[size]} rounded-md shadow-md transition bg-gray-400 text-white cursor-not-allowed ${className}`}
    >
      {text}
    </button>
  );
};

export default VerificationStock;

