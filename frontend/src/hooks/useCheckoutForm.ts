import { useState } from 'react';
import { formatCep, formatCpf, formatCelular } from '../utils/masks';

export const useCheckoutForm = () => {
  const [form, setForm] = useState({
    nome: '',
    sobrenome: '',
    cpf: '',
    pais: 'Brasil',
    cep: '',
    endereco: '',
    numero: '',
    complemento: '',
    bairro: '',
    cidade: '',
    estado: '',
    celular: '',
    email: '',
    observacao: '',
    entrega: '',
    pagamento: 'pix',
  });

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name } = e.target;
    let { value } = e.target;

    if (name === 'cep') value = formatCep(value);
    if (name === 'cpf') value = formatCpf(value);
    if (name === 'celular') value = formatCelular(value);

    setForm((prev) => ({ ...prev, [name]: value }));

    if (name === 'cep' && value.replace(/\D/g, '').length === 8) {
      fetch(`https://viacep.com.br/ws/${value.replace(/\D/g, '')}/json/`)
        .then((res) => res.json())
        .then((data) => {
          setForm((prev) => ({
            ...prev,
            endereco: data.logradouro || '',
            bairro: data.bairro || '',
            cidade: data.localidade || '',
            estado: data.uf || '',
          }));
        });
    }
  };

  return { form, handleChange, setForm };
};
