interface LivroDimensoes {
  id: string;
  pesoRealKg: number;
  larguraCm: number;
  alturaCm: number;
  comprimentoCm: number;
}

const livrosDimensoes: Record<string, LivroDimensoes> = {
  extase: {
    id: "extase",
    pesoRealKg: 0.11,
    larguraCm: 13.5,
    alturaCm: 19.5,
    comprimentoCm: 1,
  },
  regressantes: {
    id: "regressantes",
    pesoRealKg: 0.2,
    larguraCm: 14,
    alturaCm: 21,
    comprimentoCm: 1,
  },
  sempre: {
    id: "sempre",
    pesoRealKg: 0.06,
    larguraCm: 20.5,
    alturaCm: 28,
    comprimentoCm: 1,
  },
};

function calcularPesoVolumetrico(livro: LivroDimensoes): number {
  const { larguraCm, alturaCm, comprimentoCm } = livro;
  return (larguraCm * alturaCm * comprimentoCm) / 6000;
}

export function calcularPesoConsiderado(id: string): number {
  const livro = livrosDimensoes[id];
  if (!livro) return 0.3; // valor padrão se não for encontrado

  const pesoVol = calcularPesoVolumetrico(livro);
  return Math.max(livro.pesoRealKg, pesoVol);
}
