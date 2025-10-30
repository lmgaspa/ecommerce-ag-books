import { Link } from 'react-router-dom';
import { FaShoppingCart } from 'react-icons/fa';

const Navbar = () => {
  return (
    <nav className="bg-primary text-background py-4 px-4 sm:px-8 flex flex-col sm:flex-row justify-between items-center shadow-md">
      <Link
        to="/"
        className="text-2xl sm:text-3xl font-bold text-background mb-2 sm:mb-0 text-center sm:text-left"
      >
        Agenor Gasparetto
      </Link>
      <div className="flex gap-6 sm:gap-8 items-center">
        <Link
          to="/"
          className="text-base sm:text-lg font-semibold text-background hover:text-secondary transition"
        >
          Home
        </Link>
        <Link
          to="/books"
          className="text-base sm:text-lg font-semibold text-background hover:text-secondary transition"
        >
          Livros
        </Link>
        <Link
          to="/support"
          className="text-base sm:text-lg font-semibold text-background hover:text-secondary transition"
        >
          Ajuda
        </Link>
        <Link
          to="/cart"
          className="text-base sm:text-lg font-semibold text-background hover:text-secondary transition"
        >
          <FaShoppingCart className="text-xl sm:text-2xl" />
        </Link>
      </div>
    </nav>
  );
};

export default Navbar;
