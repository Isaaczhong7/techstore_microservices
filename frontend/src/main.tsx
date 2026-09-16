import React, { FormEvent, useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Boxes,
  CheckCircle2,
  ClipboardList,
  CreditCard,
  PackagePlus,
  RefreshCw,
  Search,
  ShoppingCart,
  SlidersHorizontal,
  Store,
  XCircle
} from "lucide-react";
import "./styles.css";

type Category =
  | "LAPTOP"
  | "DESKTOP"
  | "SMARTPHONE"
  | "TABLET"
  | "MONITOR"
  | "KEYBOARD"
  | "MOUSE"
  | "HEADPHONES"
  | "SPEAKER"
  | "CAMERA"
  | "SMARTWATCH"
  | "STORAGE"
  | "MEMORY"
  | "PROCESSOR"
  | "GRAPHICS_CARD"
  | "MOTHERBOARD"
  | "POWER_SUPPLY"
  | "NETWORKING"
  | "ACCESSORY"
  | "OTHER";

type Product = {
  productId: number;
  productName: string;
  description: string;
  category: Category;
  price: number;
  active: boolean;
  version?: number;
  quantity?: number;
  itemSold?: number;
};

type InventoryItem = {
  productId: number;
  quantity: number;
  itemSold: number;
  version: number;
};

type OrderStatus = "PENDING" | "PARTIAL" | "CONFIRMED" | "CANCELLED" | "EXPIRED" | "CONFIRMING" | "CANCELLING";

type OrderResult = {
  orderId: string;
  status: OrderStatus;
};

type Toast = {
  tone: "success" | "error" | "info";
  text: string;
};

const categories: Category[] = [
  "LAPTOP",
  "DESKTOP",
  "SMARTPHONE",
  "TABLET",
  "MONITOR",
  "KEYBOARD",
  "MOUSE",
  "HEADPHONES",
  "SPEAKER",
  "CAMERA",
  "SMARTWATCH",
  "STORAGE",
  "MEMORY",
  "PROCESSOR",
  "GRAPHICS_CARD",
  "MOTHERBOARD",
  "POWER_SUPPLY",
  "NETWORKING",
  "ACCESSORY",
  "OTHER"
];

