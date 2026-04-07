import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface InventoryResponse {
  id: number;
  productId: number;
  quantity: number;
  reserved: number;
  available: number;
}

@Injectable({
  providedIn: 'root'
})
export class InventoryService {

  private apiUrl = 'http://localhost:8082/api/inventory';

  constructor(private http: HttpClient) {}

  getByProductIds(productIds: number[]): Observable<InventoryResponse[]> {
    return this.http.get<InventoryResponse[]>(
      `${this.apiUrl}/batch?productIds=${productIds.join(',')}`
    );
  }
}
