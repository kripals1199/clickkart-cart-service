# ClickKart Cart Service

The signed-in customer's basket. Service #9 of the platform's 14, port **8091**.

The least authoritative service here, and deliberately so. A cart holds no money, reserves no stock,
and promises nothing. Losing one is an annoyance; losing an order or a payment is an incident.

---

## The cart owns quantities and nothing else

Names, prices, sellers and availability are read live from Product and Inventory every time the cart
is rendered. **None of them is stored.**

That is the exact opposite of what Order Service does one step along, and the contrast is the point:

| | Order | Cart |
|---|---|---|
| What it is | a record of an agreement at a point in time | a list of intentions |
| Price | **snapshotted** — re-reading would rewrite what a customer already paid | **live** — a stored one is just a copy that goes stale |
| Product name | snapshotted — or last year's orders decay into blanks | live |

A customer shown last week's price for something they have not bought yet has simply been
misinformed. A customer shown a different price from the one they were charged has been misled. Live
pricing avoids both.

### The one price that is stored is a change detector

`priceWhenAdded` is not what anyone is charged. Order Service re-prices against the catalog at
checkout, because that is the moment terms are actually agreed — so if Cart froze a price and Order
charged a different one, the customer would see one number and be billed another.

Instead the cart renders the **live** price and uses the stored one only to say *"this went up since
you added it"*. Making the change visible is worth far more than pretending it did not happen.

Incrementing a line deliberately does **not** refresh it. Refreshing on every "+1" would erase the
very change the field exists to surface — quietly, and precisely when the price had just moved.

Compared with `compareTo`, never `equals`: `BigDecimal.equals` says `100.0` differs from `100.00`, so
a scale change coming back from the catalog would be reported to every customer as a price rise that
never happened.

---

## Adding to a cart holds no stock

If it did, browsing would take goods off sale, and a shop whose inventory is consumed by
window-shoppers runs out of things to sell without selling anything.

Availability is shown as a **banded hint** from Inventory's public endpoint — the same one an
anonymous browser calls, which answers `OUT_OF_STOCK` / `LOW` / `IN_STOCK` and never an exact count.
A cart showing "only 2 left" would repeat a number Inventory deliberately withholds.

The truth about stock is established at checkout, by Inventory, once.

---

## Degrading is the right answer here

If Product Service cannot be reached, the cart **still renders** — those lines come back marked
unpriced, and the response carries `pricingDegraded`.

Order Service treats the identical call as required and fails the checkout. Both are right, because
they answer different questions: an order that cannot be priced must not exist, whereas a customer
who cannot see prices for a moment is better served by a visible basket than an error page. Checkout
will refuse properly a step later.

An availability outage is swallowed entirely. Stock is the least important thing on a basket row, and
losing prices because a stock service blinked would trade something the customer needs for something
they merely like to know.

**A delisted item stays in the cart, flagged** rather than vanishing — a line that silently
disappears leaves someone wondering what they had picked. It keeps the cart out of `readyForCheckout`
and out of the subtotal, because quoting a total checkout cannot honour is worse than quoting a
smaller one.

---

## API

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/v1/cart` | priced live |
| `POST` | `/api/v1/cart/items` | add, or **increase** one already there |
| `PUT` | `/api/v1/cart/items/{sku}` | set an absolute quantity; `0` removes |
| `DELETE` | `/api/v1/cart/items/{sku}` | remove a line |
| `DELETE` | `/api/v1/cart` | empty it |

Every route acts on the token's own subject — there is no user id in any customer-facing path, so
touching someone else's cart is not expressible. No role is required either: any signed-in account
may have a basket, a seller who wants to buy something included.

Every write returns the **whole cart**, because a cart page redraws its total and its checkout button
after every click, and returning a fragment would force a second GET each time — or tempt a client
into computing the total itself and disagreeing with the server about what things cost.

**Add increments; checkout refuses duplicates.** The difference is deliberate. A checkout request is
a statement of exactly what to buy, so merging two lines would answer a different request from the
one sent. Pressing "add to cart" twice is not a statement — it is a person asking for another one.

### Internal API

| Method | Path | Caller |
|---|---|---|
| `GET` | `/internal/v1/carts/{userPublicId}` | Order — read the basket at checkout |
| `DELETE` | `/internal/v1/carts/{userPublicId}` | Order — empty it once it became an order |

**No prices cross this boundary**, and there is no way for another service to *add* to a basket. Only
the person it belongs to may put things in it; an internal API that could add lines would be a way to
put items in a stranger's cart from inside the cluster.

---

## How checkout uses it

`POST /api/v1/orders` with **no** `items` means "order what is in my cart". Order Service reads the
list here, prices every line against the catalog itself, holds stock as usual, and empties the basket
afterwards.

Only the *list* comes from the cart. That is what makes it safe for something the customer can edit
to be an input to checkout at all — anything it supplied that reached the money path would be a
number the customer chose.

Reading the basket is **required** (an unreadable cart is not an empty one, and guessing is not an
option). Emptying it is **best-effort**: by then the order exists and its stock is held, so failing
the checkout over a housekeeping call would turn a completed purchase into an error the customer
cannot fix.

---

## Other decisions

**No audit trail.** Every other service that writes anything here reports to Audit Log Service. This
one does not, on purpose: auditing every add-to-cart would bury the events that matter — payments,
cancellations, stock movements — under the noisiest, least consequential clicks on the platform.

**A GET never creates a cart row.** Otherwise every health probe and idle click would leave one
behind to be pruned later.

**Emptying deletes the row** rather than leaving an empty cart, which is indistinguishable from a
cart nobody has.

**Abandoned carts are pruned after 90 days.** Storage is the least of the reasons — a cart records
what somebody was thinking of buying, which is personal data nobody agreed to have kept indefinitely
and which stops being useful to them long before it stops being a liability for the shop. The pruner
re-checks the deadline inside the transaction, so a sweep that started an hour ago cannot throw away
a basket somebody is filling right now.

---

## Running it

Needs Config Server, Eureka, **Product Service**, Postgres and the shared revocation Redis. Inventory
Service is optional — without it, availability hints are simply absent.

```bash
docker compose -f docker-compose.dev-infra.yml -f docker-compose.app-tier.yml up -d cart-service
```

### Tests

```bash
mvn verify
```

`verify`, not `test` — that enforces the coverage gate (floor 0.65 against measured 0.66).

> The standing caveat, unchanged since Inventory Service lost a reservation status to it: **a mocked
> repository proves nothing about persistence.** What these tests pin is decision-making — what the
> cart asks the catalog, what it refuses, and above all what it does when a price moves.

---

## Configuration

| Property | Purpose |
|---|---|
| `cart.max-distinct-items` | Lines per cart — a blast-radius limit, since each one is a catalog call per read |
| `cart.max-quantity-per-item` | Units of any one SKU; mirrors Order's per-line cap so a full cart can still check out |
| `cart.abandoned-after-days` | How long an untouched cart survives |
| `cart.prune-interval-ms` | Pruner `fixedDelay` (hourly — nothing here is time-critical) |
| `cart.internal-api-key` | Guards the checkout seam |
| `cart.product-service-api-key` | Presented when pricing a SKU |

**Known limitation: no guest carts.** A basket requires a signed-in account, so an anonymous browser
cannot collect items before registering. Supporting that needs a cart token and a merge-on-login
flow, neither of which exists — and a half-built merge that silently drops items would be worse than
not offering it.

Readiness covers `readinessState,db,redis`.
