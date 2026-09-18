import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { AdminService } from '../../core/services/admin.service';
import { Producto, ProductoRequest } from '../../core/models/producto.models';
import { Categoria, CategoriaRequest } from '../../core/models/categoria.models';
import { ReindexacionResponse } from '../../core/models/asistente.models';
import { ClpPipe } from '../../shared/pipes/clp.pipe';

export type AdminTab = 'productos' | 'categorias' | 'ia';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    DialogModule,
    ButtonModule,
    InputTextModule,
    ClpPipe
  ],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss'
})
export class AdminComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  readonly adminService = inject(AdminService);

  readonly activeTab = signal<AdminTab>('productos');
  readonly searchQuery = signal<string>('');

  // State
  readonly productos = signal<Producto[]>([]);
  readonly categorias = signal<Categoria[]>([]);
  readonly isLoading = signal<boolean>(false);
  readonly isReindexing = signal<boolean>(false);
  readonly reindexResult = signal<ReindexacionResponse | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  // Modals
  readonly isProductModalVisible = signal<boolean>(false);
  readonly editingProductId = signal<number | null>(null);

  readonly isCategoryModalVisible = signal<boolean>(false);
  readonly editingCategoryId = signal<number | null>(null);

  // Inline stock state
  readonly stockEditingMap = signal<Record<number, number | undefined>>({});

  // Forms
  readonly productForm: FormGroup = this.fb.group({
    sku: ['', [Validators.required, Validators.maxLength(50)]],
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    precio: [0, [Validators.required, Validators.min(1)]],
    stock: [0, [Validators.required, Validators.min(0)]],
    categoriaId: [1, [Validators.required]],
    descripcionTecnica: ['', [Validators.required]],
    descripcionColoquial: ['', [Validators.required]]
  });

  readonly categoryForm: FormGroup = this.fb.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    descripcion: [''],
    padreId: [null]
  });

  // Filtered products computed
  readonly filteredProductos = computed(() => {
    const q = this.searchQuery().toLowerCase().trim();
    const list = this.productos();
    if (!q) {
      return list;
    }
    return list.filter(
      (p) =>
        p.nombre.toLowerCase().includes(q) ||
        p.sku.toLowerCase().includes(q) ||
        p.descripcionColoquial.toLowerCase().includes(q)
    );
  });

  ngOnInit(): void {
    this.cargarDatos();
  }

  setTab(tab: AdminTab): void {
    this.activeTab.set(tab);
    this.clearAlerts();
  }

  cargarDatos(): void {
    this.isLoading.set(true);
    this.adminService.getProductos(0, 100).subscribe({
      next: (page) => {
        this.productos.set(page.content);
        const stockMap: Record<number, number> = {};
        page.content.forEach((p) => {
          stockMap[p.id] = p.stock;
        });
        this.stockEditingMap.set(stockMap);
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
        this.errorMessage.set('Error al cargar la lista de productos.');
      }
    });

    this.adminService.getCategorias().subscribe({
      next: (cats) => {
        this.categorias.set(cats);
      },
      error: () => {
        this.errorMessage.set('Error al cargar categorías.');
      }
    });
  }

  // --- Inline Stock Management ---

  onStockInputChange(productId: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = parseInt(input.value, 10);
    if (!Number.isNaN(value) && value >= 0) {
      this.stockEditingMap.update((map) => ({ ...map, [productId]: value }));
    }
  }

  guardarStock(product: Producto): void {
    const nuevoStock = this.stockEditingMap()[product.id] ?? product.stock;
    this.clearAlerts();

    this.adminService.actualizarStock(product.id, nuevoStock).subscribe({
      next: (updated) => {
        this.productos.update((list) =>
          list.map((p) => (p.id === updated.id ? updated : p))
        );
        this.successMessage.set(`Stock de "${product.nombre}" actualizado a ${updated.stock}.`);
      },
      error: (err) => {
        this.errorMessage.set(err?.error?.message ?? 'Error al actualizar el stock.');
      }
    });
  }

  // --- Product CRUD ---

  abrirCrearProducto(): void {
    this.editingProductId.set(null);
    this.productForm.reset({
      sku: '',
      nombre: '',
      precio: 0,
      stock: 0,
      categoriaId: this.categorias()[0]?.id ?? 1,
      descripcionTecnica: '',
      descripcionColoquial: ''
    });
    this.clearAlerts();
    this.isProductModalVisible.set(true);
  }

  abrirEditarProducto(product: Producto): void {
    this.editingProductId.set(product.id);
    this.productForm.patchValue({
      sku: product.sku,
      nombre: product.nombre,
      precio: product.precio,
      stock: product.stock,
      categoriaId: product.categoriaId ?? this.categorias()[0]?.id ?? 1,
      descripcionTecnica: product.descripcionTecnica,
      descripcionColoquial: product.descripcionColoquial
    });
    this.clearAlerts();
    this.isProductModalVisible.set(true);
  }

  cerrarProductModal(): void {
    this.isProductModalVisible.set(false);
    this.editingProductId.set(null);
  }

  guardarProducto(): void {
    if (this.productForm.invalid) {
      this.productForm.markAllAsTouched();
      return;
    }

    const payload: ProductoRequest = {
      ...this.productForm.value,
      precio: Number(this.productForm.value.precio),
      stock: Number(this.productForm.value.stock),
      categoriaId: Number(this.productForm.value.categoriaId)
    };

    const id = this.editingProductId();
    if (id) {
      this.adminService.actualizarProducto(id, payload).subscribe({
        next: (updated) => {
          this.productos.update((list) =>
            list.map((p) => (p.id === updated.id ? updated : p))
          );
          this.cerrarProductModal();
          this.successMessage.set(`Producto "${updated.nombre}" modificado.`);
        },
        error: (err) => {
          this.errorMessage.set(err?.error?.message ?? 'Error al actualizar producto.');
        }
      });
    } else {
      this.adminService.crearProducto(payload).subscribe({
        next: (created) => {
          this.productos.update((list) => [created, ...list]);
          this.cerrarProductModal();
          this.successMessage.set(`Producto "${created.nombre}" creado exitosamente.`);
        },
        error: (err) => {
          this.errorMessage.set(err?.error?.message ?? 'Error al crear producto.');
        }
      });
    }
  }

  eliminarProducto(product: Producto): void {
    if (!confirm(`¿Eliminar definitivamente "${product.nombre}"?`)) {
      return;
    }

    this.adminService.eliminarProducto(product.id).subscribe({
      next: () => {
        this.productos.update((list) => list.filter((p) => p.id !== product.id));
        this.successMessage.set(`Producto "${product.nombre}" eliminado.`);
      },
      error: (err) => {
        this.errorMessage.set(err?.error?.message ?? 'No se pudo eliminar el producto.');
      }
    });
  }

  // --- Category CRUD ---

  abrirCrearCategoria(): void {
    this.editingCategoryId.set(null);
    this.categoryForm.reset({
      nombre: '',
      descripcion: '',
      padreId: null
    });
    this.clearAlerts();
    this.isCategoryModalVisible.set(true);
  }

  cerrarCategoryModal(): void {
    this.isCategoryModalVisible.set(false);
    this.editingCategoryId.set(null);
  }

  guardarCategoria(): void {
    if (this.categoryForm.invalid) {
      this.categoryForm.markAllAsTouched();
      return;
    }

    const payload: CategoriaRequest = {
      nombre: this.categoryForm.value.nombre,
      descripcion: this.categoryForm.value.descripcion || null,
      padreId: this.categoryForm.value.padreId ? Number(this.categoryForm.value.padreId) : null
    };

    const id = this.editingCategoryId();
    if (id) {
      this.adminService.actualizarCategoria(id, payload).subscribe({
        next: (updated) => {
          this.categorias.update((list) =>
            list.map((c) => (c.id === updated.id ? updated : c))
          );
          this.cerrarCategoryModal();
          this.successMessage.set(`Categoría "${updated.nombre}" actualizada.`);
        },
        error: (err) => {
          this.errorMessage.set(err?.error?.message ?? 'Error al actualizar categoría.');
        }
      });
    } else {
      this.adminService.crearCategoria(payload).subscribe({
        next: (created) => {
          this.categorias.update((list) => [...list, created]);
          this.cerrarCategoryModal();
          this.successMessage.set(`Categoría "${created.nombre}" creada.`);
        },
        error: (err) => {
          this.errorMessage.set(err?.error?.message ?? 'Error al crear categoría.');
        }
      });
    }
  }

  eliminarCategoria(category: Categoria): void {
    if (!confirm(`¿Eliminar la categoría "${category.nombre}"?`)) {
      return;
    }

    this.adminService.eliminarCategoria(category.id).subscribe({
      next: () => {
        this.categorias.update((list) => list.filter((c) => c.id !== category.id));
        this.successMessage.set(`Categoría "${category.nombre}" eliminada.`);
      },
      error: (err) => {
        this.errorMessage.set(
          err?.error?.message ?? 'No se pudo eliminar la categoría (puede estar en uso).'
        );
      }
    });
  }

  // --- AI Reindexing Tool ---

  reindexarEmbeddings(): void {
    this.isReindexing.set(true);
    this.reindexResult.set(null);
    this.clearAlerts();

    this.adminService.reindexarEmbeddings().subscribe({
      next: (res) => {
        this.isReindexing.set(false);
        this.reindexResult.set(res);
        this.successMessage.set(
          `Reindexación completada: ${res.procesados} productos procesados, ${res.pendientes} pendientes.`
        );
      },
      error: () => {
        this.isReindexing.set(false);
        this.errorMessage.set('Error durante la reindexación de embeddings vectoriales.');
      }
    });
  }

  getNombreCategoria(categoriaId: number | null): string {
    if (!categoriaId) {
      return 'Sin categoría';
    }
    const cat = this.categorias().find((c) => c.id === categoriaId);
    return cat ? cat.nombre : `Cat #${categoriaId}`;
  }

  private clearAlerts(): void {
    this.successMessage.set(null);
    this.errorMessage.set(null);
  }
}
