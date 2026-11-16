<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="util.jsp" %>
<%!
// Sort cart lines by product id: numerically when possible, otherwise as text.
private static int compareIds(String a, String b)
{
    try
    {
        return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
    }
    catch (NumberFormatException e)
    {
        return a.compareTo(b);
    }
}
%>
<%
// Get the current list of products (each entry: 0 id, 1 name, 2 price, 3 quantity)
@SuppressWarnings({"unchecked"})
HashMap<String, ArrayList<Object>> productList =
    (HashMap<String, ArrayList<Object>>) session.getAttribute("productList");

// A one-time message from updatecart.jsp (e.g. an invalid quantity).
String cartMessage = (String) session.getAttribute("cartMessage");
session.removeAttribute("cartMessage");

NumberFormat currFormat = NumberFormat.getCurrencyInstance(Locale.CANADA);
%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Your Shopping Cart</title>
<link rel="stylesheet" href="css/store.css">
</head>
<body>
<%@ include file="header.jsp" %>
<main>

<% if (cartMessage != null) { %>
<p class="error"><%= escapeHtml(cartMessage) %></p>
<% } %>

<%
if (productList == null || productList.isEmpty())
{
%>
<h1>Your shopping cart is empty!</h1>
<p class="actions"><a class="button" href="listprod.jsp">Shop for skis</a></p>
<%
}
else
{
    List<ArrayList<Object>> lines = new ArrayList<>(productList.values());
    lines.sort((x, y) -> compareIds(x.get(0).toString(), y.get(0).toString()));

    int skiCount = 0;
    for (ArrayList<Object> line : lines)
        skiCount += (Integer) line.get(3);
%>
<h1>Your Shopping Cart</h1>
<p class="count"><%= skiCount %> <%= skiCount == 1 ? "ski" : "skis" %> in your cart</p>

<div class="table-wrap">
<table class="cart">
    <tr><th>Product</th><th class="num">Price</th><th>Quantity</th><th class="num">Subtotal</th><th></th></tr>
<%
    BigDecimal total = BigDecimal.ZERO;
    for (ArrayList<Object> product : lines)
    {
        String id = product.get(0).toString();
        int quantity = (Integer) product.get(3);

        // The price came from the add-to-cart link, so it may not be a valid number.
        BigDecimal price = BigDecimal.ZERO;
        String priceProblem = null;
        try
        {
            price = new BigDecimal(product.get(2).toString());
        }
        catch (NumberFormatException e)
        {
            priceProblem = "Invalid price for product " + id + ": " + product.get(2);
        }
        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity));
        total = total.add(subtotal);
%>
    <tr class="item" id="cart-item-<%= escapeHtml(id) %>">
        <td class="name"><%= escapeHtml(product.get(1).toString()) %>
            <span class="muted">#<%= escapeHtml(id) %></span>
            <% if (priceProblem != null) { %><div class="error"><%= escapeHtml(priceProblem) %></div><% } %></td>
        <td class="num"><%= currFormat.format(price) %></td>
        <td>
            <form method="post" action="updatecart.jsp" class="qty">
                <input type="hidden" name="id" value="<%= escapeHtml(id) %>">
                <input type="number" name="quantity" value="<%= quantity %>" min="0" max="99" aria-label="Quantity">
                <button type="submit" name="action" value="update" class="secondary">Update</button>
            </form>
        </td>
        <td class="num"><%= currFormat.format(subtotal) %></td>
        <td>
            <form method="post" action="updatecart.jsp">
                <input type="hidden" name="id" value="<%= escapeHtml(id) %>">
                <button type="submit" name="action" value="remove" class="secondary">Remove</button>
            </form>
        </td>
    </tr>
<%
    }
%>
    <tr class="total"><th colspan="3" class="num">Order Total</th><td class="num"><%= currFormat.format(total) %></td><td></td></tr>
</table>
</div>

<div class="actions">
    <a class="button" href="checkout.jsp">Check Out</a>
    <a href="listprod.jsp">Continue Shopping</a>
    <form method="post" action="updatecart.jsp" class="inline">
        <button type="submit" name="action" value="clear" class="secondary">Empty cart</button>
    </form>
</div>
<%
}
%>

</main>
</body>
</html>
