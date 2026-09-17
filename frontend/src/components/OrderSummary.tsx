import { useNow } from "../hooks/useNow";
import { formatRemainingTime } from "../lib/format";
import type { OrderEntry, OrderResult, Product } from "../types";

type Props = {
  order?: OrderEntry;
  fallback: OrderResult | null;
  productById: Map<string, Product>;
};

export function OrderSummary({ order, fallback, productById }: Props) {
  const now = useNow();

  if (!order && !fallback) {
    return <div className="muted">No order selected</div>;
  }

  return (
    <div className="order-box">
      <span>{order?.id || fallback?.orderId}</span>
      {(order?.paymentId || fallback?.paymentId) && <span>Payment: {order?.paymentId || fallback?.paymentId}</span>}
      {(order?.reservationId || fallback?.reservationId) && <span>Reservation: {order?.reservationId || fallback?.reservationId}</span>}
      <span>Reservation time: {formatRemainingTime(order?.expiresAt || fallback?.expiresAt, now)}</span>
      <strong>{order?.status || fallback?.status}</strong>
      {order?.items?.map((item) => (
        <div className="order-item" key={`${order.id}-${item.productId}`}>
          <span>{productById.get(item.productId)?.productName || item.productId}</span>
          <strong>x{item.quantity}</strong>
        </div>
      ))}
    </div>
  );
}
