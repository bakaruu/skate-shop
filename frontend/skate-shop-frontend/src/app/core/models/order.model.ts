export interface OrderItemRequest {
  productId: number;
  quantity: number;
  unitPrice: number;
}

export interface OrderRequest {
  customerId: number;
  items: OrderItemRequest[];
}

export interface OrderResponse {
  id: number;
  customerId: number;
  status: string;
  totalAmount: number;
  createdAt: string;
  items: OrderItemResponse[];
}

export interface OrderItemResponse {
  id: number;
  productId: number;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}
