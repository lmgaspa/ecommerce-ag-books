import type { Book } from '../data/books';

export function validateRelated(books: Book[]) {
  const index = Object.fromEntries(books.map(b => [b.id, b]));
  const errors: string[] = [];
  const warnings: string[] = [];

  for (const b of books) {
    const seen = new Set<string>();
    for (const r of b.relatedBooks ?? []) {
      if (r.id === b.id) errors.push(`"${b.id}" referencia a si mesmo.`);
      if (!index[r.id]) errors.push(`"${b.id}" referencia inexistente "${r.id}".`);
      if (seen.has(r.id)) errors.push(`"${b.id}" tem related duplicado "${r.id}".`);
      seen.add(r.id);

      const base = index[r.id];
      if (base) {
        if (base.price !== r.price) warnings.push(`Preço divergente: related "${b.id}" → "${r.id}" (${r.price}) ≠ catálogo (${base.price}).`);
        if (base.title !== r.title) warnings.push(`Título divergente: related "${b.id}" → "${r.id}" (${r.title}) ≠ catálogo (${base.title}).`);
        if (base.imageUrl !== r.imageUrl) warnings.push(`Imagem divergente: related "${b.id}" → "${r.id}".`);
      }
    }
  }

  return { errors, warnings };
}

//