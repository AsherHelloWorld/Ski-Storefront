<%@ page import="java.sql.*" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="jdbc.jsp" %>
<%@ include file="util.jsp" %>
<%
// =====================================================================================
// Part 1: process the order (no HTML yet). The outcome is shown in Part 2, after the
// cart has been cleared, so the header's cart count is already correct on the receipt.
// =====================================================================================
NumberFormat currFormat = NumberFormat.getCurrencyInstance(Locale.CANADA);

String errorTitle = null;      // set when the order is refused or fails
String errorMessage = null;    // (HTML-safe text)
String errorLink = null;
String errorLinkText = null;

int orderId = 0;
BigDecimal total = BigDecimal.ZERO;
List<Object[]> receipt = new ArrayList<>();   // productId, name, quantity, price, subtotal
int customerId = -1;
String customerName = "";

// Get customer id and password (bonus: the password must match the customer's)
String custId = request.getParameter("customerId");
custId = (custId == null) ? "" : custId.trim();
String password = request.getParameter("password");

@SuppressWarnings({"unchecked"})
HashMap<String, ArrayList<Object>> productList =
    (HashMap<String, ArrayList<Object>>) session.getAttribute("productList");

// ---- 1. The customer id must be a whole number ----
try
{
    customerId = Integer.parseInt(custId);
}
catch (NumberFormatException e)
{
    customerId = -1;   // rejected below
}

if (customerId < 1)
{
    errorTitle = "Invalid customer id";
    errorMessage = "Invalid customer id &quot;" + escapeHtml(custId)
                 + "&quot;. Please go back and enter the number of an existing customer.";
    errorLink = "checkout.jsp";
    errorLinkText = "Back to checkout";
}
// ---- 2. The cart must have something in it ----
else if (productList == null || productList.isEmpty())
{
    errorTitle = "Your shopping cart is empty!";
    errorMessage = "Your shopping cart is empty. Add some skis before checking out.";
    errorLink = "listprod.jsp";
    errorLinkText = "Shop for skis";
}
else
{
    // Cart items, sorted by product id so the receipt is in a stable order.
    // Each entry: 0 = id, 1 = name, 2 = price, 3 = quantity
    List<ArrayList<Object>> items = new ArrayList<>(productList.values());
    items.sort(Comparator.comparing((ArrayList<Object> p) -> p.get(0).toString()));

    try (Connection con = getConnection())
    {
        // ---- 3. The customer must exist ----
        String address = null, city = null, state = null, postalCode = null, country = null;
        String storedPassword = null;
        boolean customerFound;
        try (PreparedStatement custStmt = con.prepareStatement(
                "SELECT firstName, lastName, address, city, state, postalCode, country, password "
              + "FROM customer WHERE customerId = ?"))
        {
            custStmt.setInt(1, customerId);
            try (ResultSet rst = custStmt.executeQuery())
            {
                customerFound = rst.next();
                if (customerFound)
                {
                    customerName = rst.getString("firstName") + " " + rst.getString("lastName");
                    address = rst.getString("address");
                    city = rst.getString("city");
                    state = rst.getString("state");
                    postalCode = rst.getString("postalCode");
                    country = rst.getString("country");
                    storedPassword = rst.getString("password");
                }
            }
        }

        if (!customerFound)
        {
            errorTitle = "Invalid customer id";
            errorMessage = "Invalid customer id &quot;" + customerId
                         + "&quot;: no customer has that number. Please go back and try again.";
            errorLink = "checkout.jsp";
            errorLinkText = "Back to checkout";
        }
        // ---- 4. Bonus: the password must match (compared in Java: exact and case-sensitive) ----
        else if (password == null || storedPassword == null || !password.equals(storedPassword))
        {
            errorTitle = "Invalid password";
            errorMessage = "Invalid password for customer " + customerId
                         + ". Please go back and try again.";
            errorLink = "checkout.jsp";
            errorLinkText = "Back to checkout";
        }
        else
        {
            // ---- 5. Save the order: all of it, or none of it ----
            // One transaction, so a failure part-way (e.g. an unknown product) leaves no half order.
            con.setAutoCommit(false);
            try
            {
                // Insert the order summary and get its auto-generated id.
                String insertOrder =
                      "INSERT INTO ordersummary (orderDate, totalAmount, shiptoAddress, shiptoCity, "
                    + "shiptoState, shiptoPostalCode, shiptoCountry, customerId) "
                    + "VALUES (?, 0, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = con.prepareStatement(insertOrder, Statement.RETURN_GENERATED_KEYS))
                {
                    pstmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
                    pstmt.setString(2, address);
                    pstmt.setString(3, city);
                    pstmt.setString(4, state);
                    pstmt.setString(5, postalCode);
                    pstmt.setString(6, country);
                    pstmt.setInt(7, customerId);
                    pstmt.executeUpdate();

                    try (ResultSet keys = pstmt.getGeneratedKeys())
                    {
                        keys.next();
                        orderId = keys.getInt(1);
                    }
                }

                // Store each product in the cart. The price comes from the database, because
                // the cart's price arrived in a URL that a shopper could have edited.
                try (PreparedStatement priceStmt = con.prepareStatement(
                         "SELECT productName, productPrice FROM product WHERE productId = ?");
                     PreparedStatement itemStmt = con.prepareStatement(
                         "INSERT INTO orderproduct (orderId, productId, quantity, price) VALUES (?, ?, ?, ?)"))
                {
                    for (ArrayList<Object> product : items)
                    {
                        int productId = Integer.parseInt(product.get(0).toString());
                        int quantity = (Integer) product.get(3);

                        priceStmt.setInt(1, productId);
                        String productName;
                        BigDecimal price;
                        try (ResultSet rst = priceStmt.executeQuery())
                        {
                            if (!rst.next())
                                throw new SQLException("No product with id " + productId);
                            productName = rst.getString("productName");
                            price = rst.getBigDecimal("productPrice");
                        }

                        itemStmt.setInt(1, orderId);
                        itemStmt.setInt(2, productId);
                        itemStmt.setInt(3, quantity);
                        itemStmt.setBigDecimal(4, price);
                        itemStmt.executeUpdate();

                        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity));
                        total = total.add(subtotal);
                        receipt.add(new Object[] {productId, productName, quantity, price, subtotal});
                    }
                }

                // Update the order's total amount.
                try (PreparedStatement totalStmt = con.prepareStatement(
                        "UPDATE ordersummary SET totalAmount = ? WHERE orderId = ?"))
                {
                    totalStmt.setBigDecimal(1, total);
                    totalStmt.setInt(2, orderId);
                    totalStmt.executeUpdate();
                }

                con.commit();
            }
            catch (SQLException | RuntimeException ex)
            {
                con.rollback();
                throw new SQLException("Order not saved", ex);
            }

            // ---- 6. Success: clear the cart only now that everything is saved ----
            session.removeAttribute("productList");
        }
    }
    catch (SQLException ex)
    {
        log("order.jsp: order could not be saved", ex);
        errorTitle = "Order not placed";
        errorMessage = "Sorry, your order could not be placed. Nothing was charged and your cart "
                     + "has been kept, so you can try again.";
        errorLink = "showcart.jsp";
        errorLinkText = "Back to your cart";
    }
}

