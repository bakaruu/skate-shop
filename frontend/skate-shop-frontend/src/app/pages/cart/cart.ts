import { Component, OnInit, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { switchMap, tap } from 'rxjs';
import { CartService } from '../../core/services/cart';
import { OrderService } from '../../core/services/order';
import { PaymentService } from '../../core/services/payment';

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [CurrencyPipe],
  templateUrl: './cart.html',
  styleUrl: './cart.scss'
})
export class CartComponent implements OnInit {

  submitting = signal(false);
  errorMessage = signal<string | null>(null);

  constructor(
    public cartService: CartService,
    private orderService: OrderService,
    private paymentService: PaymentService
  ) {}

  ngOnInit(): void {
    // Arriving here with a pending order means the customer went to Stripe and came back
    // (browser back, or cancelling) without paying - release that reservation right away instead
    // of leaving it to the order-service TTL sweep, which would otherwise hold the stock for up
    // to app.reservation-ttl-minutes for no reason.
    const pendingOrderId = this.cartService.getPendingOrder();
    if (pendingOrderId) {
      this.orderService.cancelOrder(pendingOrderId).subscribe({
        complete: () => this.cartService.clearPendingOrder(),
        error: () => this.cartService.clearPendingOrder()
      });
    }
  }

  updateQuantity(productId: number, quantity: number): void {
    this.cartService.updateQuantity(productId, quantity);
  }

  removeItem(productId: number): void {
    this.cartService.removeFromCart(productId);
  }

  placeOrder(): void {
    const items = this.cartService.items();

    if (items.length === 0) {
      return;
    }

    const overstocked = this.cartService.findOverstockedItem();
    if (overstocked) {
      this.errorMessage.set(
        `Not enough stock for "${overstocked.product.name}". Only ${overstocked.availableStock} available.`
      );
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true);

    const orderRequest = {
      customerId: 1,
      items: items.map(item => ({
        productId: item.product.id,
        quantity: item.quantity
      }))
    };

    this.orderService.createOrder(orderRequest).pipe(
      tap(order => this.cartService.setPendingOrder(order.id)),
      switchMap(order => this.paymentService.createCheckout({ orderId: order.id }))
    ).subscribe({
      next: (response) => {
        // Cart is cleared on the /payment/success page instead, once Stripe actually confirms
        // payment - clearing it here would wipe it out even if the customer hits "back" from
        // Stripe without paying, losing their cart for an order that was never completed.
        window.location.href = response.checkoutUrl;
      },
      error: (err) => {
        this.submitting.set(false);
        this.errorMessage.set(
          err?.status === 409
            ? 'Sorry, one or more items just went out of stock.'
            : 'Could not complete checkout. Please try again.'
        );
      }
    });
  }
}
