import type { ReactNode } from 'react';

interface ButtonProps {
  children: ReactNode;
  variant?: 'primary' | 'secondary' | 'accent' | 'error' | 'success';
  onClick?: () => void;
}

function Button({ children, variant = 'primary', onClick }: ButtonProps) {
  const colorClasses = {
    primary: 'bg-primary text-background hover:bg-secondary',
    secondary: 'bg-secondary text-background hover:bg-accent',
    accent: 'bg-accent text-background hover:bg-secondary',
    error: 'bg-error text-background hover:bg-primary',
    success: 'bg-success text-background hover:bg-primary',
  };

  return (
    <button
      className={`px-6 py-3 rounded-md font-bold transition ${colorClasses[variant]}`}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

export default Button;
