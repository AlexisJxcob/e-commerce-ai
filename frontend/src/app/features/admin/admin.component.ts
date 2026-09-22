import { Component, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';

import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { TabsModule } from 'primeng/tabs';
import { BadgeModule } from 'primeng/badge';
import { TableModule } from 'primeng/table';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { CardModule } from 'primeng/card';
import { ProgressBarModule } from 'primeng/progressbar';
import { MessageService, ConfirmationService } from 'primeng/api';
import { AdminService } from '../../core/services/admin.service';
import { Producto, ProductoRequest } from '../../core/models/producto.models';
import { Categoria, CategoriaRequest } from '../../core/models/categoria.models';
import { ReindexacionResponse } from '../../core/models/asistente.models';
import { ClpPipe } from '../../shared/pipes/clp.pipe';

export type AdminTab = 'productos' | 'categorias' | 'ia';

@Component({
    selector: 'app-admin',
    imports: [
    FormsModule,
    ReactiveFormsModule,
    DialogModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    TagModule,
    TooltipModule,
    TabsModule,
    BadgeModule,
    TableModule,
    IconFieldModule,
    InputIconModule,
    SelectModule,
    TextareaModule,
    CardModule,
    ProgressBarModule,
    ClpPipe
],
    templateUrl: './admin.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrl: './admin.component.scss'
})
export class AdminComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly messageService = inject(MessageService);
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

  // Categories available to be chosen as parent (prevent cyclic self-parenting)
  readonly categoriasDisponiblesComoPadre = computed(() => {
    const currentId = this.editingCategoryId();
    if (!currentId) {
      return this.categorias();
    }
    return this.categorias().filter((c) => c.id !== currentId);
  });

  ngOnInit(): void {
    this.cargarDatos();
  }

  setTab(tab: string | number | undefined): void {
    if (tab && (tab === 'productos' || tab === 'categorias' || tab === 'ia')) {
      this.activeTab.set(tab);
      this.clearAlerts();
    }
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

  onStockNumberChange(productId: number, value: number | null): void {
    if (value !== null && value >= 0) {
      this.stockEditingMap.update((map) => ({ ...map, [productId]: value }));
    }
  }

  guardarStock(product: Producto): void {
    const nuevoStock = this.stockEditingMap()[product.id] ?? product.stock;

    this.adminService.actualizarStock(product.id, nuevoStock).subscribe({
      next: (updated) => {
        this.productos.update((list) =>
          list.map((p) => (p.id === updated.id ? updated : p))
        );
        this.messageService.add({
          severity: 'success',
          summary: 'Stock actualizado',
          detail: `Stock de "${product.nombre}" actualizado a ${updated.stock} un.`,
          life: 3000
        });
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error al actualizar',
          detail: err?.error?.message ?? 'No se pudo actualizar el stock.',
          life: 4000
        });
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
          this.messageService.add({
            severity: 'success',
            summary: 'Producto actualizado',
            detail: `Producto "${updated.nombre}" actualizado correctamente.`,
            life: 3000
          });
        },
        error: (err) => {
          this.messageService.add({
            severity: 'error',
            summary: 'Error al actualizar',
            detail: err?.error?.message ?? 'Error al actualizar producto.',
            life: 4000
          });
        }
      });
    } else {
      this.adminService.crearProducto(payload).subscribe({
        next: (created) => {
          this.productos.update((list) => [created, ...list]);
          this.cerrarProductModal();
          this.messageService.add({
            severity: 'success',
            summary: 'Producto creado',
            detail: `Producto "${created.nombre}" creado exitosamente.`,
            life: 3000
          });
        },
        error: (err) => {
          this.messageService.add({
            severity: 'error',
            summary: 'Error al crear',
            detail: err?.error?.message ?? 'Error al crear producto.',
            life: 4000
          });
        }
      });
    }
  }

  eliminarProducto(product: Producto): void {
    this.confirmationService.confirm({
      message: `¿Estás seguro de que deseas eliminar definitivamente el producto "${product.nombre}"?`,
      header: 'Eliminar Producto',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Sí, eliminar',
      rejectLabel: 'Cancelar',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-sm',
      accept: () => {
        this.adminService.eliminarProducto(product.id).subscribe({
          next: () => {
            this.productos.update((list) => list.filter((p) => p.id !== product.id));
            this.messageService.add({
              severity: 'info',
              summary: 'Producto eliminado',
              detail: `Producto "${product.nombre}" eliminado correctamente.`,
              life: 3000
            });
          },
          error: (err) => {
            this.messageService.add({
              severity: 'error',
              summary: 'Error al eliminar',
              detail: err?.error?.message ?? 'No se pudo eliminar el producto.',
              life: 4000
            });
          }
        });
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
          this.messageService.add({
            severity: 'success',
            summary: 'Categoría actualizada',
            detail: `Categoría "${updated.nombre}" actualizada.`,
            life: 3000
          });
        },
        error: (err) => {
          this.messageService.add({
            severity: 'error',
            summary: 'Error al actualizar',
            detail: err?.error?.message ?? 'Error al actualizar categoría.',
            life: 4000
          });
        }
      });
    } else {
      this.adminService.crearCategoria(payload).subscribe({
        next: (created) => {
          this.categorias.update((list) => [...list, created]);
          this.cerrarCategoryModal();
          this.messageService.add({
            severity: 'success',
            summary: 'Categoría creada',
            detail: `Categoría "${created.nombre}" creada con éxito.`,
            life: 3000
          });
        },
        error: (err) => {
          this.messageService.add({
            severity: 'error',
            summary: 'Error al crear',
            detail: err?.error?.message ?? 'Error al crear categoría.',
            life: 4000
          });
        }
      });
    }
  }

  eliminarCategoria(category: Categoria): void {
    this.confirmationService.confirm({
      message: `¿Estás seguro de que deseas eliminar la categoría "${category.nombre}"?`,
      header: 'Eliminar Categoría',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Sí, eliminar',
      rejectLabel: 'Cancelar',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-sm',
      accept: () => {
        this.adminService.eliminarCategoria(category.id).subscribe({
          next: () => {
            this.categorias.update((list) => list.filter((c) => c.id !== category.id));
            this.messageService.add({
              severity: 'info',
              summary: 'Categoría eliminada',
              detail: `Categoría "${category.nombre}" eliminada correctamente.`,
              life: 3000
            });
          },
          error: (err) => {
            this.messageService.add({
              severity: 'error',
              summary: 'Error al eliminar',
              detail:
                err?.error?.message ??
                'No se pudo eliminar la categoría (puede estar en uso por productos existentes).',
              life: 4500
            });
          }
        });
      }
    });
  }

  // --- AI Reindexing Tool ---

  reindexarEmbeddings(): void {
    this.isReindexing.set(true);
    this.reindexResult.set(null);

    this.adminService.reindexarEmbeddings().subscribe({
      next: (res) => {
        this.isReindexing.set(false);
        this.reindexResult.set(res);
        this.messageService.add({
          severity: 'success',
          summary: 'Reindexación completada',
          detail: `${res.procesados} productos indexados con éxito (${res.pendientes} pendientes).`,
          life: 4000
        });
      },
      error: () => {
        this.isReindexing.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Error de reindexación',
          detail: 'No se pudo completar la indexación vectorial en el servidor.',
          life: 4000
        });
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
