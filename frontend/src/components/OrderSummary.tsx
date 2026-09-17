import type { OrderEntry, OrderResult, Product } from "../types";

type Props = {
  order?: OrderEntry;
  fallback: OrderResult | null;
  productById: Map<string, Product>;
};

export function OrderSummary({ order, fallback, productById }: Props) {
  if (!order && !fallback) {
    return <div className="muted">No order selected</div>;
  }

  return (
    <div className="order-box">
      <strong>{order?.status || fallback?.status}</strong>
      {order?.items?.map((item) => (
        <div className="order-item" key={`${order.id}-${item.productId}`}>
          <span>{productById.get(item.productId)?.productName || "Product"}</span>
          <strong>x{item.quantity}</strong>
        </div>
      ))}
    </div>
  );
}
