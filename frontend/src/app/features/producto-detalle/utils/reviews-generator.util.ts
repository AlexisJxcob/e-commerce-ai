import { Producto } from '../../../core/models/producto.models';
import {
  CategoriaVisualInfo,
  DistribucionEstrellas,
  ResenaProducto,
  ResumenCalificaciones
} from '../models/producto-detalle.models';

function createPrng(seed: number): () => number {
  let s = (Math.abs(seed) * 1103515245 + 12345) % 2147483647;
  return () => {
    s = (s * 16807 + 11) % 2147483647;
    return (s - 1) / 2147483646;
  };
}

const NOMBRES = [
  'Carlos M.',
  'Rodrigo H.',
  'Marcela S.',
  'Esteban G.',
  'Gonzalo V.',
  'Patricio L.',
  'Camila T.',
  'Javier P.',
  'Fernando B.',
  'Andrés K.'
];

const COMENTARIOS_HERRAMIENTAS = [
  'Excelente calidad y agarre firme. La usé para desmontar unas piezas apretadas y no se falseó en absoluto.',
  'Herramienta muy confiable, peso equilibrado y terminación sólida. Vale totalmente la pena.',
  'Cumple al 100% con la descripción técnica. Ideal para trabajos de mantenimiento en casa y taller.',
  'Material robusto y ergonómico. Llegó en perfectas condiciones y lista para usar.'
];

const COMENTARIOS_PLOMERIA = [
  'Ajustó perfecto en la tubería existente. Cero filtraciones tras presurizar la línea.',
  'Material de primera calidad. Se nota el grosor de pared adecuado para soportar presión hidráulica.',
  'Excelente repuesto para reparar la fuga en el baño. Fácil de acoplar con los adhesivos estándar.',
  'Solucionó el problema de inmediato. Las roscas y uniones vienen con las medidas exactas.'
];

const COMENTARIOS_GENERALES = [
  'Muy buena relación precio-calidad. Es justo lo que necesitaba para el arreglo que estaba haciendo.',
  'Llegó rápido y bien protegido. Funcionó impecable desde el primer minuto.',
  'Totalmente recomendado. Buen producto para salir de apuros con una reparación casera.',
  'Se nota la durabilidad del material. Calzó exacto y me evitó tener que llamar a un técnico.'
];

export function generarResenas(producto: Producto): ResenaProducto[] {
  const nextRand = createPrng(producto.id);
  const pool = seleccionarPoolComentarios(producto);
  const count = 3 + Math.floor(nextRand() * 2); // 3 o 4 reseñas

  const resenas: ResenaProducto[] = [];
  const tiempos = ['Hace 3 días', 'Hace 1 semana', 'Hace 2 semanas', 'Hace 1 mes'];

  const indices = [0, 1, 2, 3];
  for (let i = indices.length - 1; i > 0; i--) {
    const j = Math.floor(nextRand() * (i + 1));
    [indices[i], indices[j]] = [indices[j], indices[i]];
  }

  for (let i = 0; i < count; i++) {
    const autorIndex = Math.floor(nextRand() * NOMBRES.length);
    const calificacion = nextRand() > 0.25 ? 5 : 4;
    resenas.push({
      id: producto.id * 100 + i + 1,
      autor: NOMBRES[(autorIndex + i) % NOMBRES.length],
      calificacion,
      fecha: tiempos[i % tiempos.length],
      comentario: pool[indices[i % pool.length]],
      compraVerificada: true
    });
  }

  return resenas;
}

export function generarResumenCalificaciones(producto: Producto): ResumenCalificaciones {
  const nextRand = createPrng(producto.id * 7 + 13);
  const total = 18 + Math.floor(nextRand() * 28); // 18 - 45 reseñas

  const p5 = 0.72 + nextRand() * 0.16;
  const p4 = 0.12 + nextRand() * 0.1;
  const cant5 = Math.round(total * p5);
  const cant4 = Math.round(total * p4);
  const cant3 = Math.max(0, total - cant5 - cant4);

  const suma = cant5 * 5 + cant4 * 4 + cant3 * 3;
  const promedio = Number((suma / total).toFixed(1));

  const distribucion: DistribucionEstrellas[] = [
    { estrellas: 5, porcentaje: Math.round((cant5 / total) * 100), cantidad: cant5 },
    { estrellas: 4, porcentaje: Math.round((cant4 / total) * 100), cantidad: cant4 },
    { estrellas: 3, porcentaje: Math.round((cant3 / total) * 100), cantidad: cant3 },
    { estrellas: 2, porcentaje: 0, cantidad: 0 },
    { estrellas: 1, porcentaje: 0, cantidad: 0 }
  ];

  return {
    promedio,
    totalResenas: total,
    distribucion
  };
}

function seleccionarPoolComentarios(producto: Producto): string[] {
  const texto = `${producto.nombre} ${producto.descripcionTecnica || ''} ${producto.descripcionColoquial || ''}`.toLowerCase();
  if (texto.includes('pvc') || texto.includes('tubo') || texto.includes('agua') || texto.includes('llave paso') || texto.includes('fuga') || texto.includes('cañería')) {
    return COMENTARIOS_PLOMERIA;
  }
  if (texto.includes('martillo') || texto.includes('alicate') || texto.includes('llave') || texto.includes('destornillador') || texto.includes('sierra')) {
    return COMENTARIOS_HERRAMIENTAS;
  }
  return COMENTARIOS_GENERALES;
}

export function obtenerIconoVisual(producto: Producto): CategoriaVisualInfo {
  const texto = `${producto.nombre} ${producto.descripcionTecnica || ''}`.toLowerCase();
  if (texto.includes('pvc') || texto.includes('tubo') || texto.includes('agua') || texto.includes('hidrául') || texto.includes('fuga')) {
    return { icono: 'pi pi-cloud', etiqueta: 'Fontanería & Conducción' };
  }
  if (texto.includes('martillo') || texto.includes('alicate') || texto.includes('destornillador') || texto.includes('herramienta') || texto.includes('llave')) {
    return { icono: 'pi pi-wrench', etiqueta: 'Herramientas Manuales' };
  }
  if (texto.includes('cable') || texto.includes('aisladora') || texto.includes('eléctr') || texto.includes('voltaje')) {
    return { icono: 'pi pi-bolt', etiqueta: 'Electricidad & Conexión' };
  }
  if (texto.includes('tornillo') || texto.includes('perno') || texto.includes('tarugo') || texto.includes('adhesivo') || texto.includes('pegamento')) {
    return { icono: 'pi pi-cog', etiqueta: 'Fijación & Sellado' };
  }
  return { icono: 'pi pi-box', etiqueta: 'Ferretería & Repuestos' };
}
