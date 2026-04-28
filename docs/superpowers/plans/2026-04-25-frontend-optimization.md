# Frontend Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify data mapping, product imagery, interaction consistency, and visual quality across the Vue frontend without changing unrelated backend behavior.

**Architecture:** Centralize shared frontend concerns first, especially product media resolution and page-state handling, then update each page group to consume the same shared behavior. Keep page responsibilities local, but move repeated mapping and fallback logic into `src/lib` helpers so list/detail/cart/order/favorite flows render the same product data and image everywhere.

**Tech Stack:** Vue 3, TypeScript, Vite, Pinia, Vue Router, Axios

---

## File Structure

- Modify: `frontend/src/lib/productCovers.ts`
  - Normalize product cover rules and remove obviously wrong mappings.
- Modify: `frontend/src/lib/productMapper.ts`
  - Extend backend-to-frontend mapping so list/detail/order/cart/favorite flows can reuse one product media strategy.
- Create: `frontend/src/lib/productMedia.ts`
  - Shared helpers for resolving product image arrays, primary cover, and safe fallbacks from backend payloads.
- Create: `frontend/src/lib/text.ts`
  - Shared helper for safe string normalization where current pages repeat brittle conversions.
- Modify: `frontend/src/views/ProductDetailView.vue`
  - Reuse shared media resolver, improve gallery/fallback/related state.
- Modify: `frontend/src/views/HomeView.vue`
  - Align product cards and state handling with shared mapping.
- Modify: `frontend/src/views/CategoryView.vue`
  - Align filtering list rendering and product card behavior with shared mapping.
- Modify: `frontend/src/views/SearchView.vue`
  - Align search result rendering and empty/error handling.
- Modify: `frontend/src/views/CartView.vue`
  - Ensure cart thumbnails and product links stay consistent with shared media mapping.
- Modify: `frontend/src/views/CheckoutView.vue`
  - Ensure checkout item summaries and address/payment feedback are consistent.
- Modify: `frontend/src/views/CashierView.vue`
  - Improve state clarity and visual hierarchy around pending payment flow.
- Modify: `frontend/src/views/OrdersView.vue`
  - Use real item cover fallback logic instead of hardcoded demo imagery.
- Modify: `frontend/src/views/OrderDetailView.vue`
  - Use shared media fallback and cleaner order item presentation.
- Modify: `frontend/src/views/FavoritesView.vue`
  - Align favorite cards with product card behavior and action consistency.
- Modify: `frontend/src/views/MeView.vue`
  - Clean major copy/status presentation issues.
- Modify: `frontend/src/views/MessagesView.vue`
  - Improve unread/read hierarchy and clickable behavior clarity.
- Modify: `frontend/src/views/HelpCenterView.vue`
  - Improve copy and card CTA consistency.
- Modify: `frontend/src/views/FaqView.vue`
  - Improve tab/search/accordion presentation and copy quality.
- Modify: `frontend/src/App.vue`
  - Clean top-level shell text and layout consistency if needed.
- Modify: `frontend/src/router/index.ts`
  - Correct page titles/descriptions that currently contain broken text.

### Task 1: Shared Product Media Layer

**Files:**
- Create: `frontend/src/lib/productMedia.ts`
- Modify: `frontend/src/lib/productMapper.ts`
- Modify: `frontend/src/lib/productCovers.ts`

- [ ] **Step 1: Define shared resolver shape**

Document and implement these exports:

```ts
export type ProductMediaInput = Record<string, unknown>

export const resolveProductMedia = (
  raw: ProductMediaInput,
  fallbackTitle: string,
  hint?: ProductCategoryHint,
  fallbackId?: string,
): string[] => { /* returns non-empty image array */ }

export const resolvePrimaryProductCover = (
  raw: ProductMediaInput,
  fallbackTitle: string,
  hint?: ProductCategoryHint,
  fallbackId?: string,
): string => { /* returns first image */ }
```

- [ ] **Step 2: Remove clearly wrong cover rules**

Specifically remove or replace mappings that cause mismatched hero images, especially phone mappings that point to unrelated or exaggerated Apple assets. Keep keyword coverage but prefer category-correct local/static or neutral product imagery.

- [ ] **Step 3: Update backend mapper**

Make `mapBackendProduct()` consume the shared primary cover resolver instead of inlining fallback logic:

```ts
const cover = resolvePrimaryProductCover(raw, title, options.hint, id)
```

Keep return shape backward compatible.

- [ ] **Step 4: Sanity-check call sites**

