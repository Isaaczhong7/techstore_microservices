import { FormEvent, useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import { Boxes, CheckCircle2, ClipboardList, CreditCard, PackagePlus, RefreshCw, ShoppingCart, Store, XCircle } from "lucide-react";
import { Metric } from "./components/Status";
import { request } from "./lib/api";
import { messageFrom } from "./lib/format";
import { CheckoutPage } from "./pages/CheckoutPage";
import { CustomerPage } from "./pages/CustomerPage";
import { SellerPage } from "./pages/SellerPage";
import type { Category, InventoryItem, OrderEntry, OrderForm, OrderResult, Page, PaymentForm, Product, ProductForm, Toast } from "./types";
import "./styles.css";

function App() {
  const [page, setPage] = useState<Page>("customer");
  const [catalog, setCatalog] = useState<Product[]>([]);
  const [inventory, setInventory] = useState<InventoryItem[]>([]);
  const [orders, setOrders] = useState<OrderEntry[]>([]);
  const [cart, setCart] = useState<Record<string, number>>({});
  const [inventoryAdjustments, setInventoryAdjustments] = useState<Record<string, string>>({});
  const [query, setQuery] = useState("");
  const [selectedCategory, setSelectedCategory] = useState<"ALL" | Category>("ALL");
  const [lastOrder, setLastOrder] = useState<OrderResult | null>(null);
  const [currentOrderId, setCurrentOrderId] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [toast, setToast] = useState<Toast | null>(null);
  const [productForm, setProductForm] = useState<ProductForm>({
    productName: "",
    description: "",
    category: "LAPTOP",
    price: "999.00",
    active: true,
    initialStock: "10"
  });
  const [orderForm, setOrderForm] = useState<OrderForm>({
    email: "guest@techstore.local",
    customerType: "GUEST",
    customerId: ""
  });
  const [paymentForm, setPaymentForm] = useState<PaymentForm>({
    orderId: "",
    paymentId: "",
    paymentMethodId: "",
    currencyType: "USD"
  });

  const safeInventory = Array.isArray(inventory) ? inventory : [];
  const safeCatalog = Array.isArray(catalog) ? catalog : [];
  const safeOrders = Array.isArray(orders) ? orders : [];

  const inventoryByProduct = useMemo(() => new Map(safeInventory.map((item) => [item.productId, item])), [safeInventory]);
  const productById = useMemo(() => new Map(safeCatalog.map((product) => [product.productId, product])), [safeCatalog]);

  const filteredCatalog = useMemo(() => {
    return safeCatalog.filter((product) => {
      const text = `${product.productName} ${product.description} ${product.category}`.toLowerCase();
      const matchesText = text.includes(query.toLowerCase());
      const matchesCategory = selectedCategory === "ALL" || product.category === selectedCategory;
      return matchesText && matchesCategory;
    });
  }, [safeCatalog, query, selectedCategory]);

  const cartLines = useMemo(() => {
    return Object.entries(cart)
      .map(([productId, quantity]) => {
        const product = productById.get(productId);
        return product ? { product, quantity } : null;
      })
      .filter((line): line is { product: Product; quantity: number } => Boolean(line));
  }, [cart, productById]);

  const cartTotal = cartLines.reduce((sum, line) => sum + Number(line.product.price) * line.quantity, 0);
  const currentOrder = safeOrders.find((order) => order.id === (lastOrder?.orderId || currentOrderId));

  useEffect(() => {
    void refreshAll();
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 4200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    if (page !== "checkout") return;

    const orderId = lastOrder?.orderId || currentOrderId || paymentForm.orderId;
    if (!orderId) return;

    void fetchOrderStatus(orderId);
    const timer = window.setInterval(() => {
      void fetchOrderStatus(orderId);
    }, 5000);

    return () => window.clearInterval(timer);
  }, [page, lastOrder?.orderId, currentOrderId, paymentForm.orderId]);

  async function refreshAll() {
    setIsLoading(true);
    try {
      const [products, inventoryRows, lookup, orderRows] = await Promise.allSettled([
        request<Product[]>("/api/products"),
        request<InventoryItem[]>("/api/inventory"),
        request<{ content?: Product[] }>("/api/lookup?page=0&size=100"),
        request<OrderEntry[]>("/api/orders")
      ]);

      if (lookup.status === "fulfilled" && Array.isArray(lookup.value?.content) && lookup.value.content.length) {
        setCatalog(lookup.value.content);
      } else if (products.status === "fulfilled") {
        setCatalog(Array.isArray(products.value) ? products.value : []);
      }

      if (inventoryRows.status === "fulfilled") setInventory(Array.isArray(inventoryRows.value) ? inventoryRows.value : []);
      if (orderRows.status === "fulfilled") setOrders(Array.isArray(orderRows.value) ? orderRows.value : []);
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
      const created = await request<Product[]>("/api/products", {
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

      const firstProduct = Array.isArray(created) ? created[0] : undefined;
      const initialStock = Number(productForm.initialStock);
      if (firstProduct?.productId && initialStock >= 0) {
        await request<void>("/api/inventory", {
          method: "POST",
          body: JSON.stringify([{ productId: firstProduct.productId, quantity: initialStock }])
        });
      }

      setProductForm((form) => ({ ...form, productName: "", description: "", initialStock: "10" }));
      await refreshAll();
    });
  }

  async function addInventory(productId: string, quantity: number) {
    if (!Number.isFinite(quantity) || quantity <= 0) {
      setToast({ tone: "error", text: "Inventory quantity must be greater than 0" });
      return;
    }

    await runAction("Inventory updated", async () => {
      await request<void>(`/api/inventory/${productId}/update`, {
        method: "PATCH",
        body: JSON.stringify({ quantity })
      });
      setInventoryAdjustments((current) => ({ ...current, [productId]: "" }));
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
      setCurrentOrderId(result.orderId);
      setPaymentForm((form) => ({ ...form, orderId: result.orderId, paymentId: result.paymentId || form.paymentId }));
      await fetchOrderStatus(result.orderId);
      await refreshAll();
    });
  }

  async function viewOrder(event?: FormEvent) {
    event?.preventDefault();
    const orderId = currentOrderId || lastOrder?.orderId || paymentForm.orderId;
    if (!orderId) {
      setToast({ tone: "error", text: "Enter an order UUID" });
      return;
    }

    await runAction("Order loaded", async () => {
      const result = await request<OrderResult>(`/api/orders/${orderId}`);
      setLastOrder(result);
      setCurrentOrderId(result.orderId);
      setPaymentForm((form) => ({ ...form, orderId: result.orderId, paymentId: result.paymentId || form.paymentId }));
      await refreshAll();
    });
  }

  async function fetchOrderStatus(orderId: string) {
    try {
      const result = await request<OrderResult>(`/api/orders/${orderId}`);
      setLastOrder(result);
      setCurrentOrderId(result.orderId);
      setPaymentForm((form) => ({ ...form, orderId: result.orderId, paymentId: result.paymentId || form.paymentId }));
    } catch {
      // Keep the existing checkout state if the backend is still creating the saga state.
    }
  }

  async function cancelOrder(orderId = lastOrder?.orderId || currentOrderId || paymentForm.orderId) {
    if (!orderId) {
      setToast({ tone: "error", text: "No order selected" });
      return;
    }

    await runAction("Cancellation requested", async () => {
      const result = await request<OrderResult>("/api/orders/cancel", {
        method: "POST",
        body: JSON.stringify({ orderId })
      });
      setLastOrder(result);
      setCurrentOrderId(result.orderId);
      await refreshAll();
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
      setCurrentOrderId(result.orderId);
      setPaymentForm((form) => ({ ...form, paymentId: result.paymentId || form.paymentId }));
      await refreshAll();
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

  function addToCart(productId: string) {
    setCart((current) => ({ ...current, [productId]: (current[productId] || 0) + 1 }));
  }

  function setCartQuantity(productId: string, quantity: number) {
    setCart((current) => {
      const next = { ...current };
      if (quantity <= 0) delete next[productId];
      else next[productId] = quantity;
      return next;
    });
  }

  function removeFromCart(productId: string) {
    setCart((current) => {
      const next = { ...current };
      delete next[productId];
      return next;
    });
  }

  function goHome() {
    setPage("customer");
    setLastOrder(null);
    setCurrentOrderId("");
    setPaymentForm((form) => ({ ...form, orderId: "", paymentId: "" }));
    setCart({});
  }

  return (
    <main>
      <header className="app-header">
        <div>
          <div className="eyebrow">
            <Store size={16} />
            TechStore
          </div>
          <h1>{page === "seller" ? "Seller console" : page === "checkout" ? "Checkout" : "Customer store"}</h1>
        </div>
        <div className="header-actions">
          <nav className="page-tabs" aria-label="Primary">
            <button className={page === "customer" ? "active" : ""} onClick={() => setPage("customer")}>
              <ShoppingCart size={17} />
              Customer
            </button>
            <button className={page === "checkout" ? "active" : ""} onClick={() => setPage("checkout")} disabled={!cartLines.length && !lastOrder}>
              <CreditCard size={17} />
              Checkout
            </button>
            <button className={page === "seller" ? "active" : ""} onClick={() => setPage("seller")}>
              <Store size={17} />
              Seller
            </button>
          </nav>
          <button className="icon-button text-button" onClick={refreshAll} disabled={isLoading} title="Refresh store data">
            <RefreshCw size={18} className={isLoading ? "spin" : ""} />
            Refresh
          </button>
        </div>
      </header>

      <section className="status-strip">
        <Metric label="Products" value={catalog.length} icon={<PackagePlus size={18} />} />
        <Metric label="Inventory rows" value={inventory.length} icon={<Boxes size={18} />} />
        <Metric label="Cart lines" value={cartLines.length} icon={<ShoppingCart size={18} />} />
        <Metric label="Order status" value={lastOrder?.status || "None"} icon={<ClipboardList size={18} />} />
      </section>

      {page === "customer" && (
        <CustomerPage
          cartLines={cartLines}
          cartTotal={cartTotal}
          filteredCatalog={filteredCatalog}
          inventoryByProduct={inventoryByProduct}
          selectedCategory={selectedCategory}
          query={query}
          onAddToCart={addToCart}
          onCategoryChange={setSelectedCategory}
          onCheckout={() => setPage("checkout")}
          onQueryChange={setQuery}
          onRemoveFromCart={removeFromCart}
          onSetCartQuantity={setCartQuantity}
        />
      )}

      {page === "checkout" && (
        <CheckoutPage
          cartLines={cartLines}
          cartTotal={cartTotal}
          currentOrder={currentOrder}
          currentOrderId={currentOrderId}
          isLoading={isLoading}
          lastOrder={lastOrder}
          orderForm={orderForm}
          paymentForm={paymentForm}
          productById={productById}
          onCancelOrder={cancelOrder}
          onCreateOrder={createOrder}
          onHome={goHome}
          onPaymentFormChange={setPaymentForm}
          onRemoveFromCart={removeFromCart}
          onSetCartQuantity={setCartQuantity}
          onSetCurrentOrderId={setCurrentOrderId}
          onSetOrderForm={setOrderForm}
          onSubmitPayment={submitPayment}
          onViewOrder={viewOrder}
        />
      )}

      {page === "seller" && (
        <SellerPage
          filteredCatalog={filteredCatalog}
          inventoryAdjustments={inventoryAdjustments}
          inventoryByProduct={inventoryByProduct}
          isLoading={isLoading}
          orders={safeOrders}
          productForm={productForm}
          query={query}
          selectedCategory={selectedCategory}
          onAddInventory={addInventory}
          onCancelOrder={cancelOrder}
          onCategoryChange={setSelectedCategory}
          onCreateProduct={createProduct}
          onProductFormChange={setProductForm}
          onQueryChange={setQuery}
          onRefresh={refreshAll}
          onSetInventoryAdjustments={setInventoryAdjustments}
        />
      )}

      {toast && (
        <div className={`toast ${toast.tone}`}>
          {toast.tone === "error" ? <XCircle size={18} /> : <CheckCircle2 size={18} />}
          {toast.text}
        </div>
      )}
    </main>
  );
}

createRoot(document.getElementById("root")!).render(<App />);
