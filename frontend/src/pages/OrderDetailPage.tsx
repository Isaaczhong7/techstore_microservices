import { CreditCard, Home, XCircle } from "lucide-react";
import { StatusText } from "../components/Status";
import { currency } from "../lib/constants";
import { formatEnum } from "../lib/format";
import type { OrderEntry, Product } from "../types";

type Props = {
  order?: OrderEntry;
  productById: Map<string, Product>;
  onCancelOrder: () => Promise<void>;
  onHome: () => void;
  onPaymentPage: () => void;
};

export function OrderDetailPage({ order, productById, onCancelOrder, onHome, onPaymentPage }: Props) {
  if (!order) {
    return (
      <section className="checkout-page">
        <div className="catalog-panel">
          <div className="panel-header">
            <h2>Order details</h2>
            <button className="icon-button text-button" onClick={onHome}>
              <Home size={17} />
              Home
            </button>
          </div>
          <div className="empty-state">Select an order to view details</div>
        </div>
      </section>
    );
  }

  return (
    <section className="checkout-page">
      <div className="catalog-panel order-detail-shell">
        <div className="panel-header">
          <h2>Order details</h2>
          <button className="icon-button text-button" onClick={onHome}>
            <Home size={17} />
            Home
          </button>
        </div>

        <div className="checkout-grid">
          <section className="tool-panel">
            <h2>Summary</h2>
            <div className="detail-list">
              <div>
                <span>Status: </span>
                <StatusText value={order.status} />
              </div>
              <div>
                <span>Payment: </span>
                <strong>{order.paymentStatus ? formatEnum(order.paymentStatus) : "Pending"}</strong>
              </div>
              <div>
                <span>Customer: </span>
                <strong>{order.email}</strong>
              </div>
              <div>
                <span>Checkout type: </span>
                <strong>{formatEnum(order.customerType)}</strong>
              </div>
              <div>
                <span>Total: </span>
                <strong>{currency.format(Number(order.totalAmount || 0))}</strong>
              </div>
            </div>
            <div className="split-actions">
              <button className="primary-button" onClick={onPaymentPage} disabled={order.status === "CANCELLED" || order.status === "CONFIRMED"}>
                <CreditCard size={17} />
                Pay
              </button>
              <button className="danger-button" onClick={() => void onCancelOrder()} disabled={order.status === "CANCELLED" || order.status === "CONFIRMED"}>
                <XCircle size={17} />
                Cancel
              </button>
            </div>
          </section>

          <section className="tool-panel">
            <h2>Items</h2>
            <div className="cart-lines">
              {order.items?.map((item) => {
                const product = productById.get(item.productId);
                return (
                  <div className="order-line" key={item.productId}>
                    <span>{product?.productName || "Product"}: </span>
                    <strong>x{item.quantity}</strong>
                    <span>{item.unitPrice != null ? currency.format(Number(item.unitPrice)) : "Price pending"}</span>
                  </div>
                );
              })}
              {!order.items?.length && <div className="muted">Items are still loading</div>}
            </div>
          </section>
        </div>
      </div>
    </section>
  );
}
