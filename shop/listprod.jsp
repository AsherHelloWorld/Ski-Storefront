<%@ page import="java.sql.*,java.net.URLEncoder" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="jdbc.jsp" %>
<%@ include file="util.jsp" %>
<%
// Get product name to search for (blank or missing means "show everything").
String name = request.getParameter("productName");
name = (name == null) ? "" : name.trim();

// Bonus: optional category filter. Anything that isn't a positive whole number means "all".
int categoryId = 0;
try
{
    categoryId = Integer.parseInt(request.getParameter("categoryId"));
}
catch (NumberFormatException e)
{
    categoryId = 0;
}
if (categoryId < 0)
    categoryId = 0;

// Categories for the dropdown: {id, name}. If they can't be loaded, the dropdown just offers "All".
List<String[]> categories = new ArrayList<>();
String categoryName = null;
try (Connection con = getConnection();
     Statement stmt = con.createStatement();
     ResultSet rst = stmt.executeQuery("SELECT categoryId, categoryName FROM category ORDER BY categoryId"))
{
    while (rst.next())
    {
        categories.add(new String[] {rst.getString(1), rst.getString(2)});
        if (rst.getInt(1) == categoryId)
            categoryName = rst.getString(2);
    }
}
catch (SQLException ex)
{
    log("listprod.jsp: could not load categories", ex);
}
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
<%@ include file="header.jsp" %>
<main>

<h1>Search for the skis you want to buy:</h1>

<form method="get" action="listprod.jsp" class="search">
<input type="text" name="productName" size="40" value="<%= escapeHtml(name) %>" aria-label="Ski name" placeholder="Ski name">
<%-- Choosing a category searches straight away (the Submit button still works without JavaScript). --%>
<select name="categoryId" aria-label="Category" onchange="this.form.submit()">
<option value="">All categories</option>
<%
for (String[] cat : categories)
{
    String selected = cat[0].equals(String.valueOf(categoryId)) ? " selected" : "";
%>
<option value="<%= cat[0] %>"<%= selected %>><%= escapeHtml(cat[1]) %></option>
<%
}
%>
</select>
<input type="submit" value="Submit">
<%-- Reset = load the page with no search and no category (a plain reset button would only
     restore the values that were just submitted). --%>
<a class="button secondary" href="listprod.jsp">Reset</a> (Leave blank for all skis)
</form>

<%
NumberFormat currFormat = NumberFormat.getCurrencyInstance(Locale.CANADA);

// The search term is plain text: escape LIKE's special characters (% _ [) so that
// searching for "%" looks for a percent sign instead of matching everything.
String pattern = "%" + name.replace("\\", "\\\\")
                           .replace("%", "\\%")
                           .replace("_", "\\_")
                           .replace("[", "\\[") + "%";

String sql =
      "SELECT p.productId, p.productName, p.productPrice, p.productDesc, p.productImageURL, c.categoryName "
    + "FROM product p LEFT JOIN category c ON p.categoryId = c.categoryId "
    + "WHERE p.productName LIKE ? ESCAPE '\\' "
    + (categoryId > 0 ? "AND p.categoryId = ? " : "")
    + "ORDER BY p.productName";

// Heading, e.g. "All skis", "Powder skis", "Skis matching "bent"".
String heading;
if (categoryName != null)
    heading = escapeHtml(categoryName) + " skis";
else if (categoryId > 0)
    heading = "Skis";
else
    heading = name.isEmpty() ? "All skis" : "Skis";
if (!name.isEmpty())
    heading += " matching &quot;" + escapeHtml(name) + "&quot;";
out.println("<h2>" + heading + "</h2>");

// try-with-resources closes the connection, statement and result set automatically.
try (Connection con = getConnection();
     PreparedStatement pstmt = con.prepareStatement(sql))
{
    pstmt.setString(1, pattern);
    if (categoryId > 0)
        pstmt.setInt(2, categoryId);

    try (ResultSet rst = pstmt.executeQuery())
    {
        List<String> rows = new ArrayList<>();
        while (rst.next())
        {
            int id = rst.getInt("productId");
            String productName = rst.getString("productName");
            BigDecimal price = rst.getBigDecimal("productPrice");
            String image = rst.getString("productImageURL");
            if (image == null || image.isBlank())
                image = "img/placeholder.svg";

            // addcart.jsp?id=..&name=..&price=..  (text values URL-encoded)
            String link = "addcart.jsp?id=" + id
                    + "&name=" + URLEncoder.encode(productName == null ? "" : productName, "UTF-8")
                    + "&price=" + price.toPlainString();

            rows.add("<tr class=\"product\" id=\"product-" + id + "\">"
                + "<td class=\"image\"><img src=\"" + escapeHtml(image) + "\" alt=\"" + escapeHtml(productName) + "\" width=\"64\" height=\"64\"></td>"
                + "<td class=\"name\">" + escapeHtml(productName) + "</td>"
                + "<td class=\"specs\">" + escapeHtml(rst.getString("productDesc")) + "</td>"
                + "<td class=\"category\">" + escapeHtml(rst.getString("categoryName")) + "</td>"
                + "<td class=\"num\">" + currFormat.format(price) + "</td>"
                + "<td><a class=\"add button\" href=\"" + escapeHtml(link) + "\">Add to Cart</a></td>"
                + "</tr>");
        }

        if (rows.isEmpty())
        {
            out.println("<p class=\"notice\">No skis match your search. Try a shorter name, another category, or leave both blank to see every ski.</p>");
        }
        else
        {
            out.println("<p class=\"count\">" + rows.size() + (rows.size() == 1 ? " ski" : " skis") + "</p>");
%>
<div class="table-wrap">
<table class="products">
    <tr><th></th><th>Product Name</th><th>Specs</th><th>Category</th><th class="num">Price</th><th></th></tr>
<%
            for (String row : rows)
                out.println(row);
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

<p class="back"><a href="showcart.jsp">View your cart</a> &middot; <a href="index.jsp">Back to the Ski Store</a></p>

</main>
</body>
</html>
