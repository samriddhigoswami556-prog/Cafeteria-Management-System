import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Reusable database utility class: opens connections, hashes passwords, translates SQL errors. */
public class Db {
    static final Properties CFG = new Properties();
    static {
        try (InputStream in = new FileInputStream("db.properties")) { CFG.load(in); }
        catch (IOException e) { System.out.println("db.properties not found - using defaults / environment"); }
        String drv = cfg("db.driver", "oracle.jdbc.OracleDriver");
        try { Class.forName(drv); }                       // Step 1: load the JDBC driver
        catch (ClassNotFoundException e) {
            System.err.println("JDBC driver not found: " + drv + "  -> add the jar to the classpath (see README)");
        }
    }
    static String cfg(String key, String def) {
        String env = System.getenv(key.toUpperCase().replace('.', '_'));
        return env != null ? env : CFG.getProperty(key, def);
    }

    /** Step 2: establish a connection. */
    static Connection get() throws SQLException {
        return DriverManager.getConnection(
            cfg("db.url", "jdbc:oracle:thin:@//localhost:1521/XEPDB1"),
            cfg("db.user", "system"), cfg("db.password", "password"));
    }

    static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /** Turn raw SQLException into a message a cafeteria user can understand (Oracle + H2 codes). */
    static String friendly(SQLException e) {
        int c = e.getErrorCode();
        if (c == 1 || c == 23505)  return "That value already exists (duplicate username / item name).";
        if (c == 2290 || c == 23513) return "A value is outside the allowed range (check price, stock, category, balance).";
        if (c == 2291 || c == 23506) return "Referenced record does not exist.";
        if (c == 2292 || c == 23503) return "Cannot delete: this record is used by existing orders. Mark it unavailable instead.";
        if (c == 1400 || c == 23502) return "A required field was left empty.";
        if (c == 17002 || c == 12541 || c == 1017 || c == 90013 || c == 28000)
            return "Database connection/authentication problem: " + e.getMessage();
        return "Database error [" + c + "]: " + e.getMessage();
    }
}

/** Runs sql/schema.sql through JDBC.  Usage: java -cp "out:lib/*" SetupDb */
class SetupDb {
    public static void main(String[] a) throws Exception {
        String file = a.length > 0 ? a[0] : "sql/schema.sql";
        StringBuilder cur = new StringBuilder();
        int ok = 0, skipped = 0;
        try (Connection con = Db.get(); Statement st = con.createStatement()) {
            for (String line : Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.startsWith("--") || t.isEmpty()) continue;
                cur.append(line).append('\n');
                if (t.endsWith(";")) {
                    String sql = cur.toString().trim();
                    sql = sql.substring(0, sql.length() - 1);
                    cur.setLength(0);
                    if (sql.equalsIgnoreCase("COMMIT")) continue;
                    try { st.execute(sql); ok++; }
                    catch (SQLException e) {
                        skipped++;
                        if (!sql.toUpperCase().startsWith("DROP"))
                            System.out.println("FAILED: " + sql.split("\n")[0] + "\n   -> " + e.getMessage());
                    }
                }
            }
            if (!con.getAutoCommit()) con.commit();
        }
        System.out.println("Schema script finished. Statements OK: " + ok + ", skipped/failed: " + skipped);
    }
}
