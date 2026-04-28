# k6 Smoke

These scripts are for local or staging verification only.

Do not run them against production.

## Checkout smoke

This script logs in, clears the cart, adds one product, submits checkout, and cancels the unpaid order to restore stock.

Default command:

```bash
k6 run k6/checkout-smoke.js
```

Custom environment:

```bash
BASE_URL=http://127.0.0.1:8080/api ACCOUNT=user@example.com PASSWORD=123456 k6 run k6/checkout-smoke.js
```

Optional low-concurrency loop:

```bash
VUS=1 ITERATIONS=5 k6 run k6/checkout-smoke.js
```
