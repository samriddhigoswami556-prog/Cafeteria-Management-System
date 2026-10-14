import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;

record User(long id, String username, String name, String role, String custType, BigDecimal wallet) {
    boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}

record Item(long id, String name, String category, BigDecimal price, int stock, boolean available) {
}

record Line(String name, int qty, BigDecimal unit, BigDecimal total) {
}

record OrderRow(long id, String username, String name, Timestamp date, BigDecimal subtotal,
        BigDecimal discount, BigDecimal tax, BigDecimal total, String pay, String status) {
}

/**
 * Thrown for business-rule failures (insufficient stock / balance ...).
 * Transaction is rolled back.
 */
class BizException extends Exception {
    BizException(String m) {
        super(m);
    }
}

/** Result of the order-processing logic (the "eligibility / billing" step). */
class Bill {
    long orderId;
    String payMode;
    String discountReason = "-";
    BigDecimal subtotal = BigDecimal.ZERO, discount = BigDecimal.ZERO, tax = BigDecimal.ZERO, total = BigDecimal.ZERO;
    BigDecimal walletBefore, walletAfter;
    List<Line> lines = new ArrayList<>();
}

/**
 * All SQL lives here. Every statement uses PreparedStatement +
 * try-with-resources.
 */
public class Dao {
    static final BigDecimal GST = new BigDecimal("0.05");

    static BigDecimal r2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    // ---------- users ----------
    static User mapUser(ResultSet rs) throws SQLException {
        return new User(rs.getLong("USER_ID"), rs.getString("USERNAME"), rs.getString("FULL_NAME"),
                rs.getString("ROLE"), rs.getString("CUST_TYPE"), rs.getBigDecimal("WALLET_BALANCE"));
    }

