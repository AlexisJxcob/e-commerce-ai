# Product Categories Specification

## Purpose

Catalog categorization: a `Categoria` resource, a nullable `categoria` FK on `productos`, and the rule that product create/update requires an existing category. Category backfill onto existing products is out of scope (Bloque 4). Embedding generation and all catalog search modes stay untouched.

## Requirements

### Requirement: Categoria CRUD with ADMIN-only mutations

The system MUST expose CRUD for `Categoria` under `/v1/categorias`. Reads MUST be public; POST, PUT and DELETE MUST require role ADMIN. `nombre` MUST be unique and validated with Spanish constraint messages. Deleting a categoria referenced by at least one product MUST return 409 (conflict, Spanish message) and MUST NOT cascade to products.

#### Scenario: Create, read, update and delete a category

- GIVEN an authenticated ADMIN
- WHEN POST /v1/categorias with `{nombre, descripcion}`
- THEN 201 and the categoria is retrievable by id
- AND PUT and DELETE on that categoria return 200 and 204 respectively

#### Scenario: Duplicate category name

- GIVEN an existing categoria named "Fijaciones"
- WHEN an ADMIN POSTs another categoria with the same nombre
- THEN 409 conflict with a Spanish message

#### Scenario: Delete a referenced category

- GIVEN a categoria referenced by one or more productos
- WHEN an ADMIN DELETEs that categoria
- THEN 409 conflict and neither the categoria nor the productos change

#### Scenario: Unauthenticated category mutation

- GIVEN no valid JWT in the request
- WHEN POST /v1/categorias
- THEN 403

### Requirement: Producto requires an existing categoria

The system MUST store `categoria` as a nullable FK column on `productos`, leaving existing rows NULL (no backfill). Product create and full update MUST require a non-null `categoriaId` referencing an existing categoria: missing categoriaId is a 400 field validation error with a Spanish message; unknown categoriaId is a 404 with a Spanish message.

#### Scenario: Create product without categoria

- GIVEN a product body with no categoriaId
- WHEN an ADMIN POSTs /v1/productos
- THEN 400 with a field error on categoriaId

#### Scenario: Create product with unknown categoriaId

- GIVEN a categoriaId that does not exist
- WHEN an ADMIN POSTs /v1/productos
- THEN 404 with a Spanish message

#### Scenario: Legacy product without categoria stays valid

- GIVEN an existing product whose categoria is NULL
- WHEN GET /v1/productos/{id}
- THEN 200 with the product returned and categoria absent/null
- AND keyword, vector and AI search still return that product

### Requirement: Categorization leaves search and embeddings untouched

Assigning or changing a product's categoria MUST NOT change embedding generation (text source `nombre + descripcionColoquial`) nor the behavior of keyword, vectorial or AI-assisted search.

#### Scenario: Update only reassigns categoria

- GIVEN a product with an existing embedding
- WHEN the product is updated changing only its categoria
- THEN the resulting embedding equals the one generated from `nombre + descripcionColoquial` alone
- AND no re-index requirement is introduced by the categoria change
