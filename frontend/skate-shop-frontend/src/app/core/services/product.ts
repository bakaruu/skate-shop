import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Product, ProductFilter } from '../models/product.model';

@Injectable({
  providedIn: 'root'
})
export class ProductService {

  private apiUrl = 'http://localhost:8080/api/products';

  constructor(private http: HttpClient) {}

  getProducts(filter?: ProductFilter): Observable<Product[]> {
    let params = new HttpParams();
    if (filter?.brand) params = params.set('brand', filter.brand);
    if (filter?.category) params = params.set('category', filter.category);
    if (filter?.minPrice) params = params.set('minPrice', filter.minPrice);
    if (filter?.maxPrice) params = params.set('maxPrice', filter.maxPrice);
    if (filter?.name) params = params.set('name', filter.name);
    return this.http.get<Product[]>(this.apiUrl, { params });
  }

  getProductById(id: number): Observable<Product> {
    return this.http.get<Product>(`${this.apiUrl}/${id}`);
  }
}
