import React from 'react';

interface ButtonCountCartProps {
  quantity: number;
  onDecrease: () => void;
  onIncrease: () => void;
  allowZero?: boolean;
}

const ButtonCountCart: React.FC<ButtonCountCartProps> = ({ quantity, onDecrease, onIncrease, allowZero = false }) => {
  return (
    <div className="flex items-center gap-2">
      <button
        className="px-4 py-2 bg-gray-200 rounded-md shadow-sm hover:bg-gray-300 transition disabled:opacity-50"
        onClick={onDecrease}
        disabled={!allowZero && quantity <= 1}
      >
        -
      </button>
      <span className="text-lg min-w-[2rem] text-center">{quantity}</span>
      <button
        className="px-4 py-2 bg-gray-200 rounded-md shadow-sm hover:bg-gray-300 transition"
        onClick={onIncrease}
      >
        +
      </button>
    </div>
  );
};

export default ButtonCountCart;
