interface AuthorInfoProps {
  author: string;
}

const AuthorInfo: React.FC<AuthorInfoProps> = ({ author }) => {
  return (
    <div className="bg-background rounded-lg shadow-lg p-8 mb-0">
      <div className="flex flex-col md:flex-row items-center gap-8">
        <img src="/images/agenor.webp" alt={author} className="w-32 h-32 rounded-full shadow-md" />
        <div>
          <h2 className="text-2xl font-bold text-primary mb-4">{author}</h2>
          <p className="text-lg text-text-secondary leading-relaxed">
            Possui graduação em Ciências Sociais pela Pontifícia Universidade Católica do Rio Grande do Sul (1982) e mestrado em Sociologia Rural pela Universidade Federal do Rio Grande do Sul (1985). Atualmente é Professor Adjunto da Universidade Estadual de Santa Cruz, Empresário do Sócio Estatística Pesquisa Consultoria Ltda e Sócio Diretor da Via Litterarum Editora. Atua principalmente nos seguintes temas: mobilidade social, lavoura cacaueira, Bahia.
          </p>
        </div>
      </div>
    </div>
  );
};

export default AuthorInfo;
