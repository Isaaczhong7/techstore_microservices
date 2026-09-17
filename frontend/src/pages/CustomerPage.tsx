import { useMemo, useState } from "react";
import { ShoppingCart, Trash2 } from "lucide-react";
import { CatalogHeader } from "../components/CatalogHeader";
import { Pagination } from "../components/Pagination";
import { Status } from "../components/Status";
import { currency, PAGE_SIZE } from "../lib/constants";
import { formatEnum } from "../lib/format";
import type { CartLine, Category, InventoryItem, Product } from "../types";

type Props = {
  cartLines: CartLine[];
  cartTotal: number;
  filteredCatalog: Product[];
  inventoryByProduct: Map<string, InventoryItem>;
  query: string;
  selectedCategory: "ALL" | Category;
  onAddToCart: (productId: string) => void;
  onCategoryChange: (value: "ALL" | Category) => void;
  onCheckout: () => void;
  onQueryChange: (value: string) => void;
  onRemoveFromCart: (productId: string) => void;
  onSetCartQuantity: (productId: string, quantity: number) => void;
};

export function CustomerPage(props: Props) {
  const [catalogPage, setCatalogPage] = useState(0);
  const visibleProducts = useMemo(
    () => props.filteredCatalog.slice(catalogPage * PAGE_SIZE, catalogPage * PAGE_SIZE + PAGE_SIZE),
    [catalogPage, props.filteredCatalog]
  );

  return (
    <section className="workspace">
      <div className="catalog-panel">
        <CatalogHeader
          query={props.query}
          selectedCategory={props.selectedCategory}
          onQueryChange={(value) => {
            setCatalogPage(0);
            props.onQueryChange(value);
          }}
          onCategoryChange={(value) => {
            setCatalogPage(0);
            props.onCategoryChange(value);
          }}
        />

        <div className="product-grid">
          {visibleProducts.map((product) => {
            const stock = props.inventoryByProduct.get(product.productId);
            const quantity = product.quantity ?? stock?.quantity ?? 0;
            const sold = product.itemSold ?? stock?.itemSold ?? 0;
            return (
              <article className="product-card" key={product.productId}>
                <div className="product-topline">
                  <span>{formatEnum(product.category)}</span>
                  <Status active={Boolean(product.active)} />
                </div>
                <h3>{product.productName}</h3>
                <p>{product.description || "No description"}</p>
                <div className="product-stats">
                  <strong>{currency.format(Number(product.price || 0))}</strong>
                  <span>{quantity} in stock</span>
                  <span>{sold} sold</span>
                </div>
                <button className="primary-button" onClick={() => props.onAddToCart(product.productId)} disabled={!product.active || quantity <= 0}>
                  <ShoppingCart size={17} />
                  Add to cart
                </button>
              </article>
            );
          })}
          {!visibleProducts.length && <div className="empty-state">No products available</div>}
        </div>
        <Pagination page={catalogPage} pageSize={PAGE_SIZE} totalItems={props.filteredCatalog.length} onPageChange={setCatalogPage} />
      </div>

      <aside className="side-panel">
        <section className="tool-panel">
          <h2>Cart</h2>
          <div className="cart-lines">
            {props.cartLines.map((line) => (
              <div className="cart-line" key={line.product.productId}>
                <span>{line.product.productName}</span>
                <input
                  type="number"
                  min="0"
                  value={line.quantity}
                  onChange={(event) => props.onSetCartQuantity(line.product.productId, Number(event.target.value))}
                  aria-label={`${line.product.productName} quantity`}
                />
                <button className="icon-button square-button" title="Remove item" onClick={() => props.onRemoveFromCart(line.product.productId)}>
                  <Trash2 size={16} />
                </button>
              </div>
            ))}
            {!props.cartLines.length && <div className="muted">Cart is empty</div>}
          </div>
          <div className="total-line">
            <span>Total</span>
            <strong>{currency.format(props.cartTotal)}</strong>
          </div>
          <button className="primary-button" onClick={props.onCheckout} disabled={!props.cartLines.length}>
            <ShoppingCart size={17} />
            Checkout
          </button>
        </section>
      </aside>
    </section>
  );
}
