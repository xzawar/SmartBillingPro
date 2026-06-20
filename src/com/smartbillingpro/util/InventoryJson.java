package com.smartbillingpro.util;

import com.smartbillingpro.model.Product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A tiny, self-contained JSON reader/writer used to export and import the
 * product catalogue. No external libraries are used so the app stays a single
 * dependency-free build. Only the small, fixed product shape is supported:
 *
 * <pre>
 * [
 *   { "sku": "...", "name": "...", "category": "...",
 *     "costPrice": 0, "salePrice": 0, "quantity": 0 }
 * ]
 * </pre>
 */
public final class InventoryJson {

    private InventoryJson() {
    }

    // ----- writing ------------------------------------------------------------

    /** Turn a list of products into a pretty-printed JSON array. */
    public static String export(List<Product> products) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            sb.append("  {\n");
            sb.append("    \"sku\": ").append(quote(p.getSku())).append(",\n");
            sb.append("    \"name\": ").append(quote(p.getName())).append(",\n");
            sb.append("    \"category\": ").append(quote(p.getCategory())).append(",\n");
            sb.append("    \"costPrice\": ").append(num(p.getCostPrice())).append(",\n");
            sb.append("    \"salePrice\": ").append(num(p.getSalePrice())).append(",\n");
            sb.append("    \"quantity\": ").append(p.getQuantity()).append("\n");
            sb.append("  }").append(i < products.size() - 1 ? "," : "").append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private static String num(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).toPlainString();
    }

    private static String quote(String text) {
        if (text == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.append("\"").toString();
    }

    // ----- reading ------------------------------------------------------------

    /** Parse a JSON array of products. Unknown or malformed entries are skipped. */
    public static List<Product> parse(String json) {
        Parser parser = new Parser(json);
        Object root = parser.parseValue();
        List<Product> products = new ArrayList<>();
        if (!(root instanceof List)) {
            return products;
        }
        for (Object element : (List<?>) root) {
            if (!(element instanceof java.util.Map)) {
                continue;
            }
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) element;
            Product p = new Product();
            p.setSku(str(map.get("sku")));
            p.setName(str(map.get("name")));
            p.setCategory(str(map.get("category")));
            p.setCostPrice(money(map.get("costPrice")));
            p.setSalePrice(money(map.get("salePrice")));
            p.setQuantity((int) Math.round(dbl(map.get("quantity"))));
            if (!p.getName().isEmpty() || !p.getSku().isEmpty()) {
                products.add(p);
            }
        }
        return products;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static BigDecimal money(Object o) {
        if (o instanceof Number) {
            return BigDecimal.valueOf(((Number) o).doubleValue());
        }
        return Money.parse(str(o));
    }

    private static double dbl(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        try {
            return Double.parseDouble(str(o).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** A minimal recursive-descent JSON parser (objects, arrays, strings, numbers, booleans, null). */
    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= s.length()) {
                return null;
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': case 'f': return parseBoolean();
                case 'n': pos += 4; return null;
                default: return parseNumber();
            }
        }

        private java.util.Map<String, Object> parseObject() {
            java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
            pos++; // consume {
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (pos < s.length()) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (peek() == ':') {
                    pos++;
                }
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == '}') {
                    pos++;
                    break;
                }
                break;
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // consume [
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (pos < s.length()) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == ']') {
                    pos++;
                    break;
                }
                break;
            }
            return list;
        }

        private String parseString() {
            StringBuilder sb = new StringBuilder();
            if (peek() != '"') {
                return "";
            }
            pos++; // opening quote
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\' && pos < s.length()) {
                    char esc = s.charAt(pos++);
                    switch (esc) {
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'u':
                            if (pos + 4 <= s.length()) {
                                sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                                pos += 4;
                            }
                            break;
                        default: sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Boolean parseBoolean() {
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            return Boolean.FALSE;
        }

        private Double parseNumber() {
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isDigit(c) || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            try {
                return Double.parseDouble(s.substring(start, pos));
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }

        private char peek() {
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        private void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }
    }
}
