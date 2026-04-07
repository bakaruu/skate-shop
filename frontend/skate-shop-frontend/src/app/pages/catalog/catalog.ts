import { Component, OnInit, signal } from '@angular/core';
import { ProductCardComponent } from '../../shared/components/product-card/product-card';
import { ProductService } from '../../core/services/product';
import { CartService } from '../../core/services/cart';
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
  filter: ProductFilter = {
    category: '',
    brand: ''
  };
  categories = ['DECK', 'TRUCKS', 'WHEELS'];

  constructor(
    private productService: ProductService,
    private cartService: CartService
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
      },
      error: () => this.loading.set(false)
    });
  }

  private filterTimer: any;

  onFilterChange(): void {
    clearTimeout(this.filterTimer);
    this.filterTimer = setTimeout(() => {
      this.loadProducts();
    }, 400);
  }

  onAddToCart(product: Product): void {
    this.cartService.addToCart(product);
  }

  clearFilters(): void {
    this.filter = {};
    this.loadProducts();
  }
}
