import type { ReactNode } from 'react';

interface ContainerProps {
  children: ReactNode;
}

function Container({ children }: ContainerProps) {
  return <div className="container mx-auto px-4">{children}</div>;
}

export default Container;
