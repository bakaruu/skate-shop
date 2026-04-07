import { Product } from './product.model';

export interface CartItem {
  product: Product;
  quantity: number;
  availableStock: number;
}

export interface Cart {
  items: CartItem[];
  total: number;
}
