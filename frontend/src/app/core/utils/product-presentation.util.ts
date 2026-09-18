import { Producto } from '../models/producto.models';

export interface ProductPresentation {
  marca: string;
  imagenUrl: string;
  fallbackSvg: string;
  precioAnterior: number;
  descuentoPorcentaje: number;
  rating: number;
  reviewCount: number;
  cuotasSinInteres: number;
  patrocinado: boolean;
}

function createPrng(seed: number): () => number {
  let s = (Math.abs(seed) * 1103515245 + 12345) % 2147483647;
  return () => {
    s = (s * 16807 + 11) % 2147483647;
    return (s - 1) / 2147483646;
  };
}

const BRANDS_PLUMBING = ['TIGRE', 'VINILIT', 'HOFFMAN', 'FAS', 'NIBCO'];
const BRANDS_TOOLS = ['BAUKER', 'UBERMANN', 'BOSCH', 'STANLEY', 'DEWALT'];
const BRANDS_ELECTRICAL = ['LEGRAND', 'SCHNEIDER', 'BTICINO', 'KOLFF'];
const BRANDS_OUTDOOR = ['MR BEEF', 'JUST HOME COLLECTION', 'ELEMENT DESIGN'];
const BRANDS_BUILDING = ['HOLZTEK', 'TOEX', 'MELON', 'SIPA'];
const BRANDS_GENERAL = ['REPARA PRO', 'REDLINE', 'FORCE', 'PRO-SERIES'];

function resolveBrand(producto: Producto, rand: () => number): string {
  if (producto.marca) {
    return producto.marca.toUpperCase();
  }
  const text = `${producto.nombre} ${producto.descripcionTecnica || ''} ${producto.descripcionColoquial || ''}`.toLowerCase();
  let pool = BRANDS_GENERAL;
  if (text.includes('pvc') || text.includes('tubo') || text.includes('agua') || text.includes('fuga') || text.includes('llave paso') || text.includes('sifón')) {
    pool = BRANDS_PLUMBING;
  } else if (text.includes('taladro') || text.includes('martillo') || text.includes('alicate') || text.includes('sierra') || text.includes('destornillador') || text.includes('herramienta')) {
    pool = BRANDS_TOOLS;
  } else if (text.includes('cable') || text.includes('enchufe') || text.includes('interruptor') || text.includes('eléctr') || text.includes('volt')) {
    pool = BRANDS_ELECTRICAL;
  } else if (text.includes('parrilla') || text.includes('terraza') || text.includes('jardín') || text.includes('carbón') || text.includes('gas')) {
    pool = BRANDS_OUTDOOR;
  } else if (text.includes('piso') || text.includes('porcelanato') || text.includes('cemento') || text.includes('pintura') || text.includes('adhesivo')) {
    pool = BRANDS_BUILDING;
  }
  const index = Math.floor(rand() * pool.length);
  return pool[index];
}

function resolveImageUrls(producto: Producto): { primary: string; svg: string } {
  const text = `${producto.nombre} ${producto.descripcionTecnica || ''}`.toLowerCase();

  let categoryTheme = 'tools';
  let primary = 'https://images.unsplash.com/photo-1581244277943-fe4a9c777189?w=600&auto=format&fit=crop&q=80';

  if (text.includes('parrilla') || text.includes('asado') || text.includes('barbecue')) {
    categoryTheme = 'grill';
    primary = 'https://images.unsplash.com/photo-1555041469-a586c61ea9bc?w=600&auto=format&fit=crop&q=80';
  } else if (text.includes('terraza') || text.includes('jardín') || text.includes('mueble')) {
    categoryTheme = 'furniture';
    primary = 'https://images.unsplash.com/photo-1586023492125-27b2c045efd7?w=600&auto=format&fit=crop&q=80';
  } else if (text.includes('taladro') || text.includes('percutor') || text.includes('inalámbrico')) {
    categoryTheme = 'drill';
    primary = 'https://images.unsplash.com/photo-1504148455328-c376907d081c?w=600&auto=format&fit=crop&q=80';
  } else if (text.includes('martillo') || text.includes('alicate') || text.includes('herramienta') || text.includes('llave')) {
    categoryTheme = 'handtool';
    primary = 'https://images.unsplash.com/photo-1586864387967-d02ef85d93e8?w=600&auto=format&fit=crop&q=80';
  } else if (text.includes('pvc') || text.includes('tubo') || text.includes('agua') || text.includes('fuga') || text.includes('cañería')) {
    categoryTheme = 'pipe';
    primary = 'https://images.unsplash.com/photo-1581092160607-ee22621dd758?w=600&auto=format&fit=crop&q=80';
  } else if (text.includes('tornillo') || text.includes('perno') || text.includes('fijación') || text.includes('tuerca')) {
    categoryTheme = 'screws';
    primary = 'https://images.unsplash.com/photo-1530124566582-a618bc2615dc?w=600&auto=format&fit=crop&q=80';
  } else if (text.includes('piso') || text.includes('porcelanato') || text.includes('cerámica')) {
    categoryTheme = 'tiles';
    primary = 'https://images.unsplash.com/photo-1584622650111-993a426fbf0a?w=600&auto=format&fit=crop&q=80';
  }

  if (producto.imagenUrl) {
    primary = producto.imagenUrl;
  }

  const svg = createFallbackSvg(producto.nombre, categoryTheme);

  return { primary, svg };
}