    static User login(String username, String password) throws SQLException {
        String sql = "SELECT USER_ID, USERNAME, FULL_NAME, ROLE, CUST_TYPE, WALLET_BALANCE FROM USERS "
                + "WHERE USERNAME=? AND PASSWORD_HASH=?";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, Db.sha256(password));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        }
    }

    static User getUser(long id) throws SQLException {
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT USER_ID, USERNAME, FULL_NAME, ROLE, CUST_TYPE, WALLET_BALANCE FROM USERS WHERE USER_ID=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        }
    }

    static int register(String username, String password, String name, String email, String type) throws SQLException {
        String sql = "INSERT INTO USERS (USERNAME, PASSWORD_HASH, FULL_NAME, EMAIL, ROLE, CUST_TYPE, WALLET_BALANCE) "
                + "VALUES (?,?,?,?, 'CUSTOMER', ?, 0)";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, Db.sha256(password));
            ps.setString(3, name);
            ps.setString(4, email);
            ps.setString(5, type);
            return ps.executeUpdate();
        }
    }

    static int topUp(long userId, BigDecimal amt) throws SQLException {
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE USERS SET WALLET_BALANCE = WALLET_BALANCE + ? WHERE USER_ID=?")) {
            ps.setBigDecimal(1, amt);
            ps.setLong(2, userId);
            return ps.executeUpdate();
        }
    }

    // ---------- menu ----------
    static Item mapItem(ResultSet rs) throws SQLException {
        return new Item(rs.getLong("ITEM_ID"), rs.getString("ITEM_NAME"), rs.getString("CATEGORY"),
                rs.getBigDecimal("PRICE"), rs.getInt("STOCK"), "Y".equals(rs.getString("AVAILABLE")));
    }

    /**
     * Parameterised search: optional category, optional max price, optional
     * keyword.
     */
    static List<Item> menu(String category, BigDecimal maxPrice, String keyword, boolean onlyAvailable)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT ITEM_ID, ITEM_NAME, CATEGORY, PRICE, STOCK, AVAILABLE FROM MENU_ITEM WHERE 1=1");
        List<Object> p = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            sql.append(" AND CATEGORY=?");
            p.add(category);
        }
        if (maxPrice != null) {
            sql.append(" AND PRICE<=?");
            p.add(maxPrice);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND UPPER(ITEM_NAME) LIKE ?");
            p.add("%" + keyword.toUpperCase() + "%");
        }
        if (onlyAvailable)
            sql.append(" AND AVAILABLE='Y' AND STOCK>0");
        sql.append(" ORDER BY CATEGORY, ITEM_NAME");
        List<Item> out = new ArrayList<>();
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < p.size(); i++)
                ps.setObject(i + 1, p.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    out.add(mapItem(rs));
            }
        }
        return out;
    }

    static int addItem(String name, String cat, BigDecimal price, int stock) throws SQLException {
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO MENU_ITEM (ITEM_NAME, CATEGORY, PRICE, STOCK) VALUES (?,?,?,?)")) {
            ps.setString(1, name);
            ps.setString(2, cat);
            ps.setBigDecimal(3, price);
            ps.setInt(4, stock);
            return ps.executeUpdate();
        }
    }

    static int updateItem(long id, BigDecimal price, int stock) throws SQLException {
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE MENU_ITEM SET PRICE=?, STOCK=? WHERE ITEM_ID=?")) {
            ps.setBigDecimal(1, price);
            ps.setInt(2, stock);
            ps.setLong(3, id);
            return ps.executeUpdate();
        }
    }

    static int toggleItem(long id) throws SQLException {
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE MENU_ITEM SET AVAILABLE = CASE AVAILABLE WHEN 'Y' THEN 'N' ELSE 'Y' END WHERE ITEM_ID=?")) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        }
    }

    static int deleteItem(long id) throws SQLException {
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement("DELETE FROM MENU_ITEM WHERE ITEM_ID=?")) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        }
    }

    // ---------- the core processing: billing + eligibility + transaction
    // ----------
    /**
     * Inputs : user, {itemId -> qty}, payment mode.
     * Process: validate stock/availability -> subtotal -> discount rules -> GST ->
     * wallet eligibility
     * -> INSERT order + lines, reduce stock, debit wallet (ONE transaction).
     * Output : Bill, or BizException (everything rolled back).
     */
    static Bill placeOrder(User user, Map<Long, Integer> cart, String payMode) throws SQLException, BizException {
        if (cart.isEmpty())
            throw new BizException("Please select at least one item (quantity 1 or more).");
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            try {
                Bill b = new Bill();
                b.payMode = payMode;
                // 1. read prices from the DB (never trust prices coming from the browser)
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT ITEM_NAME, PRICE, STOCK, AVAILABLE FROM MENU_ITEM WHERE ITEM_ID=?")) {
                    for (var e : cart.entrySet()) {
                        ps.setLong(1, e.getKey());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next())
                                throw new BizException("Item #" + e.getKey() + " does not exist.");
                            String n = rs.getString(1);
                            BigDecimal price = rs.getBigDecimal(2);
                            int stock = rs.getInt(3);
                            int q = e.getValue();
                            if (!"Y".equals(rs.getString(4)))
                                throw new BizException("'" + n + "' is currently not available.");
                            if (q > stock)
                                throw new BizException(
                                        "Only " + stock + " of '" + n + "' left (you asked for " + q + ").");
                            BigDecimal lt = r2(price.multiply(BigDecimal.valueOf(q)));
                            b.lines.add(new Line(n, q, price, lt));
                            b.subtotal = b.subtotal.add(lt);
                        }
                    }
                }
                // 2. discount rules
                BigDecimal pct = BigDecimal.ZERO;
                List<String> why = new ArrayList<>();
                if ("STUDENT".equals(user.custType()) && b.subtotal.compareTo(new BigDecimal("100")) >= 0) {
                    pct = pct.add(new BigDecimal("0.10"));
                    why.add("Student 10%");
                } else if ("STAFF".equals(user.custType())) {
                    pct = pct.add(new BigDecimal("0.05"));
                    why.add("Staff 5%");
                }
                if (b.subtotal.compareTo(new BigDecimal("500")) >= 0) {
                    pct = pct.add(new BigDecimal("0.05"));
                    why.add("Bulk 5%");
                }
                b.discount = r2(b.subtotal.multiply(pct));
                if (!why.isEmpty())
                    b.discountReason = String.join(" + ", why);
                // 3. GST 5% on discounted amount, grand total
                b.tax = r2(b.subtotal.subtract(b.discount).multiply(GST));
                b.total = b.subtotal.subtract(b.discount).add(b.tax);
                // 4. wallet eligibility
                if ("WALLET".equals(payMode)) {
                    try (PreparedStatement ps = c
                            .prepareStatement("SELECT WALLET_BALANCE FROM USERS WHERE USER_ID=?")) {
                        ps.setLong(1, user.id());
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            b.walletBefore = rs.getBigDecimal(1);
                        }
                    }
                    if (b.walletBefore.compareTo(b.total) < 0)
                        throw new BizException(
                                "Not eligible for wallet payment: bill is Rs." + b.total + " but wallet has only Rs."
                                        + b.walletBefore + " (short by Rs." + b.total.subtract(b.walletBefore)
                                        + "). Top up or choose Cash.");
                }
                // 5. persist (header)
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO CAFE_ORDER (USER_ID, SUBTOTAL, DISCOUNT, TAX, TOTAL, PAY_MODE, STATUS) VALUES (?,?,?,?,?,?,'PLACED')",
                        new String[] { "order_id" })) {
                    ps.setLong(1, user.id());
                    ps.setBigDecimal(2, b.subtotal);
                    ps.setBigDecimal(3, b.discount);
                    ps.setBigDecimal(4, b.tax);
                    ps.setBigDecimal(5, b.total);
                    ps.setString(6, payMode);
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) {
                        k.next();
                        b.orderId = k.getLong(1);
                    }
                }
                // 6. lines + guarded stock reduction
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO ORDER_ITEM (ORDER_ID, ITEM_ID, QTY, UNIT_PRICE, LINE_TOTAL) VALUES (?,?,?,?,?)");
                        PreparedStatement upd = c.prepareStatement(
                                "UPDATE MENU_ITEM SET STOCK = STOCK - ? WHERE ITEM_ID=? AND STOCK >= ?")) {
                    int i = 0;
                    for (var e : cart.entrySet()) {
                        Line l = b.lines.get(i++);
                        ins.setLong(1, b.orderId);
                        ins.setLong(2, e.getKey());
                        ins.setInt(3, l.qty());
                        ins.setBigDecimal(4, l.unit());
                        ins.setBigDecimal(5, l.total());
                        ins.executeUpdate();
                        upd.setInt(1, l.qty());
                        upd.setLong(2, e.getKey());
                        upd.setInt(3, l.qty());
                        if (upd.executeUpdate() != 1)
                            throw new BizException("'" + l.name() + "' just ran out of stock. Order cancelled.");
                    }
                }
                // 7. wallet debit (guarded)
                if ("WALLET".equals(payMode)) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE USERS SET WALLET_BALANCE = WALLET_BALANCE - ? WHERE USER_ID=? AND WALLET_BALANCE >= ?")) {
                        ps.setBigDecimal(1, b.total);
                        ps.setLong(2, user.id());
                        ps.setBigDecimal(3, b.total);
                        if (ps.executeUpdate() != 1)
                            throw new BizException("Wallet balance changed - payment failed. Order cancelled.");
                    }
                    b.walletAfter = b.walletBefore.subtract(b.total);
                }
                c.commit();
                return b;
            } catch (SQLException | BizException ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /**
     * Cancel a PLACED order: status -> CANCELLED, restock items, refund wallet (one
     * transaction).
     */
    static String cancelOrder(long orderId, User by) throws SQLException, BizException {
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            try {
                long owner;
                String status, pay;
                BigDecimal total;
                try (PreparedStatement ps = c
                        .prepareStatement("SELECT USER_ID, STATUS, PAY_MODE, TOTAL FROM CAFE_ORDER WHERE ORDER_ID=?")) {
                    ps.setLong(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next())
                            throw new BizException("Order not found.");
                        owner = rs.getLong(1);
                        status = rs.getString(2);
                        pay = rs.getString(3);
                        total = rs.getBigDecimal(4);
                    }
                }
                if (!by.isAdmin() && owner != by.id())
                    throw new BizException("You can only cancel your own orders.");
                if (!"PLACED".equals(status))
                    throw new BizException(
                            "Only orders in PLACED status can be cancelled (this one is " + status + ").");
                try (PreparedStatement sel = c.prepareStatement("SELECT ITEM_ID, QTY FROM ORDER_ITEM WHERE ORDER_ID=?");
                        PreparedStatement upd = c
                                .prepareStatement("UPDATE MENU_ITEM SET STOCK = STOCK + ? WHERE ITEM_ID=?")) {
                    sel.setLong(1, orderId);
                    try (ResultSet rs = sel.executeQuery()) {
                        while (rs.next()) {
                            upd.setInt(1, rs.getInt(2));
                            upd.setLong(2, rs.getLong(1));
                            upd.executeUpdate();
                        }
                    }
                }
                if ("WALLET".equals(pay)) {
                    try (PreparedStatement ps = c
                            .prepareStatement("UPDATE USERS SET WALLET_BALANCE = WALLET_BALANCE + ? WHERE USER_ID=?")) {
                        ps.setBigDecimal(1, total);
                        ps.setLong(2, owner);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = c
                        .prepareStatement("UPDATE CAFE_ORDER SET STATUS='CANCELLED' WHERE ORDER_ID=?")) {
                    ps.setLong(1, orderId);
                    ps.executeUpdate();
                }
                c.commit();
                return "WALLET".equals(pay) ? "Order #" + orderId + " cancelled. Rs." + total + " refunded to wallet."
                        : "Order #" + orderId + " cancelled.";
            } catch (SQLException | BizException ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    static int setStatus(long orderId, String status) throws SQLException {
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE CAFE_ORDER SET STATUS=? WHERE ORDER_ID=? AND STATUS<>'CANCELLED'")) {
            ps.setString(1, status);
            ps.setLong(2, orderId);
            return ps.executeUpdate();
        }
    }

    // ---------- order queries ----------
    /** userId == null -> all orders (admin). Uses the V_ORDER_SUMMARY view. */
    static List<OrderRow> orders(Long userId, String status) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT ORDER_ID, USERNAME, FULL_NAME, ORDER_DATE, SUBTOTAL, DISCOUNT, TAX, TOTAL, PAY_MODE, STATUS FROM V_ORDER_SUMMARY WHERE 1=1");
        List<Object> p = new ArrayList<>();
        if (userId != null) {
            sql.append(" AND USERNAME=(SELECT USERNAME FROM USERS WHERE USER_ID=?)");
            p.add(userId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND STATUS=?");
            p.add(status);
        }
        sql.append(" ORDER BY ORDER_ID DESC");
        List<OrderRow> out = new ArrayList<>();
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < p.size(); i++)
                ps.setObject(i + 1, p.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    out.add(new OrderRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4),
                            rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8),
                            rs.getString(9), rs.getString(10)));
            }
        }
        return out;
    }

    static OrderRow order(long id) throws SQLException {
        for (OrderRow o : orders(null, null))
            if (o.id() == id)
                return o;
        return null;
    }

    static List<Line> lines(long orderId) throws SQLException {
        List<Line> out = new ArrayList<>();
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT m.ITEM_NAME, oi.QTY, oi.UNIT_PRICE, oi.LINE_TOTAL FROM ORDER_ITEM oi JOIN MENU_ITEM m ON m.ITEM_ID=oi.ITEM_ID WHERE oi.ORDER_ID=?")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    out.add(new Line(rs.getString(1), rs.getInt(2), rs.getBigDecimal(3), rs.getBigDecimal(4)));
            }
        }
        return out;
    }

    // ---------- reports (GROUP BY / aggregates) ----------
    /**
     * Generic helper: runs a query and returns rows as string arrays + column names
     * (uses ResultSetMetaData).
     */
    static List<String[]> table(String sql, List<String> headersOut) throws SQLException {
        List<String[]> rows = new ArrayList<>();
        try (Connection c = Db.get(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            for (int i = 1; i <= n; i++)
                headersOut.add(md.getColumnLabel(i));
            while (rs.next()) {
                String[] r = new String[n];
                for (int i = 1; i <= n; i++)
                    r[i - 1] = rs.getString(i);
                rows.add(r);
            }
        }
        return rows;
    }

    static final String Q_SUMMARY = "SELECT COUNT(*) AS ORDERS, COALESCE(SUM(TOTAL),0) AS REVENUE, COALESCE(SUM(DISCOUNT),0) AS DISCOUNTS_GIVEN, COALESCE(SUM(TAX),0) AS GST_COLLECTED FROM CAFE_ORDER WHERE STATUS<>'CANCELLED'";
    static final String Q_CATEGORY = "SELECT m.CATEGORY, SUM(oi.QTY) AS UNITS_SOLD, SUM(oi.LINE_TOTAL) AS SALES FROM ORDER_ITEM oi JOIN MENU_ITEM m ON m.ITEM_ID=oi.ITEM_ID JOIN CAFE_ORDER o ON o.ORDER_ID=oi.ORDER_ID WHERE o.STATUS<>'CANCELLED' GROUP BY m.CATEGORY ORDER BY SALES DESC";
    static final String Q_TOP = "SELECT m.ITEM_NAME, SUM(oi.QTY) AS UNITS_SOLD, SUM(oi.LINE_TOTAL) AS SALES FROM ORDER_ITEM oi JOIN MENU_ITEM m ON m.ITEM_ID=oi.ITEM_ID JOIN CAFE_ORDER o ON o.ORDER_ID=oi.ORDER_ID WHERE o.STATUS<>'CANCELLED' GROUP BY m.ITEM_NAME ORDER BY UNITS_SOLD DESC FETCH FIRST 5 ROWS ONLY";
    static final String Q_LOW = "SELECT ITEM_NAME, CATEGORY, STOCK FROM MENU_ITEM WHERE STOCK < 10 ORDER BY STOCK";
}
