import { Routes } from '@angular/router';
import { CatalogComponent } from './pages/catalog/catalog';
import { CartComponent } from './pages/cart/cart';
import { PaymentSuccessComponent } from './pages/payment-success/payment-success';

export const routes: Routes = [
  { path: '', component: CatalogComponent },
  { path: 'cart', component: CartComponent },
  { path: 'payment/success', component: PaymentSuccessComponent },
  { path: '**', redirectTo: '' }
];
