import { Injectable, signal, computed, effect } from '@angular/core';
import { CartItem } from '../models/cart.model';
import { Product } from '../models/product.model';

@Injectable({
  providedIn: 'root'
})
export class CartService {

  private cartItems = signal<CartItem[]>(this.loadFromStorage());

  items = this.cartItems.asReadonly();

  total = computed(() =>
    this.cartItems().reduce((sum, item) =>
      sum + item.product.price * item.quantity, 0)
  );

  itemCount = computed(() =>
    this.cartItems().reduce((sum, item) => sum + item.quantity, 0)
  );

  constructor() {
    effect(() => {
      localStorage.setItem('cart', JSON.stringify(this.cartItems()));
    });
  }

  private loadFromStorage(): CartItem[] {
    try {
      const stored = localStorage.getItem('cart');
      return stored ? JSON.parse(stored) : [];
    } catch {
      return [];
    }
  }

  addToCart(product: Product, quantity: number = 1, availableStock: number = 99): void {
    const current = this.cartItems();
    const existing = current.find(i => i.product.id === product.id);

    if (existing) {
      this.cartItems.set(current.map(i =>
        i.product.id === product.id
          ? { ...i, quantity: i.quantity + quantity }
          : i
      ));
    } else {
      this.cartItems.set([...current, { product, quantity, availableStock }]);
    }
  }

  removeFromCart(productId: number): void {
    this.cartItems.set(this.cartItems().filter(i => i.product.id !== productId));
  }

  updateQuantity(productId: number, quantity: number): void {
    if (quantity <= 0) {
      this.removeFromCart(productId);
      return;
    }
    const item = this.cartItems().find(i => i.product.id === productId);
    if (!item) return;
    if (quantity > item.availableStock && quantity > item.quantity) return;
    this.cartItems.set(this.cartItems().map(i =>
      i.product.id === productId ? { ...i, quantity } : i
    ));
  }

  clearCart(): void {
    this.cartItems.set([]);
    localStorage.removeItem('cart');
  }

  /**
   * Tracks the order created for the Stripe redirect so it can be cancelled if the customer
   * comes back without paying (see CartComponent.ngOnInit) - stock is reserved synchronously at
   * order creation, before payment, so an abandoned checkout would otherwise hold that stock
   * until the order-service TTL sweep catches it (up to app.reservation-ttl-minutes later).
   * localStorage rather than a plain signal because a real navigation to Stripe and back reloads
   * the page - in-memory state wouldn't survive that round trip.
   */
  setPendingOrder(orderId: number): void {
    localStorage.setItem('pendingOrderId', String(orderId));
  }

  getPendingOrder(): number | null {
    const stored = localStorage.getItem('pendingOrderId');
    return stored ? Number(stored) : null;
  }

  clearPendingOrder(): void {
    localStorage.removeItem('pendingOrderId');
  }

  /**
   * Returns the first cart item whose requested quantity exceeds its last-known available
   * stock, or null if every item is within stock. This is only a fast-fail UX check against
   * stale client-side data - the backend's synchronous reservation at order creation is the
   * real authority and is re-checked regardless of what this returns.
   */
  findOverstockedItem(): CartItem | null {
    return this.cartItems().find(item => item.quantity > item.availableStock) ?? null;
  }
}
