import { useCallback, useEffect, useState } from "react";

import { listOrders, placeOrder, type Order } from "./api";

const REFRESH_MS = 5000;

export function App() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setOrders(await listOrders());
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }, []);

  useEffect(() => {
    void refresh();
    const timer = setInterval(() => void refresh(), REFRESH_MS);
    return () => clearInterval(timer);
  }, [refresh]);

  return (
    <main>
      <h1>Orders</h1>
      <PlaceOrder onPlaced={refresh} onError={setError} />
      {error && <p className="error">{error}</p>}
      <OrderTable orders={orders} />
    </main>
  );
}

function PlaceOrder({
  onPlaced,
  onError,
}: {
  onPlaced: () => Promise<void>;
  onError: (message: string | null) => void;
}) {
  const [customer, setCustomer] = useState("ada");
  const [sku, setSku] = useState("SKU-1");
  const [qty, setQty] = useState(1);
  const [price, setPrice] = useState(9.99);
  const [placing, setPlacing] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setPlacing(true);
    try {
      await placeOrder(customer, [{ sku, qty, price }]);
      onError(null);
      await onPlaced();
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err));
    } finally {
      setPlacing(false);
    }
  }

  return (
    <form onSubmit={(e) => void submit(e)}>
      <label>
        Customer
        <input value={customer} onChange={(e) => setCustomer(e.target.value)} />
      </label>
      <label>
        SKU
        <input value={sku} onChange={(e) => setSku(e.target.value)} />
      </label>
      <label>
        Quantity
        <input type="number" min={1} value={qty} onChange={(e) => setQty(Number(e.target.value))} />
      </label>
      <label>
        Price
        <input type="number" min={0} step="0.01" value={price} onChange={(e) => setPrice(Number(e.target.value))} />
      </label>
      <button type="submit" disabled={placing}>
        {placing ? "Placing…" : "Place order"}
      </button>
    </form>
  );
}

function OrderTable({ orders }: { orders: Order[] }) {
  if (orders.length === 0) {
    return <p className="empty">No orders yet. Place one above.</p>;
  }
  return (
    <table>
      <thead>
        <tr>
          <th>Received</th>
          <th>Customer</th>
          <th>Items</th>
          <th className="numeric">Total</th>
        </tr>
      </thead>
      <tbody>
        {orders.map((order) => (
          <tr key={order.order_id}>
            <td>{new Date(order.received_at).toLocaleTimeString()}</td>
            <td>{order.customer}</td>
            <td>{order.items.map((i) => `${i.qty} × ${i.sku}`).join(", ")}</td>
            <td className="numeric">{order.total.toFixed(2)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
