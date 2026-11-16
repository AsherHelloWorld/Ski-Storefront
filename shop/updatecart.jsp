<%@ page import="java.util.HashMap, java.util.ArrayList" %>
<%
// Bonus: change the quantity of a cart line, remove a line, or empty the cart.
// Only POST changes anything (so a link preview can't empty a cart); every request
// then goes back to showcart.jsp, with a one-time message if something was wrong.
//
// Parameters: action = update | remove | clear,  id = product id,  quantity = 0..99
if ("POST".equalsIgnoreCase(request.getMethod()))
{
    @SuppressWarnings("unchecked")
    HashMap<String, ArrayList<Object>> productList =
        (HashMap<String, ArrayList<Object>>) session.getAttribute("productList");

    String action = request.getParameter("action");
    String id = request.getParameter("id");
    String message = null;

    if ("clear".equals(action))
    {
        session.removeAttribute("productList");
    }
    else if ("update".equals(action) || "remove".equals(action))
    {
        if (productList == null || id == null || !productList.containsKey(id))
        {
            message = "That ski is no longer in your cart.";
        }
        else if ("remove".equals(action))
        {
            productList.remove(id);
        }
        else
        {
            int quantity;
            try
            {
                quantity = Integer.parseInt(request.getParameter("quantity").trim());
            }
            catch (RuntimeException e)   // missing or not a whole number
            {
                quantity = Integer.MAX_VALUE;
            }

            if (quantity > 99)
                message = "Please enter a quantity from 0 to 99.";
            else if (quantity <= 0)
                productList.remove(id);          // 0 means "remove this line"
            else
                productList.get(id).set(3, quantity);
        }

        if (productList != null)
            session.setAttribute("productList", productList);
    }

    if (message != null)
        session.setAttribute("cartMessage", message);
}

response.sendRedirect("showcart.jsp");
%>