Search all `mapBackendProduct(` and `/product_` image fallbacks. Replace direct fallback logic where the new resolver should own the behavior.

### Task 2: Product Browsing Pages

**Files:**
- Modify: `frontend/src/views/HomeView.vue`
- Modify: `frontend/src/views/CategoryView.vue`
- Modify: `frontend/src/views/SearchView.vue`
- Modify: `frontend/src/views/ProductDetailView.vue`

- [ ] **Step 1: Align product list cards**

Ensure home/category/search all render:
- same cover strategy
- same title truncation behavior
- same price/rating/tag priority
- same click/add-to-cart behavior

- [ ] **Step 2: Fix detail image mismatch**

Update `ProductDetailView.vue` so media resolution order is:
1. backend `media`
2. backend `cover` / image-like fields
3. shared keyword/category mapping
4. local static fallback

Do not fall back directly to the hardcoded Figma placeholder if a mapped real product cover exists.

- [ ] **Step 3: Improve detail-page UX**

Refine these areas while keeping current behavior intact:
- gallery thumbnail selection
- missing/empty product state
- disabled SKU messaging
- quantity/stock feedback
- favorite/cart action clarity

- [ ] **Step 4: Keep search/category feedback consistent**

Normalize loading, empty, and error presentation so browsing pages do not mix mismatched copy or broken-text placeholders.

### Task 3: Transaction Flow

**Files:**
- Modify: `frontend/src/views/CartView.vue`
- Modify: `frontend/src/views/CheckoutView.vue`
- Modify: `frontend/src/views/CashierView.vue`

- [ ] **Step 1: Fix cart item consistency**

Ensure cart item cards use:
- linked product title/image
- consistent quantity controls
- safe image fallback
- cleaner SKU text for default/non-default variants

- [ ] **Step 2: Improve checkout summaries**

Ensure checkout item list, coupon summary, address section, and submit states have cleaner copy and clearer totals without changing backend request shape.

- [ ] **Step 3: Improve cashier clarity**

Keep the same pay flow, but tighten:
- empty/loading states
- amount prominence
- countdown readability
- selected payment method emphasis

### Task 4: Orders and User Pages

**Files:**
- Modify: `frontend/src/views/OrdersView.vue`
- Modify: `frontend/src/views/OrderDetailView.vue`
- Modify: `frontend/src/views/FavoritesView.vue`
- Modify: `frontend/src/views/MeView.vue`
- Modify: `frontend/src/views/MessagesView.vue`

- [ ] **Step 1: Remove hardcoded demo order imagery**

Replace order list/detail fallback images with shared product cover/media logic so ordered items show the same image the user saw elsewhere.

- [ ] **Step 2: Align favorite cards**

Keep favorite actions intact but align card hierarchy with the browsing pages and prevent inconsistent price/icon/text presentation.

- [ ] **Step 3: Clean user-center states**

Improve `MeView.vue` and `MessagesView.vue` text clarity, badges, entry affordances, and unread emphasis while keeping routes and store behavior unchanged.

### Task 5: Help, FAQ, App Shell, Metadata

**Files:**
- Modify: `frontend/src/views/HelpCenterView.vue`
- Modify: `frontend/src/views/FaqView.vue`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/router/index.ts`

- [ ] **Step 1: Clean broken page copy**

Replace obviously broken or garbled visible strings in the touched shell/help/router metadata paths with readable Chinese copy.

- [ ] **Step 2: Tighten help/FAQ interaction**

Keep current features but improve:
- CTA readability
- section hierarchy
- FAQ category/search affordance
- accordion scanability

- [ ] **Step 3: Align shell/meta quality**

Fix top app shell labels and route titles/descriptions so browser title/meta no longer expose broken text.

### Task 6: Verification

**Files:**
- Verify: `frontend/package.json`

- [ ] **Step 1: Run frontend build**

Run:

```bash
npm run build
```

from:

```bash
frontend
```

Expected:
- `vue-tsc -b` passes
- `vite build` exits `0`

- [ ] **Step 2: Spot-check changed flows**

Manually confirm these routes after build if a local preview/dev server is available:
- `/`
- `/category`
- `/search`
- `/products/:id`
- `/cart`
- `/checkout`
- `/orders`
- `/favorites`
- `/help-center`
- `/faq`

- [ ] **Step 3: Final consistency sweep**

Before claiming completion, re-check:
- no touched page still uses clearly wrong demo product image where real mapped image exists
- no touched route title/visible shell text remains garbled
- no changed page breaks navigation or primary CTA flow
