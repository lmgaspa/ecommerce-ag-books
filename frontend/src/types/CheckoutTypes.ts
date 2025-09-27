export interface CheckoutFormData {
  firstName: string;
  lastName: string;
  cpf: string;
  country: string;
  cep: string;
  address: string;
  number: string;
  complement: string;
  district: string;
  city: string;
  state: string;
  phone: string;
  email: string;
  note: string;
  delivery: string;            // "normal" | "express" | ""
  payment: "pix" | "card";     // só a escolha aqui
  shipping?: number;           // para o CardPaymentPage ler do localStorage
}