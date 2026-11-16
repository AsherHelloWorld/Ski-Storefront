<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Ski Store Checkout</title>
<link rel="stylesheet" href="css/store.css">
</head>
<body>
<main>

<h1>Checkout</h1>
<p>Enter your customer ID to complete the transaction:</p>

<form method="post" action="order.jsp">
    <label for="customerId">Customer ID:</label>
    <input type="text" id="customerId" name="customerId" required>
    <input type="submit" value="Place Order">
</form>

<p class="back"><a href="showcart.jsp">Return to Shopping Cart</a></p>

</main>
</body>
</html>
