# Ecommerce Core Correctness

Use this reference for product, cart, checkout, inventory, payment, coupon, wallet, review, and after-sales work.

## Product and SKU

Source of truth should be backend/database, not frontend constants or knowledge base.

Check:

- product id/name/category/brand;
- SKU id belongs to product id;
- SKU price overrides base price when selected;
- stock is tracked per SKU when variants exist;
- disabled/out-of-stock options are visible but not purchasable;
- aliases and model normalization do not overmatch ambiguous terms.

## Cart

Cart should validate:

- logged-in user ownership;
- product exists and is active;
- SKU belongs to product;
- quantity is positive and bounded;
- adding to cart does not reduce stock.

## Checkout and Inventory

Overselling prevention belongs in backend transaction logic.

Preferred pattern:

```sql
UPDATE product_sku
SET stock = stock - :qty
WHERE id = :skuId AND stock >= :qty;
```

Then require affected rows to equal 1. If not, fail the order/payment step.

Avoid:

- frontend-only stock checks;
- read stock then update later without transaction/condition;
- reducing stock at add-to-cart time;
- allowing SKU id from one product with another product.

## Coupons

Coupon use should check:

- ownership;
- status;
- validity time range;
- minimum spend threshold;
- applicable product/category if supported;
- already used state;
- discount not exceeding payable amount.

Frontend should display unavailable coupons with a small reason such as "minimum spend not reached", and prevent selection.

## Wallet and Balance

Balance payment should be atomic:

- verify order belongs to user;
- verify order is payable;
- verify wallet balance >= amount;
- deduct balance once;
- create transaction record;
- mark order paid;
- prevent duplicate payment.

Registration gift balance and existing-user grants should be handled by backend migration/service logic, not frontend display hacks.

## Reviews and After-Sales

Reviews should be tied to completed or eligible order items.

After-sales should validate:

- user owns order;
- order item exists;
- status allows application;
- repeated applications rules;
- refund/return reason and evidence;
- policy answer in AI客服 stays generic unless tied to a user's actual case.
