// Everything this app asks of the API. VITE_API_BASE overrides where it asks;
// it defaults to /api, which is what CloudFront serves the deployed app on, so
// the browser never learns the API Gateway URL.
const BASE: string = (import.meta.env["VITE_API_BASE"] as string | undefined) ?? "/api";

export interface Item {
  sku: string;
  qty: number;
  price: number;
}

export interface Order {
  order_id: string;
  customer: string;
  items: Item[];
  total: number;
  received_at: string;
}

// The API answers an error with {"error": "..."}, and that sentence is written
// to be shown to a person, so it is what reaches the screen.
async function reason(res: Response): Promise<string> {
  try {
    const body = (await res.json()) as { error?: string };
    if (body.error) return body.error;
  } catch {
    // Fall through to the status line.
  }
  return `${res.status} ${res.statusText}`;
}

export async function listOrders(limit = 50): Promise<Order[]> {
  const res = await fetch(`${BASE}/orders?limit=${limit}`);
  if (!res.ok) throw new Error(await reason(res));
  const body = (await res.json()) as { orders: Order[] };
  return body.orders;
}

export async function placeOrder(customer: string, items: Item[]): Promise<Order> {
  const res = await fetch(`${BASE}/orders`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ customer, items }),
  });
  if (!res.ok) throw new Error(await reason(res));
  return (await res.json()) as Order;
}