// =====================================================================================
// Part 2: show the result.
// =====================================================================================
%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Ski Store Order Processing</title>
<link rel="stylesheet" href="css/store.css">
</head>
<body>
<%@ include file="header.jsp" %>
<main>

<% if (errorTitle != null) { %>

<h1><%= errorTitle %></h1>
<p class="error"><%= errorMessage %></p>
<p class="back"><a href="<%= errorLink %>"><%= errorLinkText %></a></p>

<% } else { %>

<h1>Your Order Summary</h1>

<div class="table-wrap">
<table class="receipt">
    <tr><th>Product Id</th><th>Product Name</th><th class="num">Quantity</th><th class="num">Price</th><th class="num">Subtotal</th></tr>
<%     for (Object[] line : receipt) { %>
    <tr class="item">
        <td><%= line[0] %></td>
        <td><%= escapeHtml((String) line[1]) %></td>
        <td class="num"><%= line[2] %></td>
        <td class="num"><%= currFormat.format(line[3]) %></td>
        <td class="num"><%= currFormat.format(line[4]) %></td>
    </tr>
<%     } %>
    <tr class="total"><th colspan="4" class="num">Order Total</th><td class="num"><%= currFormat.format(total) %></td></tr>
</table>
</div>

<h2>Order completed. Will be shipped soon...</h2>
<p>Order reference number: <strong><%= orderId %></strong></p>
<p>Shipping to customer: <%= customerId %> &ndash; <%= escapeHtml(customerName) %></p>

<% } %>

<p class="back"><a href="index.jsp">Back to the Ski Store</a></p>

</main>
</body>
</html>
