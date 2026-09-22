import { Component, ChangeDetectionStrategy } from '@angular/core';

import { SkeletonModule } from 'primeng/skeleton';

@Component({
    selector: 'app-search-skeleton',
    imports: [SkeletonModule],
    templateUrl: './search-skeleton.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrl: './search-skeleton.component.scss'
})
export class SearchSkeletonComponent {}
