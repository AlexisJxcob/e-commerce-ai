import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'clp',
  standalone: true
})
export class ClpPipe implements PipeTransform {
  transform(value: number | string | null | undefined): string {
    if (value === null || value === undefined || value === '') {
      return '$ 0';
    }
    const numericValue = typeof value === 'number' ? value : Number(value);
    if (Number.isNaN(numericValue)) {
      return '$ 0';
    }

    const formatted = Math.round(numericValue)
      .toString()
      .replace(/\B(?=(\d{3})+(?!\d))/g, '.');

    return `$ ${formatted}`;
  }
}
