import Container from "../components/common/Container";
import ContentBlock from "../components/common/ContentBlock";
import { useNavigate } from "react-router-dom";
import CookieConsent from "../components/CookieConsent";

function HomePage() {
  const navigate = useNavigate();

  return (
    <>
      <Container>
        <ContentBlock
          title="Agenor Gasparetto"
          imageUrl="/images/agenor.webp"
          description="Possui graduação em Ciências Sociais pela Pontifícia Universidade Católica do Rio Grande do Sul (1982) e mestrado em Sociologia Rural pela Universidade Federal do Rio Grande do Sul (1985). Atualmente é Professor Adjunto da Universidade Estadual de Santa Cruz, Empresário do Sócio Estatística Pesquisa Consultoria Ltda e Sócio Diretor da Via Litterarum Editora. Atuando principalmente nos seguintes temas: mobilidade social lavoura cacaueira Bahia."
          isAuthor
        />

        <h2 className="text-4xl font-extrabold text-primary mb-16 text-center">
          Livros
        </h2>

        <div className="flex flex-wrap justify-center gap-16">
          <div onClick={() => navigate("/books/extase")}>
            <ContentBlock
              title="Êxtase"
              imageUrl="/images/extase.webp"
              description="De birra com Jorge Amado e outras crônicas grapiúnas."
            />
          </div>
          <div onClick={() => navigate("/books/sempre")}>
            <ContentBlock
              title="Para Sempre Felizes"
              imageUrl="/images/sempre.webp"
              description="Coisas de neto."
            />
          </div>
          <div onClick={() => navigate("/books/regressantes")}>
            <ContentBlock
              title="Regressantes"
              imageUrl="/images/regressantes.webp"
              description="Histórias de luta e resistência."
            />
          </div>
          <div onClick={() => navigate("/books/versos")}>
            <ContentBlock
              title="Versos desnudos: poemas em tempos tensos"
              imageUrl="/images/versos.webp"
              description="Versos desnudos: poemas em tempos tensos."
            />
          </div>
          <div onClick={() => navigate("/books/versi")}>
            <ContentBlock
              title="Versi spogli: poesie in tempi difficili"
              imageUrl="/images/versi.webp"
              description="Versi spogli: poesie in tempi difficili."
            />
          </div>
        </div>
      </Container>

      {/* ✅ Banner de cookies (fixo no rodapé, visível logo ao entrar) */}
      <CookieConsent />
    </>
  );
}

export default HomePage;
