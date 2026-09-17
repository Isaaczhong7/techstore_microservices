import { Search, SlidersHorizontal } from "lucide-react";
import { categories } from "../lib/constants";
import { formatEnum } from "../lib/format";
import type { Category } from "../types";

type Props = {
  query: string;
  selectedCategory: "ALL" | Category;
  onQueryChange: (value: string) => void;
  onCategoryChange: (value: "ALL" | Category) => void;
};

export function CatalogHeader({ query, selectedCategory, onQueryChange, onCategoryChange }: Props) {
  return (
    <div className="panel-header">
      <h2>Catalog</h2>
      <div className="filters">
        <label className="search">
          <Search size={16} />
          <input value={query} onChange={(event) => onQueryChange(event.target.value)} placeholder="Search" />
        </label>
        <label className="select-wrap">
          <SlidersHorizontal size={16} />
          <select value={selectedCategory} onChange={(event) => onCategoryChange(event.target.value as "ALL" | Category)}>
            <option value="ALL">All categories</option>
            {categories.map((category) => (
              <option value={category} key={category}>
                {formatEnum(category)}
              </option>
            ))}
          </select>
        </label>
      </div>
    </div>
  );
}
