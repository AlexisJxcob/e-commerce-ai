import { Injectable, computed, effect, inject, signal, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, forkJoin, map, of, tap } from 'rxjs';
import { ActualizarItemCarrito, AgregarItemCarrito, Carrito, LineaCarrito } from '../models/carrito.models';
import { Producto } from '../models/producto.models';
import { AuthService } from '../auth/auth.service';

@Injectable({
  providedIn: 'root'
})
export class CarritoService {
  private static readonly GUEST_CART_STORAGE_KEY = 'repara_guest_cart';

  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly platformId = inject(PLATFORM_ID);

  readonly carrito = signal<Carrito | null>(null);
  readonly isOpen = signal<boolean>(false);
  readonly isLoading = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  readonly items = computed(() => this.carrito()?.items ?? []);
  readonly itemCount = computed(() =>
    this.items().reduce((sum, item) => sum + item.cantidad, 0)
  );
  readonly total = computed(() => this.carrito()?.total ?? 0);
  readonly isEmpty = computed(() => this.items().length === 0);

  constructor() {
    // React to auth status changes
    effect(
      () => {
        const isAuth = this.authService.isAuthenticated();
        if (isAuth) {
          this.sincronizarCarritoInvitado().subscribe(() => {
            this.cargarCarrito().subscribe();
          });
        } else {
          this.cargarCarritoInvitado();
        }
      },
      { allowSignalWrites: true }
    );
  }

  cargarCarrito(): Observable<Carrito | null> {
    if (!this.authService.isAuthenticated()) {
      this.cargarCarritoInvitado();
      return of(this.carrito());
    }

    this.isLoading.set(true);
    this.error.set(null);

    return this.http.get<Carrito>('/api/v1/carrito').pipe(
      tap((cart) => {
        this.carrito.set(cart);
        this.isLoading.set(false);
      }),
      catchError(() => {
        this.isLoading.set(false);
        this.error.set('No se pudo cargar el carrito.');
        return of(null);
      })
    );
  }

  agregarProducto(producto: Producto, cantidad: number = 1): Observable<LineaCarrito | null> {
    this.error.set(null);

    if (!this.authService.isAuthenticated()) {
      return of(this.agregarItemInvitado(producto, cantidad));
    }

    this.isLoading.set(true);
    const payload: AgregarItemCarrito = {
      productoId: producto.id,
      cantidad
    };

    return this.http.post<LineaCarrito>('/api/v1/carrito/items', payload).pipe(
      tap(() => {
        this.cargarCarrito().subscribe();
        this.abrirCarrito();
      }),
      catchError(() => {
        this.isLoading.set(false);
        this.error.set('No se pudo agregar el producto al carrito.');
        return of(null);
      })
    );
  }

  actualizarCantidad(itemId: number, cantidad: number): Observable<LineaCarrito | null> {
    if (cantidad <= 0) {
      return this.eliminarItem(itemId).pipe(map(() => null));
    }

    this.error.set(null);

    if (!this.authService.isAuthenticated()) {
      return of(this.actualizarCantidadInvitado(itemId, cantidad));
    }

    this.isLoading.set(true);
    const payload: ActualizarItemCarrito = { cantidad };

    return this.http.patch<LineaCarrito>(`/api/v1/carrito/items/${itemId}`, payload).pipe(
      tap(() => {
        this.cargarCarrito().subscribe();
      }),
      catchError(() => {
        this.isLoading.set(false);
        this.error.set('No se pudo actualizar la cantidad.');
        return of(null);
      })
    );
  }

  eliminarItem(itemId: number): Observable<void> {
    this.error.set(null);

    if (!this.authService.isAuthenticated()) {
      this.eliminarItemInvitado(itemId);
      return of(void 0);
    }

    this.isLoading.set(true);
    return this.http.delete<void>(`/api/v1/carrito/items/${itemId}`).pipe(
      tap(() => {
        this.cargarCarrito().subscribe();
      }),
      catchError(() => {
        this.isLoading.set(false);
        this.error.set('No se pudo eliminar el producto del carrito.');
        return of(void 0);
      })
    );
  }

