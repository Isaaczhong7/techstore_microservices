import type { FormEvent } from "react";
import { CreditCard, Home } from "lucide-react";
import { OrderSummary } from "../components/OrderSummary";
import { useBackendCountdown } from "../hooks/useBackendCountdown";
import { formatRemainingSeconds } from "../lib/format";
import type { OrderEntry, OrderResult, PaymentForm, Product, StateSetter } from "../types";

type Props = {
  currentOrder?: OrderEntry;
  lastOrder: OrderResult | null;
  paymentForm: PaymentForm;
  productById: Map<string, Product>;
  onHome: () => void;
  onPaymentFormChange: StateSetter<PaymentForm>;
  onSubmitPayment: (event: FormEvent) => Promise<void>;
};

export function PaymentPage(props: Props) {
  const expiresInSeconds = props.currentOrder?.expiresInSeconds ?? props.lastOrder?.expiresInSeconds;
  const remainingSeconds = useBackendCountdown(expiresInSeconds);

  return (
    <section className="checkout-page">
      <div className="catalog-panel payment-shell">
        <div className="panel-header">
          <h2>Payment</h2>
          <button className="icon-button text-button" onClick={props.onHome}>
            <Home size={17} />
            Home
          </button>
        </div>

        <div className="checkout-grid">
          <section className="tool-panel payment-panel">
            <h2>Payment details</h2>
            <div className="expiry-box payment-countdown">Payment window: {formatRemainingSeconds(remainingSeconds)}</div>
            <form onSubmit={props.onSubmitPayment} className="form-stack">
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

          <section className="tool-panel">
            <h2>Order</h2>
            <OrderSummary order={props.currentOrder} fallback={props.lastOrder} productById={props.productById} />
          </section>
        </div>
      </div>
    </section>
  );
}