const currency = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD"
});

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...options?.headers
    },
    ...options
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `${response.status} ${response.statusText}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

function App() {
  const [catalog, setCatalog] = useState<Product[]>([]);
  const [inventory, setInventory] = useState<InventoryItem[]>([]);
  const [cart, setCart] = useState<Record<number, number>>({});
  const [query, setQuery] = useState("");
  const [selectedCategory, setSelectedCategory] = useState<"ALL" | Category>("ALL");
  const [lastOrder, setLastOrder] = useState<OrderResult | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [toast, setToast] = useState<Toast | null>(null);
  const [productForm, setProductForm] = useState({
    productName: "",
    description: "",
    category: "LAPTOP" as Category,
    price: "999.00",
    active: true
  });
  const [inventoryForm, setInventoryForm] = useState({
    productId: "",
    quantity: "10"
  });
  const [orderForm, setOrderForm] = useState({
    email: "guest@techstore.local",
    customerType: "GUEST" as "GUEST" | "MEMBER",
    customerId: ""
  });
  const [paymentForm, setPaymentForm] = useState({
    orderId: "",
    paymentId: "",
    paymentMethodId: "",
    currencyType: "USD"
  });

  const inventoryByProduct = useMemo(
    () => new Map(inventory.map((item) => [item.productId, item])),
    [inventory]
  );

  const filteredCatalog = useMemo(() => {
    return catalog.filter((product) => {
      const text = `${product.productName} ${product.description} ${product.category}`.toLowerCase();
      const matchesText = text.includes(query.toLowerCase());
      const matchesCategory = selectedCategory === "ALL" || product.category === selectedCategory;
      return matchesText && matchesCategory;
    });
  }, [catalog, query, selectedCategory]);

  const cartLines = useMemo(() => {
    return Object.entries(cart)
      .map(([productId, quantity]) => {
        const product = catalog.find((item) => item.productId === Number(productId));
        return product ? { product, quantity } : null;
      })
      .filter((line): line is { product: Product; quantity: number } => Boolean(line));
  }, [cart, catalog]);

  const cartTotal = cartLines.reduce((sum, line) => sum + Number(line.product.price) * line.quantity, 0);

  useEffect(() => {
    void refreshAll();
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 4200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  async function refreshAll() {
    setIsLoading(true);
    try {
      const [products, inventoryRows, lookup] = await Promise.allSettled([
        request<Product[]>("/api/products"),
        request<InventoryItem[]>("/api/inventory"),
        request<{ content?: Product[] }>("/api/lookup?page=0&size=100")
      ]);

      if (lookup.status === "fulfilled" && lookup.value.content?.length) {
        setCatalog(lookup.value.content);
      } else if (products.status === "fulfilled") {
        setCatalog(products.value);
      }

      if (inventoryRows.status === "fulfilled") {
        setInventory(inventoryRows.value);
      }

      setToast({ tone: "success", text: "Store data refreshed" });
    } catch (error) {
      setToast({ tone: "error", text: messageFrom(error) });
    } finally {
      setIsLoading(false);
    }
  }

  async function createProduct(event: FormEvent) {
    event.preventDefault();
    await runAction("Product created", async () => {
      await request<void>("/api/products", {
        method: "POST",
        body: JSON.stringify({
          items: [
            {
              productName: productForm.productName,
              description: productForm.description,
              category: productForm.category,
              price: Number(productForm.price),
              active: productForm.active
            }
          ]
        })
      });
      setProductForm((form) => ({ ...form, productName: "", description: "" }));
      await refreshAll();
    });
  }

  async function createInventory(event: FormEvent) {
    event.preventDefault();
    await runAction("Inventory seeded", async () => {
      await request<void>("/api/inventory", {
        method: "POST",
        body: JSON.stringify([
          {
            productId: Number(inventoryForm.productId),
            quantity: Number(inventoryForm.quantity)
          }
        ])
      });
      await refreshAll();
    });
  }

  async function addInventory(productId: number, quantity: number) {
    await runAction("Inventory updated", async () => {
      await request<void>(`/api/inventory/${productId}/update`, {
        method: "PATCH",
        body: JSON.stringify({ quantity })
      });
      await refreshAll();
    });
  }

  async function createOrder(event: FormEvent) {
    event.preventDefault();
    if (!cartLines.length) {
      setToast({ tone: "error", text: "Cart is empty" });
      return;
    }

    await runAction("Order created", async () => {
      const result = await request<OrderResult>("/api/orders/create", {
        method: "POST",
        body: JSON.stringify({
          customerType: orderForm.customerType,
          customerId: orderForm.customerType === "MEMBER" && orderForm.customerId ? orderForm.customerId : null,
          email: orderForm.email,
          items: cartLines.map((line) => ({
            productId: line.product.productId,
            quantity: line.quantity
          }))
        })
      });
      setLastOrder(result);
      setPaymentForm((form) => ({ ...form, orderId: result.orderId }));
    });
  }

  async function refreshOrder(orderId = lastOrder?.orderId || paymentForm.orderId) {
    if (!orderId) return;
    await runAction("Order status refreshed", async () => {
      const result = await request<OrderResult>(`/api/orders/${orderId}`);
      setLastOrder(result);
    });
  }

  async function cancelOrder() {
    const orderId = lastOrder?.orderId || paymentForm.orderId;
    if (!orderId) return;
    await runAction("Cancellation requested", async () => {
      const result = await request<OrderResult>("/api/orders/cancel", {
        method: "POST",
        body: JSON.stringify({ orderId })
      });
      setLastOrder(result);
    });
  }

  async function submitPayment(event: FormEvent) {
    event.preventDefault();
    await runAction("Payment submitted", async () => {
      const result = await request<OrderResult>("/api/orders/payment", {
        method: "POST",
        body: JSON.stringify({
          orderId: paymentForm.orderId,
          paymentId: paymentForm.paymentId,
          paymentMethodId: paymentForm.paymentMethodId,
          currencyType: paymentForm.currencyType
        })
      });
      setLastOrder(result);
    });
  }

  async function runAction(success: string, action: () => Promise<void>) {
    setIsLoading(true);
    try {
      await action();
      setToast({ tone: "success", text: success });
    } catch (error) {
      setToast({ tone: "error", text: messageFrom(error) });
    } finally {
      setIsLoading(false);
    }
  }

  function addToCart(productId: number) {
    setCart((current) => ({ ...current, [productId]: (current[productId] || 0) + 1 }));
  }

  function setCartQuantity(productId: number, quantity: number) {
    setCart((current) => {
      const next = { ...current };
      if (quantity <= 0) {
        delete next[productId];
      } else {
        next[productId] = quantity;
      }
      return next;
    });
  }

  return (
    <main>
      <header className="app-header">
        <div>
          <div className="eyebrow">
            <Store size={16} />
            TechStore Console
          </div>
          <h1>Commerce operations</h1>
        </div>
        <button className="icon-button text-button" onClick={refreshAll} disabled={isLoading} title="Refresh store data">
          <RefreshCw size={18} className={isLoading ? "spin" : ""} />
          Refresh
        </button>
      </header>

      <section className="status-strip">
        <Metric label="Products" value={catalog.length} icon={<PackagePlus size={18} />} />
        <Metric label="Inventory rows" value={inventory.length} icon={<Boxes size={18} />} />
        <Metric label="Cart lines" value={cartLines.length} icon={<ShoppingCart size={18} />} />
        <Metric label="Order status" value={lastOrder?.status || "None"} icon={<ClipboardList size={18} />} />
      </section>

      <section className="workspace">
        <div className="catalog-panel">
          <div className="panel-header">
            <h2>Catalog</h2>
            <div className="filters">
              <label className="search">
                <Search size={16} />
                <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search" />
              </label>
              <label className="select-wrap">
                <SlidersHorizontal size={16} />
                <select value={selectedCategory} onChange={(event) => setSelectedCategory(event.target.value as "ALL" | Category)}>
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

          <div className="product-grid">
            {filteredCatalog.map((product) => {
              const stock = inventoryByProduct.get(product.productId);
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
                  <div className="card-actions">
                    <button className="icon-button" title="Add one to inventory" onClick={() => void addInventory(product.productId, 1)}>
                      <Boxes size={17} />
                    </button>
                    <button className="primary-button" onClick={() => addToCart(product.productId)} disabled={!product.active}>
                      <ShoppingCart size={17} />
                      Add
                    </button>
                  </div>
                </article>
              );
            })}
            {!filteredCatalog.length && <div className="empty-state">No products available</div>}
          </div>
        </div>

        <aside className="side-panel">
          <section className="tool-panel">
            <h2>Cart</h2>
            <div className="cart-lines">
              {cartLines.map((line) => (
                <div className="cart-line" key={line.product.productId}>
                  <span>{line.product.productName}</span>
                  <input
                    type="number"
                    min="0"
                    value={line.quantity}
                    onChange={(event) => setCartQuantity(line.product.productId, Number(event.target.value))}
                    aria-label={`${line.product.productName} quantity`}
                  />
                </div>
              ))}
              {!cartLines.length && <div className="muted">Cart is empty</div>}
            </div>
            <div className="total-line">
              <span>Total</span>
              <strong>{currency.format(cartTotal)}</strong>
            </div>
            <form onSubmit={createOrder} className="form-stack">
              <input value={orderForm.email} onChange={(event) => setOrderForm({ ...orderForm, email: event.target.value })} placeholder="Email" required />
              <select value={orderForm.customerType} onChange={(event) => setOrderForm({ ...orderForm, customerType: event.target.value as "GUEST" | "MEMBER" })}>
                <option value="GUEST">Guest</option>
                <option value="MEMBER">Member</option>
              </select>
              {orderForm.customerType === "MEMBER" && (
                <input value={orderForm.customerId} onChange={(event) => setOrderForm({ ...orderForm, customerId: event.target.value })} placeholder="Customer UUID" />
              )}
              <button className="primary-button" type="submit">
                <ClipboardList size={17} />
                Create order
              </button>
            </form>
          </section>

          <section className="tool-panel">
            <h2>Order</h2>
            <div className="order-box">
              <span>{lastOrder?.orderId || "No order selected"}</span>
              <strong>{lastOrder?.status || "None"}</strong>
            </div>
            <div className="split-actions">
              <button className="icon-button text-button" onClick={() => void refreshOrder()} disabled={!lastOrder && !paymentForm.orderId}>
                <RefreshCw size={17} />
                Poll
              </button>
              <button className="danger-button" onClick={() => void cancelOrder()} disabled={!lastOrder && !paymentForm.orderId}>
                <XCircle size={17} />
                Cancel
              </button>
            </div>
          </section>

          <section className="tool-panel">
            <h2>Payment</h2>
            <form onSubmit={submitPayment} className="form-stack">
              <input value={paymentForm.orderId} onChange={(event) => setPaymentForm({ ...paymentForm, orderId: event.target.value })} placeholder="Order UUID" required />
              <input value={paymentForm.paymentId} onChange={(event) => setPaymentForm({ ...paymentForm, paymentId: event.target.value })} placeholder="Payment UUID" required />
              <input value={paymentForm.paymentMethodId} onChange={(event) => setPaymentForm({ ...paymentForm, paymentMethodId: event.target.value })} placeholder="Payment method UUID" required />
              <select value={paymentForm.currencyType} onChange={(event) => setPaymentForm({ ...paymentForm, currencyType: event.target.value })}>
                <option value="USD">USD</option>
                <option value="EUR">EUR</option>
                <option value="GBP">GBP</option>
                <option value="JPY">JPY</option>
                <option value="CNY">CNY</option>
              </select>
              <button className="primary-button" type="submit">
                <CreditCard size={17} />
                Submit payment
              </button>
            </form>
          </section>
        </aside>
      </section>

      <section className="admin-band">
        <form onSubmit={createProduct} className="admin-form">
          <h2>Create product</h2>
          <input value={productForm.productName} onChange={(event) => setProductForm({ ...productForm, productName: event.target.value })} placeholder="Product name" required />
          <input value={productForm.description} onChange={(event) => setProductForm({ ...productForm, description: event.target.value })} placeholder="Description" required />
          <select value={productForm.category} onChange={(event) => setProductForm({ ...productForm, category: event.target.value as Category })}>
            {categories.map((category) => (
              <option value={category} key={category}>
                {formatEnum(category)}
              </option>
            ))}
          </select>
          <input type="number" min="0" step="0.01" value={productForm.price} onChange={(event) => setProductForm({ ...productForm, price: event.target.value })} placeholder="Price" required />
          <label className="toggle-line">
            <input type="checkbox" checked={productForm.active} onChange={(event) => setProductForm({ ...productForm, active: event.target.checked })} />
            Active
          </label>
          <button className="primary-button" type="submit">
            <PackagePlus size={17} />
            Save product
          </button>
        </form>

        <form onSubmit={createInventory} className="admin-form">
          <h2>Seed inventory</h2>
          <input value={inventoryForm.productId} onChange={(event) => setInventoryForm({ ...inventoryForm, productId: event.target.value })} placeholder="Product id" required />
          <input type="number" min="0" value={inventoryForm.quantity} onChange={(event) => setInventoryForm({ ...inventoryForm, quantity: event.target.value })} placeholder="Quantity" required />
          <button className="primary-button" type="submit">
            <Boxes size={17} />
            Save inventory
          </button>
        </form>
      </section>

      {toast && (
        <div className={`toast ${toast.tone}`}>
          {toast.tone === "error" ? <XCircle size={18} /> : <CheckCircle2 size={18} />}
          {toast.text}
        </div>
      )}
    </main>
  );
}

function Metric({ label, value, icon }: { label: string; value: string | number; icon: React.ReactNode }) {
  return (
    <div className="metric">
      {icon}
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function Status({ active }: { active: boolean }) {
  return <span className={active ? "status active" : "status inactive"}>{active ? "Active" : "Inactive"}</span>;
}

function formatEnum(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((word) => word[0].toUpperCase() + word.slice(1))
    .join(" ");
}

function messageFrom(error: unknown) {
  if (error instanceof Error) {
    return error.message.replaceAll('"', "");
  }
  return "Request failed";
}

createRoot(document.getElementById("root")!).render(<App />);
