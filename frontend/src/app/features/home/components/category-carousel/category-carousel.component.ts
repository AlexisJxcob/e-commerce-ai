import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CarouselModule, CarouselResponsiveOptions } from 'primeng/carousel';
import { Categoria } from '../../../../core/models/categoria.models';
import { Producto } from '../../../../core/models/producto.models';
import { ProductCardComponent } from '../../../../shared/components/product-card';

@Component({
  selector: 'app-category-carousel',
  standalone: true,
  imports: [CommonModule, CarouselModule, ProductCardComponent],
  templateUrl: './category-carousel.component.html',
  styleUrl: './category-carousel.component.scss'
})
export class CategoryCarouselComponent {
  readonly categoria = input.required<Categoria>();
  readonly productos = input.required<Producto[]>();
  readonly addToCart = output<Producto>();

  readonly responsiveOptions: CarouselResponsiveOptions[] = [
    {
      breakpoint: '1400px',
      numVisible: 5,
      numScroll: 1
    },
    {
      breakpoint: '1199px',
      numVisible: 4,
      numScroll: 1
    },
    {
      breakpoint: '991px',
      numVisible: 3,
      numScroll: 1
    },
    {
      breakpoint: '767px',
      numVisible: 2,
      numScroll: 1
    },
    {
      breakpoint: '575px',
      numVisible: 1,
      numScroll: 1
    }
  ];
}
