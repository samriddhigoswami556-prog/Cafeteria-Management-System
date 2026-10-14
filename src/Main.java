import com.sun.net.httpserver.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cafeteria Management System - tiny web server (JDK built-in) + JDBC. No
 * frameworks needed.
 */
public class Main {
    static final Map<String, User> SESSIONS = new ConcurrentHashMap<>();
    static final String[] CATS = { "BREAKFAST", "MEALS", "SNACKS", "BEVERAGES", "DESSERTS" };
    static final String[] STATUSES = { "PLACED", "PREPARING", "READY", "COMPLETED" };

    interface H {
        void run(Ctx c) throws Exception;
    }

    static class Ctx {
        HttpExchange ex;
        User user;
        Map<String, String> q = new HashMap<>(), f = new HashMap<>();
        String sid;

        String p(String k) {
            String v = f.containsKey(k) ? f.get(k) : q.get(k);
            return v == null ? "" : v.trim();
        }

        void send(int code, String html) throws IOException {
            byte[] b = html.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(code, b.length);
            try (OutputStream o = ex.getResponseBody()) {
                o.write(b);
            }
        }

        void redirect(String to) throws IOException {
            ex.getResponseHeaders().set("Location", to);
            ex.sendResponseHeaders(302, -1);
            ex.close();
        }

        void flash(String to, boolean ok, String msg) throws IOException {
            redirect(to + (to.contains("?") ? "&" : "?") + (ok ? "ok=" : "err=")
                    + URLEncoder.encode(msg, StandardCharsets.UTF_8));
        }
    }

