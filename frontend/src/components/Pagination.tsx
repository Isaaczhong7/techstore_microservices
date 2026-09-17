type Props = {
  page: number;
  pageSize: number;
  totalItems: number;
  onPageChange: (page: number) => void;
};

export function Pagination({ page, pageSize, totalItems, onPageChange }: Props) {
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const start = totalItems === 0 ? 0 : page * pageSize + 1;
  const end = Math.min(totalItems, (page + 1) * pageSize);

  return (
    <div className="pagination">
      <span>
        {start}-{end} of {totalItems}
      </span>
      <div>
        <button className="icon-button" onClick={() => onPageChange(Math.max(0, page - 1))} disabled={page === 0}>
          Prev
        </button>
        <button className="icon-button" onClick={() => onPageChange(Math.min(totalPages - 1, page + 1))} disabled={page >= totalPages - 1}>
          Next
        </button>
      </div>
    </div>
  );
}
