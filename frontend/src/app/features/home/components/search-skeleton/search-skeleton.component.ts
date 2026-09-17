import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SkeletonModule } from 'primeng/skeleton';

@Component({
  selector: 'app-search-skeleton',
  standalone: true,
  imports: [CommonModule, SkeletonModule],
  templateUrl: './search-skeleton.component.html',
  styleUrl: './search-skeleton.component.scss'
})
export class SearchSkeletonComponent {}
