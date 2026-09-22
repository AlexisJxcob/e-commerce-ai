# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Primary: homeowners / DIY users in Chile facing a home-repair problem. They describe the problem in colloquial Spanish (e.g. "tengo una fuga en una tubería de PVC") and hire the storefront to diagnose what is needed and assemble the compatible kit of tools, materials, and spare parts — without knowing technical names, SKUs, or categories upfront.

## Product Purpose

Ferretería IA storefront: an AI-assisted hardware-store e-commerce frontend. It lets a non-expert go from a plain-language problem description to a diagnosed kit, then through cart, checkout, and order tracking in one loop. Success means a homeowner resolves their repair with the right compatible products in a single session.

## Positioning

End-to-end commerce anchored by colloquial AI diagnosis: problem description → AI diagnosis + compatible kit → persistent cart → checkout → orders, plus admin catalog management — a neighboring hardware catalog site that only lists products by category/SKU could not truthfully copy the full loop.

## Operating Context

Spanish-language Chilean home-repair context: colloquial problem descriptions, CLP pricing (`clp` pipe), RUT handling (`rut.util`), category carousels for browsing, AI diagnosis + product grid for problem-solving. Authenticated flows: persistent cart, checkout, order history (`/checkout`, `/checkout/resultado`, `/pedidos`, `/carrito` → checkout redirect). Admin flow: catalog/stock management behind `adminGuard` (`/admin`). Backend is the Spring Boot Ferretería IA API proxied at `/api` → `http://localhost:8080` in development (`proxy.conf.json`).

## Capabilities and Constraints

Confirmed functionality (from `src/app` evidence):
- AI assistant: `hero-search` + `asistente.service` (diagnose/query, loading/skeleton, error + retry, result with diagnosis column + product grid, add-kit-to-cart).
- Catalog: home catalog by category with carousels, product detail (`/productos/:id`), category browsing via `catalogo.service`.
- Commerce: persistent cart (`carrito.service`), checkout + resultado, orders (`pedido.service`), auth modal + JWT (`auth.service`, `auth.interceptor`, `error.interceptor`, `authGuard`/`adminGuard`).
- Admin: `AdminComponent` + `admin.service` for product/category management (ADMIN role from backend JWT claim `roles`).
- Stack (existing codebase, not a greenfield choice): Angular 18 + PrimeNG 18 + PrimeIcons, SCSS, standalone components + lazy-loaded routes, RxJS signals-style state.
- Technical constraints: must keep the `/api` backend contract (paginated products, asistente/diagnose, vector `/buscar`, cart, pedidos, auth); Spanish user-facing copy; CLP/RUT locale behavior.
- Explicitly undecided: whether "pros / contractors" become a second primary audience; performance budgets and offline behavior not yet set as product requirements.

## Brand Commitments

Binding name: Ferretería IA. Voice: colloquial Chilean Spanish for problem descriptions, technical Spanish for product data. The incumbent code contains a "Repara.ai" design-system comment/token set in `src/styles.scss` — that is implementation detail, not a binding brand commitment; future visual work may replace it under the Ferretería IA name.

## Evidence on Hand

Real implementation (not to be fabricated around): `src/app/app.routes.ts` (routes), `src/app/features/home/home.component.html` (hero-search → diagnosis + grid → catalog carousels), `src/app/core/models/producto.models.ts` (product/page contracts), `src/app/core/services/` (asistente, catalogo, carrito, pedido, admin), `src/styles.scss` (incumbent token set), `proxy.conf.json` (backend contract). No invented testimonials, customers, benchmarks, pricing, or press exist; future work must not fabricate them.

## Product Principles

1. Plain words in, right kit out — the user never needs to know SKUs or trade jargon.
2. Compatibility over catalog depth — every recommendation must justify why items belong together.
3. One loop to done — diagnosis, cart, checkout, and order status stay in a single comprehensible flow.
4. Trust through transparency — prices in CLP, stock truth, and recoverable errors over dead ends.
5. Admin leverage, not admin burden — catalog and stock work serves the homeowner's success, not its own complexity.
