import { ChevronLeft, ChevronRight } from "lucide-react";
export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <nav className="pagination" aria-label="Pagination">
      <span>
        Page {page + 1} of {totalPages}
      </span>
      <button
        className="icon-button"
        aria-label="Previous page"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
      >
        <ChevronLeft size={18} />
      </button>
      <button
        className="icon-button"
        aria-label="Next page"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        <ChevronRight size={18} />
      </button>
    </nav>
  );
}
