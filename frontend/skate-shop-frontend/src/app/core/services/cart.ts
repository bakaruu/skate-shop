import { Injectable, signal, computed } from '@angular/core';
import { CartItem } from '../models/cart.model';
import { Product } from '../models/product.model';

@Injectable({
  providedIn: 'root'
})
export class CartService {

  private cartItems = signal<CartItem[]>([]);

  items = this.cartItems.asReadonly();

  total = computed(() =>
    this.cartItems().reduce((sum, item) =>
      sum + item.product.price * item.quantity, 0)
  );

  itemCount = computed(() =>
    this.cartItems().reduce((sum, item) => sum + item.quantity, 0)
  );

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

    // Solo bloquea subir por encima del stock, bajar siempre permitido
    if (quantity > item.availableStock && quantity > item.quantity) return;

    this.cartItems.set(this.cartItems().map(i =>
      i.product.id === productId ? { ...i, quantity } : i
    ));
  }

  clearCart(): void {
    this.cartItems.set([]);
  }
}
