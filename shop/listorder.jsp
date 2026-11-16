<%@ page import="java.sql.*" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Locale" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="jdbc.jsp" %>
<%@ include file="util.jsp" %>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Ski Store Order List</title>
<link rel="stylesheet" href="css/store.css">
</head>
<body>
<main>

<h1>Order List</h1>

<%
// Currency like $91.70. The locale is fixed so the server's default can't change it.
NumberFormat currFormat = NumberFormat.getCurrencyInstance(Locale.CANADA);
SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

// All order summaries, with the customer's name.
String orderSql =
      "SELECT o.orderId, o.orderDate, o.customerId, c.firstName, c.lastName, o.totalAmount "
    + "FROM ordersummary o JOIN customer c ON o.customerId = c.customerId "
    + "ORDER BY o.orderId";

// The products in one order. Prepared once and reused for every order.
String itemSql =
      "SELECT op.productId, p.productName, op.quantity, op.price "
    + "FROM orderproduct op JOIN product p ON op.productId = p.productId "
    + "WHERE op.orderId = ? "
    + "ORDER BY op.productId";

// try-with-resources closes the connection, statements and result sets automatically.
try (Connection con = getConnection();
     Statement stmt = con.createStatement();
     ResultSet orders = stmt.executeQuery(orderSql);
     PreparedStatement itemStmt = con.prepareStatement(itemSql))
{
    boolean anyOrders = false;

    while (orders.next())
    {
        anyOrders = true;
        int orderId = orders.getInt("orderId");
        String customerName = orders.getString("firstName") + " " + orders.getString("lastName");
%>
<section class="order" id="order-<%= orderId %>">
<div class="table-wrap">
<table class="order-summary">
    <tr><th>Order Id</th><th>Order Date</th><th>Customer Id</th><th>Customer Name</th><th class="num">Total Amount</th></tr>
    <tr>
        <td><%= orderId %></td>
        <td><%= dateFormat.format(orders.getTimestamp("orderDate")) %></td>
        <td><%= orders.getInt("customerId") %></td>
        <td><%= escapeHtml(customerName) %></td>
        <td class="num"><%= currFormat.format(orders.getBigDecimal("totalAmount")) %></td>
    </tr>
</table>
</div>
<%
        itemStmt.setInt(1, orderId);
        try (ResultSet items = itemStmt.executeQuery())
        {
            if (!items.next())
            {
%>
<p class="notice">No items in this order.</p>
<%
            }
            else
            {
%>
<div class="table-wrap">
<table class="order-items">
    <tr><th>Product Id</th><th>Product Name</th><th class="num">Quantity</th><th class="num">Price</th></tr>
<%
                do
                {
%>
    <tr class="item">
        <td><%= items.getInt("productId") %></td>
        <td><%= escapeHtml(items.getString("productName")) %></td>
        <td class="num"><%= items.getInt("quantity") %></td>
        <td class="num"><%= currFormat.format(items.getBigDecimal("price")) %></td>
    </tr>
<%
                } while (items.next());
%>
</table>
</div>
<%
            }
        }
%>
</section>
<%
    }

    if (!anyOrders)
    {
%>
<p class="notice">No orders have been placed yet.</p>
<%
    }
}
catch (SQLException ex)
{
    // Details go to the Tomcat log; the shopper gets a plain message.
    log("listorder.jsp: could not load orders", ex);
%>
<p class="error">Unable to load orders right now. Please try again later.</p>
<%
}
%>

<p class="back"><a href="index.jsp">Back to the Ski Store</a></p>

</main>
</body>
</html>
