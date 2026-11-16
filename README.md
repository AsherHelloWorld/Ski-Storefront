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

_To be completed in step 7._

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
