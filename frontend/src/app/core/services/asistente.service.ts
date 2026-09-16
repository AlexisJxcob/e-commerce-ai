import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, tap, throwError } from 'rxjs';
import { BusquedaInteligenteResponse } from '../models/asistente.models';
import { ErrorResponse } from '../models/error.models';

@Injectable({
  providedIn: 'root'
})
export class AsistenteService {
  private static readonly ASISTENTE_API_URL = '/api/v1/productos/asistente';

  private readonly http = inject(HttpClient);

  readonly currentQuery = signal<string>('');
  readonly isLoading = signal<boolean>(false);
  readonly response = signal<BusquedaInteligenteResponse | null>(null);
  readonly error = signal<string | null>(null);

  readonly hasResult = computed(() => !!this.response());
  readonly sugerencia = computed(() => this.response()?.sugerencia ?? null);
  readonly productos = computed(() => this.response()?.productos ?? []);

  buscarRecomendacion(query: string): Observable<BusquedaInteligenteResponse> {
    const params = new HttpParams().set('q', query);
    return this.http.get<BusquedaInteligenteResponse>(AsistenteService.ASISTENTE_API_URL, { params });
  }

  buscar(query: string): Observable<BusquedaInteligenteResponse> {
    const trimmed = query.trim();
    if (!trimmed) {
      return throwError(() => new Error('La consulta no puede estar vacía.'));
    }

    this.isLoading.set(true);
    this.error.set(null);
    this.currentQuery.set(trimmed);

    return this.buscarRecomendacion(trimmed).pipe(
      tap((data) => {
        this.response.set(data);
        this.isLoading.set(false);
      }),
      catchError((err) => {
        this.isLoading.set(false);
        const errorBody: ErrorResponse | undefined = err.error;
        const message =
          errorBody?.message ??
          'No pudimos procesar tu consulta técnica con el asistente. Intenta nuevamente.';
        this.error.set(message);
        return throwError(() => err);
      })
    );
  }

  limpiar(): void {
    this.currentQuery.set('');
    this.isLoading.set(false);
    this.response.set(null);
    this.error.set(null);
  }
}