  vaciarCarrito(): Observable<void> {
    this.error.set(null);

    if (!this.authService.isAuthenticated()) {
      this.guardarCarritoInvitado({ items: [], total: 0 });
      return of(void 0);
    }

    this.isLoading.set(true);
    return this.http.delete<void>('/api/v1/carrito').pipe(
      tap(() => {
        this.carrito.set({ items: [], total: 0 });
        this.isLoading.set(false);
      }),
      catchError(() => {
        this.isLoading.set(false);
        this.error.set('No se pudo vaciar el carrito.');
        return of(void 0);
      })
    );
  }

  abrirCarrito(): void {
    this.isOpen.set(true);
  }

  cerrarCarrito(): void {
    this.isOpen.set(false);
  }

  toggleCarrito(): void {
    this.isOpen.update((open) => !open);
  }

  sincronizarCarritoInvitado(): Observable<void> {
    if (!isPlatformBrowser(this.platformId)) {
      return of(void 0);
    }

    const guestCart = this.leerCarritoInvitadoStorage();
    if (!guestCart || guestCart.items.length === 0) {
      return of(void 0);
    }

    const requests = guestCart.items.map((item) =>
      this.http.post<LineaCarrito>('/api/v1/carrito/items', {
        productoId: item.productoId,
        cantidad: item.cantidad
      }).pipe(
        catchError(() => of(null))
      )
    );

    return forkJoin(requests).pipe(
      tap(() => {
        localStorage.removeItem(CarritoService.GUEST_CART_STORAGE_KEY);
      }),
      map(() => void 0)
    );
  }

  private cargarCarritoInvitado(): void {
    const cart = this.leerCarritoInvitadoStorage();
    this.carrito.set(cart);
  }

  private agregarItemInvitado(producto: Producto, cantidad: number): LineaCarrito {
    const currentCart = this.leerCarritoInvitadoStorage();
    const existingIndex = currentCart.items.findIndex(
      (item) => item.productoId === producto.id
    );

    let updatedLine: LineaCarrito;

    if (existingIndex >= 0) {
      const existing = currentCart.items[existingIndex];
      const newCantidad = existing.cantidad + cantidad;
      updatedLine = {
        ...existing,
        cantidad: newCantidad,
        subtotal: existing.precioUnitario * newCantidad
      };
      currentCart.items[existingIndex] = updatedLine;
    } else {
      updatedLine = {
        id: Date.now(),
        productoId: producto.id,
        nombre: producto.nombre,
        precioUnitario: producto.precio,
        cantidad,
        subtotal: producto.precio * cantidad
      };
      currentCart.items.push(updatedLine);
    }

    currentCart.total = currentCart.items.reduce((sum, item) => sum + item.subtotal, 0);
    this.guardarCarritoInvitado(currentCart);
    this.abrirCarrito();
    return updatedLine;
  }

  private actualizarCantidadInvitado(itemId: number, cantidad: number): LineaCarrito | null {
    const currentCart = this.leerCarritoInvitadoStorage();
    const index = currentCart.items.findIndex((item) => item.id === itemId);
    if (index === -1) {
      return null;
    }

    const item = currentCart.items[index];
    item.cantidad = cantidad;
    item.subtotal = item.precioUnitario * cantidad;
    currentCart.items[index] = item;
    currentCart.total = currentCart.items.reduce((sum, i) => sum + i.subtotal, 0);

    this.guardarCarritoInvitado(currentCart);
    return item;
  }

  private eliminarItemInvitado(itemId: number): void {
    const currentCart = this.leerCarritoInvitadoStorage();
    currentCart.items = currentCart.items.filter((item) => item.id !== itemId);
    currentCart.total = currentCart.items.reduce((sum, i) => sum + i.subtotal, 0);
    this.guardarCarritoInvitado(currentCart);
  }

  private leerCarritoInvitadoStorage(): Carrito {
    if (!isPlatformBrowser(this.platformId)) {
      return { items: [], total: 0 };
    }

    try {
      const stored = localStorage.getItem(CarritoService.GUEST_CART_STORAGE_KEY);
      if (stored) {
        return JSON.parse(stored);
      }
    } catch {
      // Ignore parse errors and fallback
    }

    return { items: [], total: 0 };
  }

  private guardarCarritoInvitado(cart: Carrito): void {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(
        CarritoService.GUEST_CART_STORAGE_KEY,
        JSON.stringify(cart)
      );
    }
    this.carrito.set(cart);
  }
}
