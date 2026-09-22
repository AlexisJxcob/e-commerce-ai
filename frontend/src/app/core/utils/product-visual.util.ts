import { Producto } from '../models/producto.models';

/**
 * Presentación visual de un producto a partir de datos REALES.
 *
 * Reemplaza a `product-presentation.util.ts`, que fabricaba marcas, descuentos,
 * precios "antes", ratings, cuotas, "Patrocinado" y fotos de Unsplash con un
 * PRNG sembrado por id. Nada de eso existe en el backend: era publicidad
 * engañosa y testimonios inventados.
 *
 * Lo que queda es honesto: la categoría visual se deriva del propio texto del
 * producto, y cuando no hay fotografía real se dibuja una placa técnica de
 * taller en vez de simular una foto de catálogo.
 */

export interface CategoriaVisual {
  icono: string;
  etiqueta: string;
  tema: PlateTheme;
}

export type PlateTheme = 'plumbing' | 'tools' | 'electrical' | 'fastening' | 'general';

const ICON_POR_TEMA: Record<PlateTheme, string> = {
  plumbing: 'pi pi-cloud',
  tools: 'pi pi-wrench',
  electrical: 'pi pi-bolt',
  fastening: 'pi pi-cog',
  general: 'pi pi-box'
};

/**
 * Deduce la familia visual leyendo el nombre y las descripciones reales del
 * producto. No inventa atributos: sólo elige cómo dibujarlo.
 */
export function obtenerCategoriaVisual(producto: Producto): CategoriaVisual {
  const texto = `${producto.nombre} ${producto.descripcionTecnica || ''} ${producto.descripcionColoquial || ''}`.toLowerCase();

  if (coincide(texto, ['pvc', 'tubo', 'cañería', 'tubería', 'agua', 'hidrául', 'fuga', 'sifón', 'llave paso', 'gasfitería'])) {
    return { icono: ICON_POR_TEMA.plumbing, etiqueta: 'Fontanería y conducción', tema: 'plumbing' };
  }
  if (coincide(texto, ['martillo', 'alicate', 'destornillador', 'sierra', 'herramienta', 'llave', 'taladro', 'esmeril', 'nivel'])) {
    return { icono: ICON_POR_TEMA.tools, etiqueta: 'Herramientas', tema: 'tools' };
  }
  if (coincide(texto, ['cable', 'aisladora', 'eléctr', 'voltaje', 'enchufe', 'interruptor', 'corriente', 'automático'])) {
    return { icono: ICON_POR_TEMA.electrical, etiqueta: 'Electricidad', tema: 'electrical' };
  }
  if (coincide(texto, ['tornillo', 'perno', 'tarugo', 'adhesivo', 'pegamento', 'fijación', 'tuerca', 'golilla', 'silicona'])) {
    return { icono: ICON_POR_TEMA.fastening, etiqueta: 'Fijación y sellado', tema: 'fastening' };
  }
  return { icono: ICON_POR_TEMA.general, etiqueta: 'Ferretería y repuestos', tema: 'general' };
}

function coincide(texto: string, claves: string[]): boolean {
  return claves.some((clave) => texto.includes(clave));
}

/**
 * Devuelve la imagen a mostrar. Si el backend algún día entrega una fotografía
 * real (`imagenUrl`), se usa; si no, se devuelve la placa técnica dibujada.
 */
export function imagenProducto(producto: Producto): string {
  const real = producto.imagenUrl?.trim();
  if (real) {
    return real;
  }
  return placaTecnica(obtenerCategoriaVisual(producto).tema);
}

/**
 * Placa técnica de taller: retícula milimétrica, marco de medición y una
 * silueta lineal del tipo de pieza. Es un marcador declarado, no una foto
 * falsa de catálogo.
 */
export function placaTecnica(tema: PlateTheme): string {
  const glifo = GLIFOS[tema];
  const raw = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 320" width="320" height="320" role="img">
  <defs>
    <pattern id="grid" width="32" height="32" patternUnits="userSpaceOnUse">
      <path d="M32 0H0V32" fill="none" stroke="#CBD5E1" stroke-width="1" opacity="0.55"/>
    </pattern>
  </defs>
  <rect width="320" height="320" fill="#EDF1F5"/>
  <rect width="320" height="320" fill="url(#grid)"/>
  <rect x="12.5" y="12.5" width="295" height="295" fill="none" stroke="#94A3B8" stroke-width="1"/>
  <g stroke="#94A3B8" stroke-width="1">
    <path d="M12 24h8M12 40h8M12 56h8M12 72h8M12 88h8M12 104h8M12 120h8M12 136h8M12 152h8M12 168h8M12 184h8M12 200h8M12 216h8M12 232h8M12 248h8M12 264h8M12 280h8M12 296h8"/>
    <path d="M24 12v8M40 12v8M56 12v8M72 12v8M88 12v8M104 12v8M120 12v8M136 12v8M152 12v8M168 12v8M184 12v8M200 12v8M216 12v8M232 12v8M248 12v8M264 12v8M280 12v8M296 12v8"/>
  </g>
  <circle cx="160" cy="160" r="86" fill="#FFFFFF" stroke="#CBD5E1" stroke-width="1"/>
  <g transform="translate(160 160) scale(2.4)" fill="none" stroke="#334155" stroke-width="1.75"
     stroke-linecap="round" stroke-linejoin="round">${glifo}</g>
  <g transform="translate(160 288)">
    <path d="M-34 0h68" stroke="#94A3B8" stroke-width="1"/>
    <path d="M-34 -4v8M34 -4v8" stroke="#94A3B8" stroke-width="1"/>
  </g>
</svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(raw)}`;
}

/** Glifos de una sola línea, un solo grosor de trazo, trazo abierto. */
const GLIFOS: Record<PlateTheme, string> = {
  plumbing: `<path d="M-22 -8h44v16h-44z"/><path d="M-30 -14h8v28h-8zM22 -14h8v28h-8z"/><path d="M-8 -8v-10h16v10"/>`,
  tools: `<path d="M-20 14l26-26"/><path d="M6 -12a8 8 0 1 0 11 11l-6-6 3-8-8 3z"/><path d="M-22 12a8 8 0 0 0 8 8l-8-8z"/>`,
  electrical: `<path d="M4 -24L-12 2h10l-4 22L12 -4H2z"/>`,
  fastening: `<path d="M-20 -12h40v10h-40z"/><path d="M-14 -12l-4-6h36l-4 6"/><path d="M-20 6h40v18h-40z"/><path d="M-12 6v18M-4 6v18M4 6v18M12 6v18"/>`,
  general: `<path d="M-22 -16h44v32h-44z"/><path d="M-22 0h44M0 -16v32"/><path d="M-12 -8h24v16h-24z"/>`
};
