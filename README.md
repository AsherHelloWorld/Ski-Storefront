# Ski Store

COSC 304 Lab 7 (development stream): an online store that sells skis, built with
Java/JSP on Tomcat 11 and SQL Server.

## Submitted by

| Name | Student number |
|------|----------------|
| Asher Nathan | 40317612 |

## Required pages

| Page | What it does |
|------|--------------|
| `shop/listorder.jsp` | Lists every order with its customer, total and the skis in it |
| `shop/listprod.jsp` | Search skis by name and add them to the cart |
| `shop/order.jsp` | Validates the customer, saves the order and its items, shows a receipt |

## Bonus features

Bonus marks requested for the following (the assignment's maximum of 10 applies):

| Bonus | Marks | Where | What it does |
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

Requires Docker Desktop. Ports 80 and 1433 are the same as the course's lab 7 stack, so stop
that first (`docker compose down` in its folder).

```bash
docker compose up -d --build        # site: http://localhost/shop/
docker compose down                 # stop (keeps the database)
```

The first start downloads SQL Server and Tomcat and loads the database, which takes a few
minutes (longer on Apple Silicon, where SQL Server runs under emulation).

## Checking out

Checkout asks for a customer ID **and password** (bonus). The sample customers are:

| Customer ID | Name | Password |
|-------------|------|----------|
| 1 | Arnold Anderson | `304Arnold!` |
| 2 | Bobby Brown | `304Bobby!` |
| 3 | Candace Cole | `304Candace!` |
| 4 | Darren Doe | `304Darren!` |
| 5 | Elizabeth Elliott | `304Beth!` |

## Data

The database is loaded from `ddl/SQLServer_orderdb.ddl`. It uses the course schema unchanged;
only the sample rows differ: 30 skis in 6 categories (All-Mountain, Powder, Carving,
Park/Freestyle, Touring, Kids), with ski specs in `productDesc` and a grey placeholder image.
The course's 5 sample customers are kept, and the 5 sample orders are rebuilt with skis.

**Database Setup** (`loaddata.jsp`, linked from the home page) shows row counts and has a
**Reset Database** button that reloads the DDL, removing any orders placed since.

## Project layout

| Path | Contents |
|------|----------|
| `shop/` | The web app (JSP pages, `css/`, `img/`) |
| `ddl/SQLServer_orderdb.ddl` | Schema and ski sample data |
| `lib/` | SQL Server JDBC driver |
| `Dockerfile`, `docker-compose.yml` | Tomcat + SQL Server setup |
| `tests/` | JUnit test suite and coverage tooling |

## Tests

```bash
docker compose --profile test run --rm tests
```

Runs the JUnit suite in `tests/` against a separate copy of the site and database
(`test-app`, `ski-test-sqlserver`), so test runs never change the real site's data.
The run fails if any test fails or if JSP line coverage is below 90%.
Coverage report: `tests/target/coverage/jsp-lines.txt`.
