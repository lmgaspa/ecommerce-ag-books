export const formatCpf = (value: string): string =>
  value
    .replace(/\D/g, '')
    .slice(0, 11)
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d{1,2})$/, '$1-$2');

export const formatCelular = (value: string): string => {
  const digits = value.replace(/\D/g, '').slice(0, 11);

  if (digits.length <= 2) {
    return `(${digits}`;
  }

  if (digits.length <= 7) {
    return `(${digits.slice(0, 2)})${digits.slice(2)}`;
  }

  if (digits.length <= 11) {
    return `(${digits.slice(0, 2)})${digits.slice(2, 7)}-${digits.slice(7)}`;
  }

  return `(${digits.slice(0, 2)})${digits.slice(2, 7)}-${digits.slice(7, 11)}`;
};

export const formatCep = (value: string): string =>
  value
    .replace(/\D/g, '')
    .slice(0, 8)
    .replace(/(\d{5})(\d{1,3})$/, '$1-$2');