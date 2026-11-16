<%-- Site header: store name plus links to products, orders and the cart (with item count).
     Included at the top of every page with an include directive. --%>
<%
{
    // Count the skis in this shopper's cart (each cart entry: 0 id, 1 name, 2 price, 3 quantity).
    int headerCartCount = 0;
    Object headerCart = session.getAttribute("productList");
    if (headerCart instanceof java.util.Map)
    {
        for (Object headerItem : ((java.util.Map<?, ?>) headerCart).values())
        {
            if (headerItem instanceof java.util.List && ((java.util.List<?>) headerItem).size() > 3
                    && ((java.util.List<?>) headerItem).get(3) instanceof Integer)
                headerCartCount += (Integer) ((java.util.List<?>) headerItem).get(3);
        }
    }

    // Mark the link for the page being viewed.
    String headerPage = request.getServletPath();
    String shopCurrent = "/listprod.jsp".equals(headerPage) ? " aria-current=\"page\"" : "";
    String ordersCurrent = "/listorder.jsp".equals(headerPage) ? " aria-current=\"page\"" : "";
    String cartCurrent = "/showcart.jsp".equals(headerPage) ? " aria-current=\"page\"" : "";
%>
<header class="site-header">
    <nav>
        <a class="brand" href="index.jsp">&#9975; Ski Store</a>
        <a href="listprod.jsp"<%= shopCurrent %>>Shop Skis</a>
        <a href="listorder.jsp"<%= ordersCurrent %>>Orders</a>
        <a href="showcart.jsp"<%= cartCurrent %>>Cart (<%= headerCartCount %>)</a>
    </nav>
</header>
<%
}
%>
