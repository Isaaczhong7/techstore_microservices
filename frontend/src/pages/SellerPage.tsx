import type { FormEvent } from "react";
import { useMemo, useState } from "react";
import { Boxes, PackagePlus, RefreshCw, XCircle } from "lucide-react";
import { CatalogHeader } from "../components/CatalogHeader";
import { Pagination } from "../components/Pagination";
import { StatusText } from "../components/Status";
import { categories, currency, PAGE_SIZE } from "../lib/constants";
import { formatEnum } from "../lib/format";
import type { Category, InventoryItem, OrderEntry, Product, ProductForm, StateSetter } from "../types";

type Props = {
  filteredCatalog: Product[];
  inventoryAdjustments: Record<string, string>;
  inventoryByProduct: Map<string, InventoryItem>;
  isLoading: boolean;
  orders: OrderEntry[];
  productForm: ProductForm;
  query: string;
  selectedCategory: "ALL" | Category;
  onAddInventory: (productId: string, quantity: number) => Promise<void>;
  onCancelOrder: (orderId?: string) => Promise<void>;
  onCategoryChange: (value: "ALL" | Category) => void;
  onCreateProduct: (event: FormEvent) => Promise<void>;
  onProductFormChange: StateSetter<ProductForm>;
  onQueryChange: (value: string) => void;
  onRefresh: () => Promise<void>;
  onSetInventoryAdjustments: StateSetter<Record<string, string>>;
};

export function SellerPage(props: Props) {
  const [productPage, setProductPage] = useState(0);
  const [orderPage, setOrderPage] = useState(0);
  const visibleProducts = useMemo(
    () => props.filteredCatalog.slice(productPage * PAGE_SIZE, productPage * PAGE_SIZE + PAGE_SIZE),
    [productPage, props.filteredCatalog]
  );
  const visibleOrders = useMemo(
    () => props.orders.slice(orderPage * PAGE_SIZE, orderPage * PAGE_SIZE + PAGE_SIZE),
    [orderPage, props.orders]
  );

  return (
    <section className="seller-page">
      <form onSubmit={props.onCreateProduct} className="admin-form">
        <h2>Create product + stock</h2>
        <input value={props.productForm.productName} onChange={(event) => props.onProductFormChange({ ...props.productForm, productName: event.target.value })} placeholder="Product name" required />
        <input value={props.productForm.description} onChange={(event) => props.onProductFormChange({ ...props.productForm, description: event.target.value })} placeholder="Description" required />
        <select value={props.productForm.category} onChange={(event) => props.onProductFormChange({ ...props.productForm, category: event.target.value as Category })}>
          {categories.map((category) => (
            <option value={category} key={category}>
              {formatEnum(category)}
            </option>
          ))}
        </select>
        <input type="number" min="0" step="0.01" value={props.productForm.price} onChange={(event) => props.onProductFormChange({ ...props.productForm, price: event.target.value })} placeholder="Price" required />
        <input type="number" min="0" value={props.productForm.initialStock} onChange={(event) => props.onProductFormChange({ ...props.productForm, initialStock: event.target.value })} placeholder="Initial stock" required />
        <label className="toggle-line">
          <input type="checkbox" checked={props.productForm.active} onChange={(event) => props.onProductFormChange({ ...props.productForm, active: event.target.checked })} />
          Active
        </label>
        <button className="primary-button" type="submit">
          <PackagePlus size={17} />
          Save product
        </button>
      </form>

      <section className="catalog-panel">
        <CatalogHeader
          query={props.query}
          selectedCategory={props.selectedCategory}
          onQueryChange={(value) => {
            setProductPage(0);
            props.onQueryChange(value);
          }}
          onCategoryChange={(value) => {
            setProductPage(0);
            props.onCategoryChange(value);
          }}
        />
        <div className="seller-table">
          <div className="seller-row seller-head">
            <span>Product</span>
            <span>Category</span>
            <span>Stock</span>
            <span>Sold</span>
            <span>Add quantity</span>
          </div>
          {visibleProducts.map((product) => {
            const stock = props.inventoryByProduct.get(product.productId);
            const quantity = product.quantity ?? stock?.quantity ?? 0;
            const sold = product.itemSold ?? stock?.itemSold ?? 0;
            const adjustment = props.inventoryAdjustments[product.productId] ?? "";
            return (
              <div className="seller-row" key={product.productId}>
                <span>
                  <strong>{product.productName}</strong>
                  <small>{product.productId}</small>
                </span>
                <span>{formatEnum(product.category)}</span>
                <span>{quantity}</span>
                <span>{sold}</span>
                <span className="inventory-editor">
                  <input
                    type="number"
                    min="1"
                    value={adjustment}
                    onChange={(event) => props.onSetInventoryAdjustments((current) => ({ ...current, [product.productId]: event.target.value }))}
                    placeholder="Qty"
                  />
                  <button className="primary-button" onClick={() => void props.onAddInventory(product.productId, Number(adjustment))}>
                    <Boxes size={16} />
                    Update
                  </button>
                </span>
              </div>
            );
          })}
          {!visibleProducts.length && <div className="empty-state">No products available</div>}
        </div>
        <Pagination page={productPage} pageSize={PAGE_SIZE} totalItems={props.filteredCatalog.length} onPageChange={setProductPage} />
      </section>

      <section className="catalog-panel">
        <div className="panel-header">
          <h2>Order entries</h2>
          <button className="icon-button text-button" onClick={props.onRefresh} disabled={props.isLoading}>
            <RefreshCw size={17} className={props.isLoading ? "spin" : ""} />
            Refresh
          </button>
        </div>
        <div className="order-list">
          {visibleOrders.map((order) => (
            <article className="order-entry" key={order.id}>
              <div>
                <strong>{order.email}</strong>
                <span>{order.id}</span>
              </div>
              <div>
                <StatusText value={order.status} />
                <span>{order.paymentStatus || "Payment pending"}</span>
              </div>
              <div>
                <span>{currency.format(Number(order.totalAmount || 0))}</span>
                <button className="danger-button" onClick={() => void props.onCancelOrder(order.id)} disabled={order.status === "CANCELLED" || order.status === "CONFIRMED"}>
                  <XCircle size={16} />
                  Cancel
                </button>
              </div>
            </article>
          ))}
          {!visibleOrders.length && <div className="empty-state">No order entries yet</div>}
        </div>
        <Pagination page={orderPage} pageSize={PAGE_SIZE} totalItems={props.orders.length} onPageChange={setOrderPage} />
      </section>
    </section>
  );
}
