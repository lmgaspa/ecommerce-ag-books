import { descriptions } from './descriptions';

export interface Book {
  id: string;
  title: string;
  imageUrl: string;
  price: string;
  description: string;
  author: string;
  additionalInfo: Record<string, string>;
  category: string;
  relatedBooks: { title: string; imageUrl: string; price: string; category: string; id: string }[];
  stock: number;
}

const agenorGasparetto = "Agenor Gasparetto";
const selo = "Via Litterarum";
const idioma = "Português";

export const books: Book[] = [
  {
    id: "extase",
    title: "Êxtase, de birra com Jorge Amado e outras crônicas grapiúnas",
    imageUrl: "/images/extase.webp",
    price: "R$30,00",
    description: descriptions.extase,
    author: agenorGasparetto,
    additionalInfo: {
      Peso: "110 g",
      Dimensões: "13,5 × 19,5 × 1 cm",
      Selo: selo,
      ISBN: "978-65-86676-72-3",
      Edição: "1ª",
      "Ano de Publicação": "2023",
      "Nº de Páginas": "52",
      Idioma: idioma
    },
    category: "Crônicas",
    stock: 100,
    relatedBooks: [
      { id: "regressantes", title: "Regressantes", imageUrl: "/images/regressantes.webp", price: "R$20,00", category: "Contos" },
      { id: "sempre", title: "Para sempre felizes: coisas de neto", imageUrl: "/images/sempre.webp", price: "R$20,00", category: "Infantojuvenil" }
    ]
  },
  {
    id: "regressantes",
    title: "Regressantes",
    imageUrl: "/images/regressantes.webp",
    price: "R$30,00",
    description: descriptions.regressantes,
    author: agenorGasparetto,
    additionalInfo: {
      Peso: "200 g",
      Dimensões: "21 × 14 × 1 cm",
      Selo: selo,
      ISBN: "978-85-8151-019-4",
      Edição: "2ª",
      "Ano de Publicação": "2012",
      "Nº de Páginas": "142",
      Idioma: idioma
    },
    category: "Contos",
    stock: 100,
    relatedBooks: [
      { id: "extase", title: "Êxtase, de birra com Jorge Amado e outras crônicas grapiúnas", imageUrl: "/images/extase.webp", price: "R$29,90", category: "Crônicas" },
      { id: "sempre", title: "Para sempre felizes: coisas de neto", imageUrl: "/images/sempre.webp", price: "R$20,00", category: "Infantojuvenil" }
    ]
  },
  {
    id: "sempre",
    title: "Para sempre felizes: coisas de neto",
    imageUrl: "/images/sempre.webp",
    price: "R$20,00",
    description: descriptions.sempre,
    author: agenorGasparetto,
    additionalInfo: {
      Peso: "60 g",
      Dimensões: "28 × 20,5 × 1 cm",
      Selo: selo,
      ISBN: "978-85-8151-035-4",
      Edição: "1ª",
      "Ano de Publicação": "2013",
      "Nº de Páginas": "20",
      Idioma: idioma
    },
    category: "Infantojuvenil",
    stock: 100,
    relatedBooks: [
      { id: "extase", title: "Êxtase, de birra com Jorge Amado e outras crônicas grapiúnas", imageUrl: "/images/extase.webp", price: "R$29,90", category: "Crônicas" },
      { id: "regressantes", title: "Regressantes", imageUrl: "/images/regressantes.webp", price: "R$20,00", category: "Contos" }
    ]
  },
  {
    id: "versos",
    title: "Versos Desnudos, Poemas em tempo Tensos",
    imageUrl: "/images/versos.webp",
    price: "R$20,00",
    description: descriptions.versos,
    author: agenorGasparetto,
    additionalInfo: {
      Peso: "100 g",
      Dimensões: "11 × 21 × 1 cm",
      Selo: selo,
      ISBN: "978-85-8151-300-3",
      Edição: "1ª",
      "Ano de Publicação": "2025",
      "Nº de Páginas": "80",
      Idioma: idioma
    },
    category: "Poemas",
    stock: 100,
    relatedBooks: [
      { id: "extase", title: "Êxtase, de birra com Jorge Amado e outras crônicas grapiúnas", imageUrl: "/images/extase.webp", price: "R$29,90", category: "Crônicas" },
      { id: "regressantes", title: "Regressantes", imageUrl: "/images/regressantes.webp", price: "R$20,00", category: "Contos" }
    ],
  },
  {
    id: "versi",
    title: "Versi spogli: poesie in tempi difficili",
    imageUrl: "/images/versi.webp",
    price: "R$30,00",
    description: descriptions.versi,
    author: agenorGasparetto,
    additionalInfo: {
      Peso: "100 g",
      Dimensões: "11 × 21 × 1 cm",
      Selo: selo,
      ISBN: "978-85-8151-306-5",
      Edição: "1ª",
      "Ano de Publicação": "2025",
      "Nº de Páginas": "80",
      Idioma: idioma
    },
    category: "Poemas",
    stock: 100,
    relatedBooks: [
      { id: "extase", title: "Êxtase, de birra com Jorge Amado e outras crônicas grapiúnas", imageUrl: "/images/extase.webp", price: "R$29,90", category: "Crônicas" },
      { id: "regressantes", title: "Regressantes", imageUrl: "/images/regressantes.webp", price: "R$20,00", category: "Contos" }
    ],
  }
];
