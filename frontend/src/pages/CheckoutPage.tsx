import type { FormEvent } from "react";
import { ClipboardList, CreditCard, Home, RefreshCw, Trash2, XCircle } from "lucide-react";
import { OrderSummary } from "../components/OrderSummary";
import { currency } from "../lib/constants";
import type { CartLine, OrderEntry, OrderForm, OrderResult, PaymentForm, Product, StateSetter } from "../types";

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
  onPaymentPage: () => void;
  onRemoveFromCart: (productId: string) => void;
  onSetCartQuantity: (productId: string, quantity: number) => void;
  onSetCurrentOrderId: (value: string) => void;
  onSetOrderForm: StateSetter<OrderForm>;
  onViewOrder: (event?: FormEvent) => Promise<void>;
};

export function CheckoutPage(props: Props) {
  const hasOrder = Boolean(props.lastOrder || props.currentOrder);

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
              <button className="primary-button" type="submit" disabled={!props.cartLines.length || hasOrder}>
                <ClipboardList size={17} />
                Create order
              </button>
            </form>
            <button className="primary-button" type="button" onClick={props.onPaymentPage} disabled={!hasOrder}>
              <CreditCard size={17} />
              Continue to payment
            </button>
          </section>

          <section className="tool-panel">
            <h2>Current order</h2>
            <div className="split-actions">
              <button className="icon-button text-button" type="button" onClick={() => void props.onViewOrder()} disabled={!props.lastOrder && !props.currentOrderId && !props.paymentForm.orderId}>
                <RefreshCw size={17} className={props.isLoading ? "spin" : ""} />
                Refresh
              </button>
              <button className="danger-button" type="button" onClick={() => void props.onCancelOrder()} disabled={!props.lastOrder && !props.currentOrderId && !props.paymentForm.orderId}>
                <XCircle size={17} />
                Cancel
              </button>
            </div>
            <OrderSummary order={props.currentOrder} fallback={props.lastOrder} productById={props.productById} />
          </section>

        </div>
      </div>
    </section>
  );
}
