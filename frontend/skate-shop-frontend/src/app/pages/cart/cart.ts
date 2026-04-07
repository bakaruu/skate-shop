import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import { CartService } from '../../core/services/cart';
import { OrderService } from '../../core/services/order';
import { PaymentService } from '../../core/services/payment';
import { CartItem } from '../../core/models/cart.model';

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
    const items = this.cartService.items().map(item => ({
      productId: item.product.id,
      quantity: item.quantity,
      unitPrice: item.product.price
    }));

    const orderRequest = {
      customerId: 1,
      items
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
