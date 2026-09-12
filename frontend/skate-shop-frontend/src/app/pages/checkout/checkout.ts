import { Component, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import { switchMap } from 'rxjs';
import { CartService } from '../../core/services/cart';
import { OrderService } from '../../core/services/order';
import { PaymentService } from '../../core/services/payment';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CurrencyPipe],
  templateUrl: './checkout.html',
  styleUrl: './checkout.scss',
})
export class Checkout {

  submitting = signal(false);
  errorMessage = signal<string | null>(null);

  constructor(
    public cartService: CartService,
    private orderService: OrderService,
    private paymentService: PaymentService,
    private router: Router
  ) {}

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
      switchMap(order => this.paymentService.createCheckout({ orderId: order.id }))
    ).subscribe({
      next: (response) => {
        this.cartService.clearCart();
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

  backToCart(): void {
    this.router.navigate(['/cart']);
  }
}
