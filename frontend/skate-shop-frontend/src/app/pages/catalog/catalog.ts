import { Component, OnInit, signal } from '@angular/core';
import { ProductCardComponent } from '../../shared/components/product-card/product-card';
import { ProductService } from '../../core/services/product';
import { CartService } from '../../core/services/cart';
import { InventoryService, InventoryResponse } from '../../core/services/inventory';
import { Product, ProductFilter } from '../../core/models/product.model';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-catalog',
  standalone: true,
  imports: [ProductCardComponent, FormsModule],
  templateUrl: './catalog.html',
  styleUrl: './catalog.scss'
})
export class CatalogComponent implements OnInit {

  products = signal<Product[]>([]);
  loading = signal(true);
  brands = signal<string[]>([]);
  inventory = signal<Map<number, number>>(new Map());
  filter: ProductFilter = { category: '', brand: '' };
  categories = ['DECK', 'TRUCKS', 'WHEELS'];
  private filterTimer: any;

  constructor(
    private productService: ProductService,
    private cartService: CartService,
    private inventoryService: InventoryService
  ) {}

  ngOnInit(): void {
    this.loadProducts();
    this.loadBrands();
  }

  loadBrands(): void {
    this.productService.getBrands().subscribe({
      next: (brands) => this.brands.set(brands)
    });
  }

  loadProducts(): void {
    this.loading.set(true);
    this.productService.getProducts(this.filter).subscribe({
      next: (products) => {
        this.products.set(products);
        this.loading.set(false);
        this.loadInventory(products.map(p => p.id));
      },
      error: () => this.loading.set(false)
    });
  }

  loadInventory(productIds: number[]): void {
    if (productIds.length === 0) return;
    this.inventoryService.getByProductIds(productIds).subscribe({
      next: (items) => {
        const map = new Map<number, number>();
        items.forEach(item => map.set(item.productId, item.available));
        this.inventory.set(map);
      }
    });
  }

  onFilterChange(): void {
    clearTimeout(this.filterTimer);
    this.filterTimer = setTimeout(() => {
      this.loadProducts();
    }, 400);
  }

  onAddToCart(product: Product): void {
    const stock = this.getStock(product.id);
    this.cartService.addToCart(product, 1, stock);
  }

  clearFilters(): void {
    this.filter = { category: '', brand: '' };
    this.loadProducts();
  }

  getStock(productId: number): number {
    return this.inventory().get(productId) ?? 0;
  }
}
