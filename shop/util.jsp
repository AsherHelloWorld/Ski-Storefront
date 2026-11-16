<%!
    // Shared helpers for the shop pages. Pages include this file with an include directive.

    // Escape text before writing it into HTML, so data like a customer name
    // containing "<script>" is shown as text instead of being run by the browser.
    public static String escapeHtml(String text)
    {
        if (text == null)
            return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray())
        {
            switch (c)
            {
                case '<':  sb.append("&lt;");   break;
                case '>':  sb.append("&gt;");   break;
                case '&':  sb.append("&amp;");  break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&#39;");  break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }
%>
