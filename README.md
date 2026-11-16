# Ski Store

COSC 304 Lab 7 (development stream): an online store that sells skis, built with
Java/JSP on Tomcat 11 and SQL Server.

## Submitted by

| Name | Student number |
|------|----------------|
| Asher Nathan | 40317612 |
|  |  |

## Required pages

| Page | What it does |
|------|--------------|
| `shop/listorder.jsp` | Lists every order with its customer, total and the skis in it |
| `shop/listprod.jsp` | Search skis by name and add them to the cart |
| `shop/order.jsp` | Validates the customer, saves the order and its items, shows a receipt |

## Bonus features

Bonus marks requested for the following (the assignment's maximum of 10 applies):

| Bonus | Marks | Where | What we did |
|-------|-------|-------|-------------|
| Remove items / change quantities in the cart | +5 | `showcart.jsp`, `updatecart.jsp` | Every cart line has a quantity box with **Update** and a **Remove** button, plus an **Empty cart** button. A quantity of 0 removes the line; anything other than 0–99 is rejected with a message. Changes are POST-only, then redirect back to the cart. |
| Validate the customer's password when ordering | +5 | `checkout.jsp`, `order.jsp` | Checkout asks for the customer ID **and password**; `order.jsp` checks the password (exact, case-sensitive) against the `customer` table before saving anything. A wrong or missing password saves nothing and keeps the cart. |
| Page header with links | +2 | `header.jsp` (included on every page) | Store name plus links to **Shop Skis** (`listprod.jsp`), **Orders** (`listorder.jsp`) and **Cart** (`showcart.jsp`, with the number of skis in the cart). The current page is highlighted. |
| Product page formatting + category filter | +3 | `listprod.jsp` | Category dropdown (loaded from the `category` table) that combines with the name search; each ski shows its image, specs, category, price and an **Add to Cart** button; result count and a heading naming the category/search. |
| Improved cart page formatting | +3 | `showcart.jsp` | Styled cart table with subtotals, item count, order total, **Check Out** and **Continue Shopping** buttons, and a friendly empty-cart page. |

Other improvements (not claimed for marks): a shared stylesheet (`css/store.css`), HTML-escaping of
all database text, orders saved in a single transaction (rolled back on any failure), and order item
prices taken from the database rather than the editable cart link.

## Running it

```bash
docker compose up -d --build        # site: http://localhost/shop/
docker compose down                 # stop (keeps the database)
```

`shop/` is mounted live into Tomcat: edit a JSP and refresh. The database is loaded from
`ddl/SQLServer_orderdb.ddl`; **Database Setup** (`loaddata.jsp`) resets it to that data.

## Tests

```bash
docker compose --profile test run --rm tests
```

Runs the JUnit suite in `tests/` against a separate copy of the site and database
(`test-app`, `ski-test-sqlserver`), so test runs never change the real site's data.
The run fails if any test fails or if JSP line coverage is below 90%.
Coverage report: `tests/target/coverage/jsp-lines.txt`.
