import type { FormEvent } from "react";
import { ClipboardList, CreditCard, Home, RefreshCw, Trash2, XCircle } from "lucide-react";
import { OrderSummary } from "../components/OrderSummary";
import { useNow } from "../hooks/useNow";
import { currency } from "../lib/constants";
import { formatRemainingTime } from "../lib/format";
import type { CartLine, CustomerType, OrderEntry, OrderForm, OrderResult, PaymentForm, Product, StateSetter } from "../types";

type Props = {
  cartLines: CartLine[];
  cartTotal: number;
  currentOrder?: OrderEntry;
  currentOrderId: string;
  isLoading: boolean;
  lastOrder: OrderResult | null;
  orderForm: OrderForm;
  paymentForm: PaymentForm;
  productById: Map<string, Product>;
  onCancelOrder: () => Promise<void>;
  onCreateOrder: (event: FormEvent) => Promise<void>;
  onHome: () => void;
  onPaymentFormChange: StateSetter<PaymentForm>;
  onRemoveFromCart: (productId: string) => void;
  onSetCartQuantity: (productId: string, quantity: number) => void;
  onSetCurrentOrderId: (value: string) => void;
  onSetOrderForm: StateSetter<OrderForm>;
  onSubmitPayment: (event: FormEvent) => Promise<void>;
  onViewOrder: (event?: FormEvent) => Promise<void>;
};

export function CheckoutPage(props: Props) {
  const expiresAt = props.currentOrder?.expiresAt || props.lastOrder?.expiresAt;
  const hasOrder = Boolean(props.lastOrder || props.currentOrder);
  const now = useNow();

  return (
    <section className="checkout-page">
      <div className="catalog-panel">
        <div className="panel-header">
          <h2>Checkout</h2>
          <button className="icon-button text-button" onClick={props.onHome}>
            <Home size={17} />
            Home
          </button>
        </div>

        <div className="checkout-grid">
          <section className="tool-panel">
            <h2>Cart items</h2>
            <div className="cart-lines">
              {props.cartLines.map((line) => (
                <div className="cart-line checkout-line" key={line.product.productId}>
                  <span>{line.product.productName}</span>
                  <input
                    type="number"
                    min="0"
                    value={line.quantity}
                    onChange={(event) => props.onSetCartQuantity(line.product.productId, Number(event.target.value))}
                    aria-label={`${line.product.productName} quantity`}
                    disabled={hasOrder}
                  />
                  <button className="icon-button square-button" title="Remove item" onClick={() => props.onRemoveFromCart(line.product.productId)} disabled={hasOrder}>
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
          </section>

          <section className="tool-panel">
            <h2>Place order</h2>
            <form onSubmit={props.onCreateOrder} className="form-stack">
              <input value={props.orderForm.email} onChange={(event) => props.onSetOrderForm({ ...props.orderForm, email: event.target.value })} placeholder="Email" required />
              <select value={props.orderForm.customerType} onChange={(event) => props.onSetOrderForm({ ...props.orderForm, customerType: event.target.value as CustomerType })}>
                <option value="GUEST">Guest</option>
                <option value="MEMBER">Member</option>
              </select>
              {props.orderForm.customerType === "MEMBER" && (
                <input value={props.orderForm.customerId} onChange={(event) => props.onSetOrderForm({ ...props.orderForm, customerId: event.target.value })} placeholder="Customer UUID" />
              )}
              <button className="primary-button" type="submit" disabled={!props.cartLines.length || hasOrder}>
                <ClipboardList size={17} />
                Create order
              </button>
            </form>
            {hasOrder && <div className="expiry-box">Reservation time: {formatRemainingTime(expiresAt, now)}</div>}
          </section>

          <section className="tool-panel">
            <h2>Current order</h2>
            <form onSubmit={props.onViewOrder} className="form-stack">
              <input value={props.currentOrderId} onChange={(event) => props.onSetCurrentOrderId(event.target.value)} placeholder="Order UUID" />
              <div className="split-actions">
                <button className="icon-button text-button" type="submit">
                  <RefreshCw size={17} className={props.isLoading ? "spin" : ""} />
                  View
                </button>
                <button className="danger-button" type="button" onClick={() => void props.onCancelOrder()} disabled={!props.lastOrder && !props.currentOrderId && !props.paymentForm.orderId}>
                  <XCircle size={17} />
                  Cancel
                </button>
              </div>
            </form>
            <OrderSummary order={props.currentOrder} fallback={props.lastOrder} productById={props.productById} />
          </section>

          <section className="tool-panel">
            <h2>Payment</h2>
            <form onSubmit={props.onSubmitPayment} className="form-stack">
              <input value={props.paymentForm.orderId} onChange={(event) => props.onPaymentFormChange({ ...props.paymentForm, orderId: event.target.value })} placeholder="Order UUID" required />
              <input value={props.paymentForm.paymentId} onChange={(event) => props.onPaymentFormChange({ ...props.paymentForm, paymentId: event.target.value })} placeholder="Payment UUID" required />
              <input value={props.paymentForm.paymentMethodId} onChange={(event) => props.onPaymentFormChange({ ...props.paymentForm, paymentMethodId: event.target.value })} placeholder="Payment method UUID" required />
              <select value={props.paymentForm.currencyType} onChange={(event) => props.onPaymentFormChange({ ...props.paymentForm, currencyType: event.target.value })}>
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
        </div>
      </div>
    </section>
  );
}