    static Map<String, String> parse(String s) {
        Map<String, String> m = new HashMap<>();
        if (s == null || s.isEmpty())
            return m;
        for (String kv : s.split("&")) {
            int i = kv.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? kv : kv.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8);
            m.put(k, v);
        }
        return m;
    }

    /** role: 0 = public, 1 = any logged-in user, 2 = admin only */
    static void route(HttpServer s, String path, int role, H h) {
        s.createContext(path, ex -> {
            Ctx c = new Ctx();
            c.ex = ex;
            try {
                if (!ex.getRequestURI().getPath().equals(path)) {
                    c.send(404, page(null, "Not found", "<p>Page not found.</p>"));
                    return;
                }
                c.q = parse(ex.getRequestURI().getRawQuery());
                if ("POST".equals(ex.getRequestMethod()))
                    c.f = parse(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String ck = ex.getRequestHeaders().getFirst("Cookie");
                if (ck != null)
                    for (String part : ck.split(";")) {
                        String[] kv = part.trim().split("=", 2);
                        if (kv.length == 2 && kv[0].equals("SID")) {
                            c.sid = kv[1];
                            c.user = SESSIONS.get(kv[1]);
                        }
                    }
                if (role >= 1 && c.user == null) {
                    c.redirect("/login");
                    return;
                }
                if (role == 2 && !c.user.isAdmin()) {
                    c.send(403, page(c.user, "Forbidden",
                            "<div class='empty'><b>This page is for the cafeteria manager.</b><span>Your account does not have admin access.</span><a class='btn' href='/menu'>Back to the menu</a></div>"));
                    return;
                }
                h.run(c);
            } catch (SQLException e) {
                c.send(500, page(c.user, "Error", "<div class='msg err'>" + esc(Db.friendly(e)) + "</div>"));
            } catch (Exception e) {
                e.printStackTrace();
                c.send(500, page(c.user, "Error",
                        "<div class='msg err'>Unexpected error: " + esc(String.valueOf(e.getMessage())) + "</div>"));
            }
        });
    }

    public static void main(String[] args) throws Exception {
        String renderPort = System.getenv("PORT");
        int port = (renderPort != null)
                ? Integer.parseInt(renderPort)
                : Integer.parseInt(Db.cfg("http.port", "8080"));
        try (var con = Db.get()) {
            System.out.println("Database connected: " + con.getMetaData().getURL());
        } catch (SQLException e) {
            System.err.println("!! Could not connect to the database: " + e.getMessage());
        }
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);

        route(s, "/", 0, c -> c.redirect(c.user == null ? "/login" : c.user.isAdmin() ? "/admin/dashboard" : "/menu"));

        // ---------- login / logout / register ----------
        route(s, "/login", 0, c -> {
            if ("POST".equals(c.ex.getRequestMethod())) {
                String u = c.p("username"), pw = c.f.getOrDefault("password", "");
                if (u.isEmpty() || pw.isEmpty()) {
                    c.flash("/login", false, "Enter both username and password.");
                    return;
                }
                User user = Dao.login(u, pw);
                if (user == null) {
                    c.flash("/login", false, "Invalid username or password.");
                    return;
                }
                String sid = UUID.randomUUID().toString();
                SESSIONS.put(sid, user);
                c.ex.getResponseHeaders().add("Set-Cookie", "SID=" + sid + "; Path=/; HttpOnly; SameSite=Lax");
                c.redirect(user.isAdmin() ? "/admin/dashboard" : "/menu");
                return;
            }
            c.send(200, authPage("Sign in",
                    """
                            <form method='post' action='/login'>
                              <label>Username</label><input name='username' autofocus required>
                              <label>Password</label><input type='password' name='password' required>
                              <button class='btn'>Sign in</button>
                            </form>
                            <p class='muted'>New here? <a href='/register'>Create an account</a></p>
                            <div class='demo'><b>Demo logins</b><br>Admin: <code>admin / admin123</code><br>Student: <code>student1 / student123</code><br>Staff: <code>staff1 / staff123</code></div>""",
                    c));
        });
        route(s, "/logout", 0, c -> {
            if (c.sid != null)
                SESSIONS.remove(c.sid);
            c.redirect("/login");
        });
        route(s, "/register", 0, c -> {
            if ("POST".equals(c.ex.getRequestMethod())) {
                String u = c.p("username"), pw = c.f.getOrDefault("password", ""), n = c.p("name"), em = c.p("email"),
                        t = c.p("type");
                if (!u.matches("[A-Za-z0-9_]{3,30}")) {
                    c.flash("/register", false, "Username must be 3-30 letters, digits or underscore.");
                    return;
                }
                if (pw.length() < 6) {
                    c.flash("/register", false, "Password must be at least 6 characters.");
                    return;
                }
                if (n.isEmpty()) {
                    c.flash("/register", false, "Full name is required.");
                    return;
                }
                if (!em.isEmpty() && !em.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                    c.flash("/register", false, "Email looks invalid.");
                    return;
                }
                if (!Set.of("STUDENT", "STAFF", "GUEST").contains(t))
                    t = "GUEST";
                try {
                    Dao.register(u, pw, n, em, t);
                } catch (SQLException e) {
                    c.flash("/register", false, Db.friendly(e));
                    return;
                }
                c.flash("/login", true, "Account created. Please sign in.");
                return;
            }
            c.send(200,
                    authPage("Create account",
                            """
                                    <form method='post' action='/register'>
                                      <label>Full name</label><input name='name' required>
                                      <label>Username</label><input name='username' required pattern='[A-Za-z0-9_]{3,30}'>
                                      <label>Email (optional)</label><input type='email' name='email'>
                                      <label>Password (min 6)</label><input type='password' name='password' minlength='6' required>
                                      <label>I am a</label><select name='type'><option>STUDENT</option><option>STAFF</option><option>GUEST</option></select>
                                      <button class='btn'>Register</button>
                                    </form><p class='muted'><a href='/login'>Back to sign in</a></p>""",
                            c));
        });

        // ---------- customer pages ----------
        route(s, "/menu", 1, c -> {
            String cat = c.p("cat"), kw = c.p("kw"), mp = c.p("max");
            BigDecimal max = null;
            if (!mp.isEmpty())
                try {
                    max = new BigDecimal(mp);
                } catch (NumberFormatException e) {
                    c.flash("/menu", false, "Max price must be a number.");
                    return;
                }
            List<Item> items = Dao.menu(cat, max, kw, false);
            StringBuilder b = new StringBuilder(flash(c));
            b.append("<div class='head'><h1>Menu</h1>");
            if (!c.user.isAdmin())
                b.append("<a class='btn accent' href='/order'>Start an order</a>");
            b.append(
                    "</div><form class='filters' method='get' action='/menu'><select name='cat' aria-label='Category'><option value=''>All categories</option>");
            for (String k : CATS)
                b.append("<option value='").append(k).append("'").append(k.equals(cat) ? " selected" : "").append(">")
                        .append(cap(k)).append("</option>");
            b.append("</select><input name='kw' placeholder='Search for an item' aria-label='Search' value='")
                    .append(esc(kw)).append("'>")
                    .append("<input name='max' placeholder='Max price' aria-label='Max price' type='number' step='0.01' min='0' value='")
                    .append(esc(mp)).append("'>")
                    .append("<button class='btn'>Search</button><a class='btn ghost' href='/menu'>Clear</a></form>");
            if (items.isEmpty())
                b.append(
                        "<div class='empty'><b>Nothing matches that search.</b><span>Try another keyword, a higher price limit, or clear the filters.</span><a class='btn' href='/menu'>Show full menu</a></div>");
            else {
                b.append("<div class='cols'>");
                for (var g : groupByCat(items).entrySet()) {
                    b.append("<section class='cat'><h2><i class='dot c-").append(g.getKey().toLowerCase())
                            .append("'></i>").append(cap(g.getKey())).append("</h2>");
                    for (Item i : g.getValue()) {
                        boolean off = !i.available() || i.stock() == 0;
                        b.append("<div class='mi").append(off ? " off" : "").append("'><span class='n'>")
                                .append(esc(i.name())).append(note(i))
                                .append("</span><span class='dots'></span><span class='p'>")
                                .append(money(i.price())).append("</span></div>");
                    }
                    b.append("</section>");
                }
                b.append("</div>");
            }
            c.send(200, page(c.user, "Menu", b.toString()));
        });
        route(s, "/order", 1, c -> {
            if ("POST".equals(c.ex.getRequestMethod())) {
                Map<Long, Integer> cart = new TreeMap<>();
                for (var e : c.f.entrySet())
                    if (e.getKey().startsWith("qty_")) {
                        String v = e.getValue().trim();
                        if (v.isEmpty())
                            continue;
                        int q;
                        try {
                            q = Integer.parseInt(v);
                        } catch (NumberFormatException ex) {
                            c.flash("/order", false, "Quantities must be whole numbers.");
                            return;
                        }
                        if (q < 0 || q > 50) {
                            c.flash("/order", false, "Quantity must be between 0 and 50.");
                            return;
                        }
                        if (q > 0)
                            cart.put(Long.parseLong(e.getKey().substring(4)), q);
                    }
                String pay = c.p("pay");
                if (!pay.equals("WALLET") && !pay.equals("CASH")) {
                    c.flash("/order", false, "Choose a payment mode.");
                    return;
                }
                User fresh = Dao.getUser(c.user.id());
                try {
                    Bill bill = Dao.placeOrder(fresh, cart, pay);
                    SESSIONS.put(c.sid, Dao.getUser(c.user.id()));
                    c.send(200, page(SESSIONS.get(c.sid), "Bill",
                            billHtml(bill, "Order #" + bill.orderId + " is placed", "", true)));
                } catch (BizException e) {
                    c.flash("/order", false, e.getMessage());
                }
                return;
            }
            c.user = Dao.getUser(c.user.id());
            SESSIONS.put(c.sid, c.user);
            List<Item> items = Dao.menu(null, null, null, true);
            StringBuilder b = new StringBuilder(flash(c));
            b.append("<div class='head'><h1>Place an order</h1><span class='who2'>Wallet balance <b>")
                    .append(money(c.user.wallet())).append("</b></span></div>");
            if (items.isEmpty()) {
                b.append(
                        "<div class='empty'><b>Nothing is available to order right now.</b><span>Check back soon, or ask the cafeteria manager to restock.</span></div>");
                c.send(200, page(c.user, "Order", b.toString()));
                return;
            }
            b.append("<form id='orderForm' method='post' action='/order' data-type='").append(c.user.custType())
                    .append("' data-wallet='").append(c.user.wallet().toPlainString())
                    .append("'><div class='split'><div class='listpane'>");
            for (var g : groupByCat(items).entrySet()) {
                b.append("<section class='cat'><h2><i class='dot c-").append(g.getKey().toLowerCase()).append("'></i>")
                        .append(cap(g.getKey())).append("</h2>");
                for (Item i : g.getValue()) {
                    int mx = Math.min(50, i.stock());
                    b.append("<div class='mi pick' data-price='").append(i.price().toPlainString())
                            .append("' data-name='").append(esc(i.name())).append("'><span class='n'>")
                            .append(esc(i.name())).append(note(i))
                            .append("</span><span class='dots'></span><span class='p'>").append(money(i.price()))
                            .append("</span><span class='step'><button type='button' class='sb' data-d='-1' aria-label='Remove one ")
                            .append(esc(i.name()))
                            .append("'>&minus;</button><input type='number' inputmode='numeric' name='qty_")
                            .append(i.id()).append("' min='0' max='").append(mx)
                            .append("' value='0' aria-label='Quantity of ").append(esc(i.name()))
                            .append("'><button type='button' class='sb' data-d='1' aria-label='Add one ")
                            .append(esc(i.name())).append("'>+</button></span></div>");
                }
                b.append("</section>");
            }
            b.append(
                    "</div><aside class='slip' aria-live='polite'><h2>Your order</h2><div id='lines' class='lines'><p class='muted'>Add items from the menu and your bill appears here.</p></div>")
                    .append("<dl class='tot'><div><dt>Subtotal</dt><dd id='t-sub'>&#8377;0.00</dd></div><div class='disc'><dt id='t-dl'>Discount</dt><dd id='t-dis'>&minus; &#8377;0.00</dd></div><div><dt>GST 5%</dt><dd id='t-tax'>&#8377;0.00</dd></div><div class='grand'><dt>Total</dt><dd id='t-tot'>&#8377;0.00</dd></div></dl>")
                    .append("<fieldset class='pay'><legend>Pay with</legend><label><input type='radio' name='pay' value='WALLET' checked> Wallet <small>")
                    .append(money(c.user.wallet())).append(" available</small></label>")
                    .append("<label><input type='radio' name='pay' value='CASH'> Cash at the counter</label></fieldset><p id='warn' class='warn' hidden></p>")
                    .append("<button class='btn accent wide' id='go' disabled>Place order</button><p class='muted small'>Students get 10% off bills of &#8377;100 or more, staff get 5%, and bills of &#8377;500 or more get another 5%. This is an estimate; the final bill is worked out when you place the order.</p></aside></div></form>")
                    .append("<script>").append(ORDER_JS).append("</script>");
            c.send(200, page(c.user, "Order", b.toString()));
        });
        route(s, "/orders", 1, c -> {
            List<OrderRow> rows = Dao.orders(c.user.isAdmin() ? null : c.user.id(), c.p("status"));
            StringBuilder b = new StringBuilder(flash(c)).append("<div class='head'><h1>")
                    .append(c.user.isAdmin() ? "All orders" : "My orders").append("</h1><span class='muted'>")
                    .append(rows.size()).append(rows.size() == 1 ? " order" : " orders").append("</span></div>");
            if (c.user.isAdmin()) {
                b.append(
                        "<form class='filters' method='get'><select name='status'><option value=''>Any status</option>");
                for (String st : new String[] { "PLACED", "PREPARING", "READY", "COMPLETED", "CANCELLED" })
                    b.append("<option value='").append(st).append("'")
                            .append(st.equals(c.p("status")) ? " selected" : "").append(">").append(cap(st))
                            .append("</option>");
                b.append("</select><button class='btn'>Filter</button></form>");
            }
            if (rows.isEmpty())
                b.append("<div class='empty'><b>")
                        .append(c.p("status").isEmpty() ? "No orders yet" : "No orders with that status")
                        .append("</b><span>")
                        .append(c.user.isAdmin() ? "Orders appear here as soon as customers place them."
                                : "Your orders will appear here after you place one.")
                        .append("</span>")
                        .append(c.user.isAdmin() ? "" : "<a class='btn accent' href='/order'>Place an order</a>")
                        .append("</div>");
            else {
                b.append("<div class='tablewrap'><table><tr><th>#</th><th>Date</th>")
                        .append(c.user.isAdmin() ? "<th>Customer</th>" : "")
                        .append("<th class='r'>Total</th><th>Pay</th><th>Status</th><th></th></tr>");
                for (OrderRow o : rows) {
                    b.append("<tr><td>").append(o.id()).append("</td><td>").append(date(o.date())).append("</td>");
                    if (c.user.isAdmin())
                        b.append("<td>").append(esc(o.name())).append("</td>");
                    b.append("<td class='r'>").append(money(o.total())).append("</td><td>").append(cap(o.pay()))
                            .append("</td><td><span class='pill ")
                            .append(o.status().toLowerCase()).append("'>").append(cap(o.status()))
                            .append("</span></td><td class='act'><a href='/order/view?id=").append(o.id())
                            .append("'>View</a>");
                    if (c.user.isAdmin() && !o.status().equals("CANCELLED") && !o.status().equals("COMPLETED")) {
                        b.append(
                                "<form method='post' action='/order/status' class='inline'><input type='hidden' name='id' value='")
                                .append(o.id()).append("'><select name='status'>");
                        for (String st : STATUSES)
                            b.append("<option value='").append(st).append("'")
                                    .append(st.equals(o.status()) ? " selected" : "").append(">").append(cap(st))
                                    .append("</option>");
                        b.append("</select><button class='btn sm'>Set</button></form>");
                    }
                    if (o.status().equals("PLACED"))
                        b.append(
                                "<form method='post' action='/order/cancel' class='inline' onsubmit=\"return confirm('Cancel order #")
                                .append(o.id())
                                .append("?')\"><input type='hidden' name='id' value='").append(o.id())
                                .append("'><button class='btn sm danger'>Cancel</button></form>");
                    b.append("</td></tr>");
                }
                b.append("</table></div>");
            }
            c.send(200, page(c.user, "Orders", b.toString()));
        });
        route(s, "/order/view", 1, c -> {
            long id = parseLong(c.p("id"));
            OrderRow o = Dao.order(id);
            if (o == null || (!c.user.isAdmin() && !o.username().equals(c.user.username()))) {
                c.flash("/orders", false, "Order not found.");
                return;
            }
            Bill bl = new Bill();
            bl.orderId = id;
            bl.lines = Dao.lines(id);
            bl.subtotal = o.subtotal();
            bl.discount = o.discount();
            bl.tax = o.tax();
            bl.total = o.total();
            bl.payMode = o.pay();
            c.send(200, page(c.user, "Order " + id, billHtml(bl, "Order #" + id,
                    "<span class='pill " + o.status().toLowerCase() + "'>" + cap(o.status()) + "</span>", false)));
        });
        route(s, "/order/cancel", 1, c -> {
            try {
                c.flash("/orders", true, Dao.cancelOrder(parseLong(c.p("id")), c.user));
            } catch (BizException e) {
                c.flash("/orders", false, e.getMessage());
            }
        });
        route(s, "/order/status", 2, c -> {
            int n = Dao.setStatus(parseLong(c.p("id")), c.p("status"));
            c.flash("/orders", n == 1, n == 1 ? "Status updated (" + n + " row)." : "No row updated.");
        });
        route(s, "/wallet", 1, c -> {
            if ("POST".equals(c.ex.getRequestMethod())) {
                BigDecimal amt;
                try {
                    amt = new BigDecimal(c.p("amount"));
                } catch (NumberFormatException e) {
                    c.flash("/wallet", false, "Enter a valid amount.");
                    return;
                }
                if (amt.signum() <= 0 || amt.compareTo(new BigDecimal("5000")) > 0) {
                    c.flash("/wallet", false, "Top-up must be between 1 and 5000.");
                    return;
                }
                Dao.topUp(c.user.id(), amt);
                c.flash("/wallet", true, "Added " + money(amt) + " to your wallet.");
                return;
            }
            c.user = Dao.getUser(c.user.id());
            SESSIONS.put(c.sid, c.user);
            c.send(200, page(c.user, "Wallet", flash(c)
                    + "<div class='head'><h1>My wallet</h1></div><div class='balance'><span>Available balance</span><b>"
                    + money(c.user.wallet()) + "</b></div>"
                    + "<div class='panel'><h2>Add money</h2><form method='post' action='/wallet' class='filters'><input id='amt' name='amount' type='number' min='1' max='5000' step='1' placeholder='Amount in rupees' aria-label='Amount' required>"
                    + "<button class='btn accent'>Add money</button></form><div class='chips'><button type='button' class='chip' data-v='100'>&#8377;100</button><button type='button' class='chip' data-v='200'>&#8377;200</button><button type='button' class='chip' data-v='500'>&#8377;500</button></div>"
                    + "<p class='muted small'>This is a demo top-up with no real payment. You can add up to &#8377;5000 at a time.</p></div>"
                    + "<script>document.querySelectorAll('.chip').forEach(function(b){b.onclick=function(){document.getElementById('amt').value=b.dataset.v;};});</script>"));
        });
        // ---------- admin pages ----------
        route(s, "/admin/dashboard", 2, c -> {
            List<String> h1 = new ArrayList<>(), h2 = new ArrayList<>(), h3 = new ArrayList<>(), h4 = new ArrayList<>();
            List<String[]> sum = Dao.table(Dao.Q_SUMMARY, h1), cat = Dao.table(Dao.Q_CATEGORY, h2),
                    top = Dao.table(Dao.Q_TOP, h3), low = Dao.table(Dao.Q_LOW, h4);
            String[] s0 = sum.get(0);
            StringBuilder b = new StringBuilder(flash(c)).append(
                    "<div class='head'><h1>Dashboard</h1><span class='muted'>Cancelled orders are not counted</span></div><div class='stats'>")
                    .append(stat("Revenue", money(new BigDecimal(s0[1])), true)).append(stat("Orders", s0[0], false))
                    .append(stat("Discounts given", money(new BigDecimal(s0[2])), false))
                    .append(stat("GST collected", money(new BigDecimal(s0[3])), false))
                    .append("</div><div class='grid2'>")
                    .append(bars("Sales by category", cat, 2, "rs")).append(bars("Top 5 items", top, 1, "units"))
                    .append("</div>");
            b.append("<section class='panel'><h2>Low stock</h2>");
            if (low.isEmpty())
                b.append("<p class='muted'>Everything has 10 or more in stock.</p>");
            else {
                b.append("<table><tr><th>Item</th><th>Category</th><th class='r'>Left</th></tr>");
                for (String[] r : low)
                    b.append("<tr><td>").append(esc(r[0])).append("</td><td>").append(cap(r[1]))
                            .append("</td><td class='r'><span class='pill ")
                            .append("0".equals(r[2]) ? "cancelled" : "preparing").append("'>").append(esc(r[2]))
                            .append("</span></td></tr>");
                b.append("</table><p class='muted small'><a href='/admin/menu'>Restock in Manage menu</a></p>");
            }
            b.append("</section>");
            c.send(200, page(c.user, "Dashboard", b.toString()));
        });
        route(s, "/admin/menu", 2, c -> {
            List<Item> items = Dao.menu(null, null, null, false);
            StringBuilder b = new StringBuilder(flash(c))
                    .append("<div class='head'><h1>Manage menu</h1><span class='muted'>").append(items.size())
                    .append(" items</span></div>")
                    .append("<details class='panel'><summary>Add a new item</summary><form method='post' action='/admin/menu/add' class='filters'><input name='name' placeholder='Item name' aria-label='Item name' required maxlength='50'><select name='cat' aria-label='Category'>");
            for (String k : CATS)
                b.append("<option value='").append(k).append("'>").append(cap(k)).append("</option>");
            b.append(
                    "</select><input name='price' type='number' step='0.01' min='0.01' placeholder='Price in rupees' aria-label='Price' required><input name='stock' type='number' min='0' placeholder='Stock' aria-label='Stock' required><button class='btn accent'>Add item</button></form></details>")
                    .append("<div class='tablewrap'><table><tr><th>Item</th><th>Category</th><th>Price (&#8377;)</th><th>Stock</th><th>Status</th><th>Actions</th></tr>");
            for (Item i : items) {
                String fid = "u" + i.id();
                b.append("<tr><td><b>").append(esc(i.name())).append("</b></td><td>").append(cap(i.category()))
                        .append("</td>")
                        .append("<td><input form='").append(fid).append("' name='price' aria-label='Price of ")
                        .append(esc(i.name())).append("' type='number' step='0.01' min='0.01' value='")
                        .append(i.price()).append("'></td>")
                        .append("<td><input form='").append(fid).append("' name='stock' aria-label='Stock of ")
                        .append(esc(i.name())).append("' type='number' min='0' value='").append(i.stock())
                        .append("'></td><td>").append(avail(i)).append("</td>")
                        .append("<td class='act'><form id='").append(fid)
                        .append("' method='post' action='/admin/menu/update' class='inline'><input type='hidden' name='id' value='")
                        .append(i.id()).append("'><button class='btn sm'>Save</button></form>")
                        .append("<form method='post' action='/admin/menu/toggle' class='inline'><input type='hidden' name='id' value='")
                        .append(i.id()).append("'><button class='btn sm ghost'>")
                        .append(i.available() ? "Disable" : "Enable").append("</button></form>")
                        .append("<form method='post' action='/admin/menu/delete' class='inline' onsubmit=\"return confirm('Delete this item?')\"><input type='hidden' name='id' value='")
                        .append(i.id()).append("'><button class='btn sm danger'>Delete</button></form></td></tr>");
            }
            b.append("</table></div>");
            c.send(200, page(c.user, "Manage menu", b.toString()));
        });
        route(s, "/admin/menu/add", 2, c -> {
            try {
                String n = c.p("name");
                BigDecimal pr = new BigDecimal(c.p("price"));
                int st = Integer.parseInt(c.p("stock"));
                if (n.isEmpty() || n.length() > 50) {
                    c.flash("/admin/menu", false, "Item name is required (max 50 chars).");
                    return;
                }
                if (!Arrays.asList(CATS).contains(c.p("cat"))) {
                    c.flash("/admin/menu", false, "Invalid category.");
                    return;
                }
                Dao.addItem(n, c.p("cat"), pr, st);
                c.flash("/admin/menu", true, "Item added.");
            } catch (NumberFormatException e) {
                c.flash("/admin/menu", false, "Price and stock must be numbers.");
            } catch (SQLException e) {
                c.flash("/admin/menu", false, Db.friendly(e));
            }
        });
        route(s, "/admin/menu/update", 2, c -> {
            try {
                int n = Dao.updateItem(parseLong(c.p("id")), new BigDecimal(c.p("price")),
                        Integer.parseInt(c.p("stock")));
                c.flash("/admin/menu", n == 1, n + " row(s) updated.");
            } catch (NumberFormatException e) {
                c.flash("/admin/menu", false, "Price and stock must be numbers.");
            } catch (SQLException e) {
                c.flash("/admin/menu", false, Db.friendly(e));
            }
        });
        route(s, "/admin/menu/toggle", 2, c -> {
            Dao.toggleItem(parseLong(c.p("id")));
            c.flash("/admin/menu", true, "Availability changed.");
        });
        route(s, "/admin/menu/delete", 2, c -> {
            try {
                int n = Dao.deleteItem(parseLong(c.p("id")));
                c.flash("/admin/menu", n == 1, n + " row(s) deleted.");
            } catch (SQLException e) {
                c.flash("/admin/menu", false, Db.friendly(e));
            }
        });

        s.start();
        System.out.println("Cafeteria app running ->  http://localhost:" + port);
    }

    // ================= HTML helpers =================
    static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return -1;
        }
    }

    static String esc(String s) {
        return s == null ? ""
                : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'",
                        "&#39;");
    }

    static String money(BigDecimal v) {
        return "&#8377;" + (v == null ? "0.00" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
    }

    static String date(java.util.Date d) {
        return d == null ? "" : new SimpleDateFormat("dd MMM yyyy, HH:mm").format(d);
    }

    static String cap(String s) {
        return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase();
    }

    static Map<String, List<Item>> groupByCat(List<Item> items) {
        Map<String, List<Item>> m = new LinkedHashMap<>();
        for (String k : CATS)
            for (Item i : items)
                if (i.category().equals(k))
                    m.computeIfAbsent(k, x -> new ArrayList<>()).add(i);
        return m;
    }

    /** Small stock note shown under an item name. */
    static String note(Item i) {
        if (!i.available())
            return "<small>Not available today</small>";
        if (i.stock() == 0)
            return "<small class='low'>Sold out</small>";
        if (i.stock() < 10)
            return "<small class='low'>Only " + i.stock() + " left</small>";
        return "";
    }

    static String avail(Item i) {
        return !i.available() ? "<span class='pill cancelled'>Disabled</span>"
                : i.stock() == 0 ? "<span class='pill cancelled'>Sold out</span>"
                        : i.stock() < 10 ? "<span class='pill preparing'>Low stock</span>"
                                : "<span class='pill completed'>Available</span>";
    }

    static String stat(String l, String v, boolean hero) {
        return "<div class='stat" + (hero ? " lead" : "") + "'><span>" + l + "</span><b>" + v + "</b></div>";
    }

    /**
     * Horizontal bar list; rows are {label, units, amount}; bars scale on column
     * `col`.
     */
    static String bars(String title, List<String[]> rows, int col, String unit) {
        StringBuilder b = new StringBuilder("<section class='panel'><h2>" + title + "</h2>");
        if (rows.isEmpty())
            return b.append(
                    "<p class='muted'>No sales yet. Orders will show up here as soon as they are placed.</p></section>")
                    .toString();
        double max = 0;
        for (String[] r : rows)
            max = Math.max(max, Double.parseDouble(r[col]));
        for (String[] r : rows) {
            double v = Double.parseDouble(r[col]);
            b.append("<div class='bar'><span class='bl'>")
                    .append(esc(r[0].equals(r[0].toUpperCase()) ? cap(r[0]) : r[0]))
                    .append("</span><span class='track'><i style='width:")
                    .append(max == 0 ? 0 : Math.round(v * 100 / max)).append("%'></i></span><b>")
                    .append(unit.equals("rs") ? "&#8377;" + r[col] : r[col] + " sold").append("</b></div>");
        }
        return b.append("</section>").toString();
    }

    static String flash(Ctx c) {
        if (!c.p("ok").isEmpty())
            return "<div class='msg ok' role='status'>" + esc(c.p("ok")) + "</div>";
        if (!c.p("err").isEmpty())
            return "<div class='msg err' role='alert'>" + esc(c.p("err")) + "</div>";
        return "";
    }

    static String billHtml(Bill b, String title, String badge, boolean justPlaced) {
        StringBuilder s = new StringBuilder("<div class='head'><h1>" + esc(title) + "</h1>" + badge
                + "</div><div class='receipt'><div class='rh'><b>Campus Caf&eacute;</b><span>Order #" + b.orderId
                + "</span></div><table><tr><th>Item</th><th class='r'>Qty</th><th class='r'>Price</th><th class='r'>Amount</th></tr>");
        for (Line l : b.lines)
            s.append("<tr><td>").append(esc(l.name())).append("</td><td class='r'>").append(l.qty())
                    .append("</td><td class='r'>").append(money(l.unit())).append("</td><td class='r'>")
                    .append(money(l.total())).append("</td></tr>");
        s.append("</table><dl class='tot'><div><dt>Subtotal</dt><dd>").append(money(b.subtotal)).append("</dd></div>");
        if (b.discount.signum() > 0 || !b.discountReason.equals("-"))
            s.append("<div class='disc'><dt>Discount")
                    .append(b.discountReason.equals("-") ? "" : " <small>" + esc(b.discountReason) + "</small>")
                    .append("</dt><dd>&minus; ").append(money(b.discount)).append("</dd></div>");
        s.append("<div><dt>GST 5%</dt><dd>").append(money(b.tax))
                .append("</dd></div><div class='grand'><dt>Total</dt><dd>").append(money(b.total)).append("</dd></div>")
                .append("<div><dt>Paid by</dt><dd>")
                .append(b.payMode.equals("WALLET") ? "Wallet" : "Cash at the counter").append("</dd></div>");
        if (b.walletBefore != null)
            s.append("<div><dt>Wallet balance</dt><dd>").append(money(b.walletBefore)).append(" &rarr; ")
                    .append(money(b.walletAfter)).append("</dd></div>");
        s.append("</dl></div><div class='actions'><a class='btn' href='/orders'>")
                .append(justPlaced ? "View my orders" : "Back to orders").append("</a>");
        if (justPlaced)
            s.append("<a class='btn ghost' href='/order'>Order more</a>");
        return s.append("</div>").toString();
    }

    static String boardHtml() {
        StringBuilder b = new StringBuilder();
        try {
            Map<String, Integer> seen = new HashMap<>();
            int n = 0;
            for (Item i : Dao.menu(null, null, null, true)) {
                if (n >= 8 || seen.merge(i.category(), 1, Integer::sum) > 2)
                    continue;
                b.append("<div class='mi'><span class='n'>").append(esc(i.name()))
                        .append("</span><span class='dots'></span><span class='p'>").append(money(i.price()))
                        .append("</span></div>");
                n++;
            }
        } catch (Exception e) {
            /* board is decoration; never block the login page */ }
        return b.length() == 0 ? "<p class='muted'>The menu will appear here once the database is connected.</p>"
                : b.toString();
    }

    static String authPage(String title, String inner, Ctx c) {
        return "<!doctype html><html lang='en'><head>" + HEAD + "<title>" + title
                + " - Campus Caf&eacute;</title></head><body class='auth'>"
                + "<div class='hero'><div class='brand'><i class='plate'></i>Campus Caf&eacute;</div><h1>Order from your seat.<br>Pick up when it&#39;s ready.</h1>"
                + "<div class='board'><h2>On the counter today</h2>" + boardHtml() + "</div></div>"
                + "<main class='formside'><div class='authbox'><h2>" + title + "</h2>" + flash(c) + inner
                + "</div></main></body></html>";
    }

    static String page(User u, String title, String body) {
        StringBuilder nav = new StringBuilder();
        if (u != null) {
            nav.append("<a href='/menu'>Menu</a>");
            if (u.isAdmin())
                nav.append(
                        "<a href='/admin/dashboard'>Dashboard</a><a href='/admin/menu'>Manage menu</a><a href='/orders'>All orders</a>");
            else
                nav.append("<a href='/order'>Order</a><a href='/orders'>My orders</a><a href='/wallet'>Wallet</a>");
            nav.append("<span class='who'>").append(esc(u.name())).append(u.isAdmin() ? " (admin)" : "")
                    .append("</span><a class='out' href='/logout'>Log out</a>");
        }
        return "<!doctype html><html lang='en'><head>" + HEAD + "<title>" + esc(title)
                + " - Campus Caf&eacute;</title></head><body>"
                + "<header><a class='brand' href='/'><i class='plate'></i>Campus Caf&eacute;</a><nav>" + nav
                + "</nav></header><main>" + body + "</main>"
                + "<script>document.querySelectorAll('nav a').forEach(function(a){if(a.getAttribute('href')===location.pathname)a.classList.add('on');});</script></body></html>";
    }

    static final String HEAD = "<meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<link rel='preconnect' href='https://fonts.googleapis.com'><link rel='preconnect' href='https://fonts.gstatic.com' crossorigin>"
            + "<link rel='stylesheet' href='https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,500;12..96,700&family=Figtree:wght@400;500;600&display=swap'>"
            + "<style>" + Css.CSS + "</style>";

    static final String ORDER_JS = """
              (function(){
                var f=document.getElementById('orderForm'); if(!f) return;
                var type=f.dataset.type, wallet=parseFloat(f.dataset.wallet);
                var lines=document.getElementById('lines'), go=document.getElementById('go'), warn=document.getElementById('warn');
                function fmt(v){return '₹'+v.toFixed(2);}
                function r2(v){return Math.round(v*100)/100;}
                function set(id,t){document.getElementById(id).textContent=t;}
                function update(){
                  var sub=0, count=0; lines.textContent='';
                  f.querySelectorAll('.mi.pick').forEach(function(row){
                    var inp=row.querySelector('input[type=number]'), max=parseInt(inp.max)||0, q=parseInt(inp.value)||0;
                    if(q<0) q=0; if(q>max) q=max; inp.value=q;
                    row.classList.toggle('on',q>0);
                    if(q>0){
                      var lt=parseFloat(row.dataset.price)*q; sub+=lt; count+=q;
                      var d=document.createElement('div'); d.className='ln';
                      var a=document.createElement('span'); a.textContent=row.dataset.name+' × '+q;
                      var b=document.createElement('span'); b.textContent=fmt(lt);
                      d.appendChild(a); d.appendChild(b); lines.appendChild(d);
                    }
                  });
                  if(count===0){var p=document.createElement('p'); p.className='muted'; p.textContent='Add items from the menu and your bill appears here.'; lines.appendChild(p);}
                  var pct=0, why=[];
                  if(type==='STUDENT' && sub>=100){pct+=0.10; why.push('student 10%');}
                  else if(type==='STAFF'){pct+=0.05; why.push('staff 5%');}
                  if(sub>=500){pct+=0.05; why.push('bulk 5%');}
                  var dis=r2(sub*pct), tax=r2((sub-dis)*0.05), tot=r2(sub-dis+tax);
                  set('t-sub',fmt(sub)); set('t-dis','− '+fmt(dis)); set('t-tax',fmt(tax)); set('t-tot',fmt(tot));
                  set('t-dl', why.length? 'Discount ('+why.join(' + ')+')' : 'Discount');
                  var pay=f.querySelector('input[name=pay]:checked').value, short=pay==='WALLET' && tot>wallet;
                  warn.hidden=!short;
                  if(short) warn.textContent='Your wallet is short by '+fmt(tot-wallet)+'. Add money in Wallet or pay cash at the counter.';
                  go.disabled = count===0 || short;
                }
                f.addEventListener('click',function(e){
                  var b=e.target.closest('.sb'); if(!b) return;
                  var inp=b.parentNode.querySelector('input'); inp.value=(parseInt(inp.value)||0)+parseInt(b.dataset.d); update();
                });
                f.addEventListener('input',update); f.addEventListener('change',update); update();
              })();
            """;
}
