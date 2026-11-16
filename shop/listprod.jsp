<%@ page import="java.sql.*,java.net.URLEncoder" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.Locale" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="jdbc.jsp" %>
<%@ include file="util.jsp" %>
<%
// Get product name to search for (blank or missing means "show everything").
String name = request.getParameter("productName");
name = (name == null) ? "" : name.trim();
%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Ski Store - Shop Skis</title>
<link rel="stylesheet" href="css/store.css">
</head>
<body>
<main>

<h1>Search for the skis you want to buy:</h1>

<form method="get" action="listprod.jsp" class="search">
<input type="text" name="productName" size="50" value="<%= escapeHtml(name) %>" aria-label="Ski name">
<input type="submit" value="Submit"><input type="reset" value="Reset"> (Leave blank for all skis)
</form>

<p><a href="showcart.jsp">View your cart</a></p>

<%
NumberFormat currFormat = NumberFormat.getCurrencyInstance(Locale.CANADA);

// The search term is plain text: escape LIKE's special characters (% _ [) so that
// searching for "%" looks for a percent sign instead of matching everything.
String pattern = "%" + name.replace("\\", "\\\\")
                           .replace("%", "\\%")
                           .replace("_", "\\_")
                           .replace("[", "\\[") + "%";

String sql =
      "SELECT p.productId, p.productName, p.productPrice, c.categoryName "
    + "FROM product p LEFT JOIN category c ON p.categoryId = c.categoryId "
    + "WHERE p.productName LIKE ? ESCAPE '\\' "
    + "ORDER BY p.productName";

if (name.isEmpty())
    out.println("<h2>All skis</h2>");
else
    out.println("<h2>Skis matching &quot;" + escapeHtml(name) + "&quot;</h2>");

// try-with-resources closes the connection, statement and result set automatically.
try (Connection con = getConnection();
     PreparedStatement pstmt = con.prepareStatement(sql))
{
    pstmt.setString(1, pattern);

    try (ResultSet rst = pstmt.executeQuery())
    {
        if (!rst.next())
        {
            out.println("<p class=\"notice\">No skis match your search. Try a shorter name, or leave it blank to see every ski.</p>");
        }
        else
        {
%>
<div class="table-wrap">
<table class="products">
    <tr><th></th><th>Product Name</th><th>Category</th><th class="num">Price</th></tr>
<%
            do
            {
                int id = rst.getInt("productId");
                String productName = rst.getString("productName");
                BigDecimal price = rst.getBigDecimal("productPrice");

                // addcart.jsp?id=..&name=..&price=..  (text values URL-encoded)
                String link = "addcart.jsp?id=" + id
                        + "&name=" + URLEncoder.encode(productName == null ? "" : productName, "UTF-8")
                        + "&price=" + price.toPlainString();
%>
    <tr class="product" id="product-<%= id %>">
        <td><a class="add" href="<%= escapeHtml(link) %>">Add to Cart</a></td>
        <td class="name"><%= escapeHtml(productName) %></td>
        <td><%= escapeHtml(rst.getString("categoryName")) %></td>
        <td class="num"><%= currFormat.format(price) %></td>
    </tr>
<%
            } while (rst.next());
%>
</table>
</div>
<%
        }
    }
}
catch (SQLException ex)
{
    log("listprod.jsp: product search failed", ex);
    out.println("<p class=\"error\">Unable to load products right now. Please try again later.</p>");
}
%>

<p class="back"><a href="index.jsp">Back to the Ski Store</a></p>

</main>
</body>
</html>
