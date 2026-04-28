# Reviews And Wallet Balance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import the missing seeded product reviews and add a practical user wallet balance payment flow.

**Architecture:** Keep reviews as seed data in SQL so existing product review APIs and UI work unchanged. Add wallet tables and a focused wallet service, then let `PaymentServiceImpl` handle `channel=balance` as an immediate internal payment method.

**Tech Stack:** Spring Boot 3, MyBatis XML mappers, MySQL, JUnit/Mockito, Vue 3, Pinia, Axios.

---

### Task 1: Backend Wallet Payment

**Files:**
- Create: `back/src/main/java/com/web/pojo/UserWallet.java`
- Create: `back/src/main/java/com/web/pojo/WalletTransaction.java`
- Create: `back/src/main/java/com/web/mapper/UserWalletMapper.java`
- Create: `back/src/main/java/com/web/mapper/WalletTransactionMapper.java`
- Create: `back/src/main/resources/mapper/UserWalletMapper.xml`
- Create: `back/src/main/resources/mapper/WalletTransactionMapper.xml`
- Create: `back/src/main/java/com/web/service/WalletService.java`
- Create: `back/src/main/java/com/web/service/impl/WalletServiceImpl.java`
- Modify: `back/src/main/java/com/web/service/impl/PaymentServiceImpl.java`
- Modify: `back/src/main/java/com/web/controller/UserController.java`
- Test: `back/src/test/java/com/web/service/impl/PaymentServiceImplTest.java`

- [x] Write failing tests for balance payment success and insufficient balance.
- [x] Implement wallet debit with row-level balance update and transaction records.
- [x] Add `GET /v1/me/wallet` for balance display.
- [x] Run Maven tests.

### Task 2: Review Seed Data

**Files:**
- Modify: `back/sql/init_db.sql`
- Create: `back/sql/seed_reviews.sql`

- [x] Replace the 2-row review seed with the 340-row review data from `C:\Users\Administrator\Desktop\init_db.sql`.
- [x] Make `seed_reviews.sql` safe to run on an existing database by deleting seed review IDs/content scope before inserting.
- [x] Keep UTF-8 Chinese content intact.

### Task 3: Frontend Wallet UI

**Files:**
- Create: `frontend/src/stores/wallet.ts`
- Modify: `frontend/src/views/CashierView.vue`
- Modify: `frontend/src/views/MeView.vue`

- [x] Add wallet store to fetch current balance.
- [x] Add balance payment option in cashier and submit `channel: "balance"`.
- [x] Show wallet balance in the user center.
- [x] Run frontend type/build checks.

### Verification

- [x] `mvn test` from `back`.
- [x] `npm run build` from `frontend`.
- [ ] Manual smoke: fetch product reviews for product 1 and pay a pending order with balance.
