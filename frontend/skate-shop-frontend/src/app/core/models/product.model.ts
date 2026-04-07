export interface Product {
  id: number;
  name: string;
  brand: string;
  category: 'DECK' | 'TRUCKS' | 'WHEELS';
  price: number;
  description: string;
  imageUrl: string;
  width: number;
  active: boolean;
}

export interface ProductFilter {
  brand?: string;
  category?: string;
  minPrice?: number;
  maxPrice?: number;
  name?: string;
}
