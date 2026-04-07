import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
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
export class CartComponent {

  constructor(
    public cartService: CartService,
    private orderService: OrderService,
    private paymentService: PaymentService,
    private router: Router
  ) {}

  updateQuantity(productId: number, quantity: number): void {
    this.cartService.updateQuantity(productId, quantity);
  }

  removeItem(productId: number): void {
    this.cartService.removeFromCart(productId);
  }

  checkout(): void {
    const items = this.cartService.items();

    for (const item of items) {
      if (item.quantity > item.availableStock) {
        alert(`Not enough stock for "${item.product.name}". Only ${item.availableStock} available.`);
        return;
      }
    }

    const orderItems = items.map(item => ({
      productId: item.product.id,
      quantity: item.quantity,
      unitPrice: item.product.price
    }));

    const orderRequest = {
      customerId: 1,
      items: orderItems
    };

    this.orderService.createOrder(orderRequest).subscribe({
      next: (order) => {
        this.paymentService.createCheckout({
          orderId: order.id,
          customerId: 1,
          amount: order.totalAmount
        }).subscribe({
          next: (response) => {
            window.location.href = response.checkoutUrl;
          }
        });
      }
    });
  }
}