function createFallbackSvg(nombre: string, theme: string): string {
  const safeName = (nombre || 'Producto').substring(0, 24).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const iconSymbol = getCategorySymbol(theme);

  const rawSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 300" width="100%" height="100%">
    <defs>
      <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="#F8FAFC"/>
        <stop offset="100%" stop-color="#EDF2F7"/>
      </linearGradient>
    </defs>
    <rect width="100%" height="100%" fill="url(#bg)"/>
    <circle cx="150" cy="130" r="64" fill="#E2E8F0" opacity="0.8"/>
    <g transform="translate(150, 130) scale(1.6)">
      ${iconSymbol}
    </g>
    <text x="150" y="225" text-anchor="middle" font-family="system-ui, -apple-system, sans-serif" font-size="13" font-weight="600" fill="#475569">${safeName}</text>
    <text x="150" y="245" text-anchor="middle" font-family="system-ui, -apple-system, sans-serif" font-size="11" font-weight="500" fill="#94A3B8">REPARA.AI FERRETERÍA</text>
  </svg>`;

  return `data:image/svg+xml;utf8,${encodeURIComponent(rawSvg)}`;
}

function getCategorySymbol(theme: string): string {
  switch (theme) {
    case 'drill':
    case 'tools':
      return `<path d="M-12,-8 L8,-8 L12,-4 L8,0 L4,0 L4,12 L-4,12 L-4,0 L-12,0 Z" fill="#2563EB" transform="translate(0,-2)"/>`;
    case 'pipe':
      return `<path d="M-16,-6 L16,-6 L16,6 L-16,6 Z M-10,-12 L-6,-12 L-6,12 L-10,12 Z M6,-12 L10,-12 L10,12 L6,12 Z" fill="#0284C7"/>`;
    case 'grill':
      return `<path d="M-14,-4 C-14,6 14,6 14,-4 Z M-12,8 L-10,16 M12,8 L10,16 M-14,-6 L14,-6" stroke="#DC2626" stroke-width="2.5" fill="none" stroke-linecap="round"/>`;
    default:
      return `<path d="M-10,-10 L10,-10 L10,10 L-10,10 Z M-10,0 L10,0 M0,-10 L0,10" stroke="#059669" stroke-width="2" fill="none"/>`;
  }
}

export function getProductPresentation(producto: Producto): ProductPresentation {
  const rand = createPrng(producto.id || (producto.sku ? producto.sku.charCodeAt(0) : 1));
  const marca = resolveBrand(producto, rand);
  const { primary, svg } = resolveImageUrls(producto);

  const discountPercentages = [15, 20, 25, 27, 30, 35];
  const descuentoPorcentaje =
    producto.descuentoPorcentaje ?? discountPercentages[Math.floor(rand() * discountPercentages.length)];

  const precioAnterior =
    producto.precioAnterior ?? Math.round((producto.precio / (1 - descuentoPorcentaje / 100)) / 100) * 100;

  const rating = producto.rating ?? Number((4.2 + rand() * 0.7).toFixed(1));
  const reviewCount = producto.reviewCount ?? Math.floor(10 + rand() * 55);

  const cuotasOptions = [3, 6, 12];
  const cuotasSinInteres = producto.cuotasSinInteres ?? cuotasOptions[Math.floor(rand() * cuotasOptions.length)];

  const patrocinado = producto.patrocinado ?? (rand() > 0.7);

  return {
    marca,
    imagenUrl: primary,
    fallbackSvg: svg,
    precioAnterior,
    descuentoPorcentaje,
    rating,
    reviewCount,
    cuotasSinInteres,
    patrocinado
  };
}
