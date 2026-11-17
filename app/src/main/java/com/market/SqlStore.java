package com.market;

import com.market.Model.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * تخزين البيانات في قاعدة SQLite حقيقية (market.db) بجداول وأعمدة واضحة:
 * - كل حفظ يتم داخل معاملة (Transaction) كاملة أو لا شيء، مع سجل WAL يمنع تلف الملف عند انقطاع الكهرباء.
 * - تُكتب الصفوف المتغيرة فقط، ويتم الكتابة في خيط منفصل حتى لا تتأخر الواجهة.
 */
public final class SqlStore {
    private SqlStore() {}

    static Sqlite conn;
    static Path file;
    static final ExecutorService DBT = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "sqlite"); t.setDaemon(false); return t; });
    static volatile Throwable lastError;

    // ------------------------------------------------------------------ تعريف الجداول
    static final class Table {
        final String name; final Class<?> cls; final Field[] fields; final boolean appendOnly; String lines; // جدول الأسطر للفواتير
        final Map<Long, Integer> saved = new HashMap<>(); // id -> بصمة آخر قيمة محفوظة
        Table(String n, Class<?> c, boolean append) { name = n; cls = c; appendOnly = append; fields = columns(c); }
    }
    static Field[] columns(Class<?> c) {
        List<Field> l = new ArrayList<>();
        for (Field f : c.getFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) || Modifier.isTransient(m)) continue;
            Class<?> t = f.getType();
            if (t == long.class || t == int.class || t == double.class || t == boolean.class || t == String.class || t == LocalDateTime.class || t.isEnum()) l.add(f);
        }
        l.sort((a, b) -> a.getName().equals("id") ? -1 : b.getName().equals("id") ? 1 : 0); // id أولاً
        return l.toArray(new Field[0]);
    }
    static String sqlType(Class<?> t) { return t == double.class ? "REAL" : (t == String.class || t == LocalDateTime.class || t.isEnum()) ? "TEXT" : "INTEGER"; }

    static final Table CATEGORY = new Table("categories", Category.class, false), PRODUCT = new Table("products", Product.class, false),
            PARTY = new Table("parties", Party.class, false), WALLET = new Table("wallets", Wallet.class, false),
            INVOICE = new Table("invoices", Invoice.class, false), VOUCHER = new Table("vouchers", Voucher.class, false),
            EXPCAT = new Table("expense_categories", ExpenseCat.class, false), EXPENSE = new Table("expenses", Expense.class, false),
            DAMAGE = new Table("damages", Damage.class, false),
            CASH = new Table("cash_moves", CashMove.class, true), STOCK = new Table("stock_moves", StockMove.class, true),
            LEDGER = new Table("ledger", Ledger.class, true), HELD = new Table("held_invoices", Invoice.class, false), USERS = new Table("users", User.class, false),
            SHIFT = new Table("shifts", Shift.class, false), OFFER = new Table("offers", Offer.class, false),
            WAREHOUSE = new Table("warehouses", Warehouse.class, false), TRANSFER = new Table("transfers", Transfer.class, false),
            JOURNAL = new Table("journal_entries", JournalEntry.class, false), ACCOUNT = new Table("accounts", Account.class, false),
            BATCH = new Table("batches", Model.Batch.class, false), ORDER = new Table("purchase_orders", PurchaseOrder.class, false);
    static final Table[] TABLES = {CATEGORY, PRODUCT, PARTY, WALLET, INVOICE, VOUCHER, EXPCAT, EXPENSE, DAMAGE, CASH, STOCK, LEDGER, HELD, USERS, SHIFT, OFFER, WAREHOUSE, TRANSFER, JOURNAL, ACCOUNT, BATCH, ORDER};
    static { INVOICE.lines = "invoice_lines"; HELD.lines = "held_lines"; }
    static final Field[] LINE_FIELDS = columns(InvLine.class);
    /** بصمة الصف لاكتشاف التغيير. للفواتير تشمل سطورها، حتى تُحفظ الفاتورة المعدّلة في مكانها بسطورها الجديدة */
    static int rowHash(Object o, Table t) {
        int h = Arrays.hashCode(values(o, t.fields));
        if (o instanceof Invoice && t.lines != null) for (InvLine l : ((Invoice) o).lines) h = 31 * h + Arrays.hashCode(values(l, LINE_FIELDS));
        return h;
    }
    static final Map<String, String> savedSettings = new HashMap<>();

    static Collection<?> rowsOf(Store s, Table t) {
        if (t == CATEGORY) return s.categories; if (t == PRODUCT) return s.products.values(); if (t == PARTY) return s.parties.values();
        if (t == WALLET) return s.wallets; if (t == INVOICE) return s.invoices; if (t == VOUCHER) return s.vouchers; if (t == EXPCAT) return s.expenseCats;
        if (t == EXPENSE) return s.expenses; if (t == DAMAGE) return s.damages; if (t == CASH) return s.cash; if (t == STOCK) return s.stock;
        if (t == HELD) return s.held; if (t == USERS) return s.users; if (t == SHIFT) return s.shifts; if (t == OFFER) return s.offers;
        if (t == WAREHOUSE) return s.warehouses; if (t == TRANSFER) return s.transfers; if (t == JOURNAL) return s.journal; if (t == ACCOUNT) return s.accounts;
        if (t == BATCH) return s.batches; if (t == ORDER) return s.orders; return s.ledger;
    }

    // ------------------------------------------------------------------ تشغيل على خيط القاعدة
    static <T> T onDb(Callable<T> c) {
        try { return DBT.submit(c).get(); }
        catch (ExecutionException e) { Throwable t = e.getCause(); if (t instanceof RuntimeException) throw (RuntimeException) t; throw new RuntimeException(t); }
        catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    static void schema(Sqlite c) {
        c.exec("PRAGMA journal_mode=WAL"); c.exec("PRAGMA synchronous=FULL"); c.exec("PRAGMA foreign_keys=OFF"); c.exec("PRAGMA busy_timeout=5000");
        c.exec("CREATE TABLE IF NOT EXISTS settings (k TEXT PRIMARY KEY, v TEXT)");
        c.exec("CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v TEXT)");
        for (Table t : TABLES) create(c, t.name, t.fields, "id INTEGER PRIMARY KEY");
        for (Table t : TABLES) if (t.lines != null) {
            create(c, t.lines, LINE_FIELDS, "invoice_id INTEGER NOT NULL, pos INTEGER NOT NULL");
            c.exec("CREATE INDEX IF NOT EXISTS ix_" + t.lines + "_inv ON " + t.lines + "(invoice_id)");
        }
        c.exec("CREATE INDEX IF NOT EXISTS ix_inv_type_no ON invoices(type, no)");
        c.exec("CREATE INDEX IF NOT EXISTS ix_inv_date ON invoices(date)");
        c.exec("CREATE INDEX IF NOT EXISTS ix_stock_product ON stock_moves(productId, date)");
        c.exec("CREATE INDEX IF NOT EXISTS ix_cash_wallet ON cash_moves(walletId, date)");
        c.exec("CREATE INDEX IF NOT EXISTS ix_ledger_party ON ledger(partyKind, partyId, date)");
        c.exec("CREATE INDEX IF NOT EXISTS ix_products_barcode ON products(barcode)");
        // فهارس للاستعلامات المتكررة: أسعار الصنف، وكشف حساب الزبون/المورد
        c.exec("CREATE INDEX IF NOT EXISTS ix_invoice_lines_product ON invoice_lines(productId)");
        c.exec("CREATE INDEX IF NOT EXISTS ix_inv_party ON invoices(partyId, date)");
        c.exec("CREATE INDEX IF NOT EXISTS ix_vouchers_party ON vouchers(partyId, date)");
        c.exec("INSERT OR REPLACE INTO meta VALUES ('schema','1')");
    }
    static void create(Sqlite c, String name, Field[] fs, String key) {
        StringBuilder b = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(name).append(" (").append(key);
        for (Field f : fs) if (!f.getName().equals("id") || key.startsWith("invoice_id")) b.append(", \"").append(f.getName()).append("\" ").append(sqlType(f.getType()));
        c.exec(b.append(")").toString());
        // إضافة أعمدة جديدة إن تمت ترقية البرنامج
        Set<String> have = new HashSet<>();
        try (Sqlite.Stmt st = c.prepare("PRAGMA table_info(" + name + ")")) { while (st.step()) have.add(String.valueOf(st.get(1))); }
        for (Field f : fs) if (!have.contains(f.getName())) c.exec("ALTER TABLE " + name + " ADD COLUMN \"" + f.getName() + "\" " + sqlType(f.getType()));
    }

    // ------------------------------------------------------------------ تحويل القيم
    static Object[] values(Object o, Field[] fs) {
        Object[] v = new Object[fs.length];
        try {
            for (int i = 0; i < fs.length; i++) {
                Object x = fs[i].get(o);
                if (x instanceof LocalDateTime) x = x.toString(); else if (x instanceof Enum) x = ((Enum<?>) x).name(); else if (x instanceof Integer) x = ((Integer) x).longValue();
                v[i] = x;
            }
        } catch (IllegalAccessException e) { throw new RuntimeException(e); }
        return v;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void assign(Object o, Field f, Object v) throws IllegalAccessException {
        Class<?> t = f.getType();
        if (v == null) { if (!t.isPrimitive()) f.set(o, null); return; }
        if (t == long.class) f.setLong(o, ((Number) v).longValue());
        else if (t == int.class) f.setInt(o, ((Number) v).intValue());
        else if (t == double.class) f.setDouble(o, ((Number) v).doubleValue());
        else if (t == boolean.class) f.setBoolean(o, ((Number) v).longValue() != 0);
        else if (t == String.class) f.set(o, v.toString());
        else if (t == LocalDateTime.class) f.set(o, LocalDateTime.parse(v.toString()));
        else if (t.isEnum()) f.set(o, Enum.valueOf((Class) t, v.toString()));
    }
    static long id(Object o) { try { return o.getClass().getField("id").getLong(o); } catch (Exception e) { throw new RuntimeException(e); } }

    static String insertSql(String table, Field[] fs, String prefix, int extra) {
        StringBuilder b = new StringBuilder("INSERT OR REPLACE INTO ").append(table).append(" (").append(prefix);
        for (int i = 0; i < fs.length; i++) { if (i > 0 || !prefix.isEmpty()) b.append(", "); b.append('"').append(fs[i].getName()).append('"'); }
        b.append(") VALUES (");
        for (int i = 0; i < fs.length + extra; i++) b.append(i == 0 ? "?" : ", ?");
        return b.append(")").toString();
    }

    // ------------------------------------------------------------------ فتح وتحميل
    public static boolean available() { try { return Sqlite.available(); } catch (Throwable t) { return false; } }

    /** يفتح القاعدة ويحمّل كل البيانات في الذاكرة */
    public static Store open(Path f) {
        file = f;
        return onDb(() -> {
            if (conn != null) conn.close();
            conn = new Sqlite(f); schema(conn);
            Store s = read(conn);
            remember(s);
            return s;
        });
    }

    static boolean isEmpty(Path f) {
        return onDb(() -> { try (Sqlite c = new Sqlite(f); Sqlite.Stmt st = c.prepare("SELECT count(*) FROM sqlite_master WHERE name='settings'")) { st.step(); return ((Long) st.get(0)) == 0; } });
    }

    /** قراءة قاعدة (الحالية أو نسخة احتياطية) إلى كائن Store جديد */
    public static Store readFile(Path f) {
        return onDb(() -> { try (Sqlite c = new Sqlite(f)) { schema(c); return read(c); } });
    }

    static Store read(Sqlite c) throws Exception {
        Store s = new Store();
        try (Sqlite.Stmt st = c.prepare("SELECT k, v FROM settings")) { while (st.step()) s.settings.put(String.valueOf(st.get(0)), String.valueOf(st.get(1))); }
        try (Sqlite.Stmt st = c.prepare("SELECT k, v FROM meta WHERE k LIKE 'seq:%'")) { while (st.step()) s.seq.put(String.valueOf(st.get(0)).substring(4), Long.parseLong(String.valueOf(st.get(1)))); }
        Map<Table, Map<Long, Invoice>> invById = new HashMap<>();
        for (Table t : TABLES) {
            StringBuilder q = new StringBuilder("SELECT id");
            for (Field fd : t.fields) if (!fd.getName().equals("id")) q.append(", \"").append(fd.getName()).append('"');
            q.append(" FROM ").append(t.name).append(" ORDER BY id");
            try (Sqlite.Stmt st = c.prepare(q.toString())) {
                while (st.step()) {
                    Object o = t.cls.getDeclaredConstructor().newInstance();
                    int k = 0;
                    for (Field fd : t.fields) assign(o, fd, st.get(k++));
                    add(s, t, o);
                    if (o instanceof Invoice) { ((Invoice) o).lines = new ArrayList<>(); invById.computeIfAbsent(t, kk -> new HashMap<>()).put(((Invoice) o).id, (Invoice) o); }
                }
            }
        }
        for (Table t : TABLES) if (t.lines != null) {
        Map<Long, Invoice> byId = invById.getOrDefault(t, new HashMap<>());
        StringBuilder q = new StringBuilder("SELECT invoice_id");
        for (Field fd : LINE_FIELDS) q.append(", \"").append(fd.getName()).append('"');
        try (Sqlite.Stmt st = c.prepare(q.append(" FROM " + t.lines + " ORDER BY invoice_id, pos").toString())) {
            while (st.step()) {
                Invoice inv = byId.get((Long) st.get(0)); if (inv == null) continue;
                InvLine l = new InvLine(); for (int i = 0; i < LINE_FIELDS.length; i++) assign(l, LINE_FIELDS[i], st.get(i + 1));
                inv.lines.add(l);
            }
        }
        }
        return s;
    }
    static void add(Store s, Table t, Object o) {
        if (t == PRODUCT) s.products.put(((Product) o).id, (Product) o); else if (t == PARTY) s.parties.put(((Party) o).id, (Party) o);
        else if (t == CATEGORY) s.categories.add((Category) o); else if (t == WALLET) s.wallets.add((Wallet) o); else if (t == INVOICE) s.invoices.add((Invoice) o);
        else if (t == VOUCHER) s.vouchers.add((Voucher) o); else if (t == EXPCAT) s.expenseCats.add((ExpenseCat) o); else if (t == EXPENSE) s.expenses.add((Expense) o);
        else if (t == HELD) s.held.add((Invoice) o); else if (t == USERS) s.users.add((User) o);
        else if (t == SHIFT) s.shifts.add((Shift) o); else if (t == OFFER) s.offers.add((Offer) o);
        else if (t == WAREHOUSE) s.warehouses.add((Warehouse) o); else if (t == TRANSFER) s.transfers.add((Transfer) o);
        else if (t == JOURNAL) s.journal.add((JournalEntry) o); else if (t == ACCOUNT) s.accounts.add((Account) o);
        else if (t == BATCH) s.batches.add((Model.Batch) o); else if (t == ORDER) s.orders.add((PurchaseOrder) o);
        else if (t == DAMAGE) s.damages.add((Damage) o); else if (t == CASH) s.cash.add((CashMove) o); else if (t == STOCK) s.stock.add((StockMove) o); else s.ledger.add((Ledger) o);
    }

    /** تسجيل ما هو محفوظ حالياً لمقارنته لاحقاً */
    static void remember(Store s) {
        savedSettings.clear(); savedSettings.putAll(s.settings);
        for (String k : s.seq.keySet()) savedSettings.put("seq:" + k, String.valueOf(s.seq.get(k)));
        for (Table t : TABLES) { t.saved.clear(); for (Object o : rowsOf(s, t)) t.saved.put(id(o), t.appendOnly ? 0 : rowHash(o, t)); }
    }

    // ------------------------------------------------------------------ الحفظ
    /** دفعة تغييرات جاهزة للكتابة */
    static final class Batch {
        final Map<String, String> settings = new LinkedHashMap<>(); final Set<String> settingsDel = new HashSet<>();
        final Map<Table, List<Object[]>> upserts = new LinkedHashMap<>(); final Map<Table, List<Long>> deletes = new LinkedHashMap<>();
        final Set<Table> wipe = new HashSet<>(); final Map<Table, Map<Long, List<Object[]>>> newLines = new LinkedHashMap<>();
        boolean empty() { return settings.isEmpty() && settingsDel.isEmpty() && upserts.isEmpty() && deletes.isEmpty() && wipe.isEmpty(); }
    }

    /** يُستدعى من خيط الواجهة: يحسب الفروقات فقط (سريع) */
    static Batch diff(Store s, boolean full) {
        Batch b = new Batch();
        Map<String, String> cur = new HashMap<>(s.settings);
        for (String k : s.seq.keySet()) cur.put("seq:" + k, String.valueOf(s.seq.get(k)));
        for (Map.Entry<String, String> e : cur.entrySet()) if (full || !e.getValue().equals(savedSettings.get(e.getKey()))) b.settings.put(e.getKey(), e.getValue());
        for (String k : savedSettings.keySet()) if (!cur.containsKey(k)) b.settingsDel.add(k);
        savedSettings.clear(); savedSettings.putAll(cur);
        for (Table t : TABLES) {
            Collection<?> rows = rowsOf(s, t);
            if (full) { b.wipe.add(t); t.saved.clear(); }
            else if (rows.isEmpty() && !t.saved.isEmpty()) { b.wipe.add(t); t.saved.clear(); continue; }
            List<Object[]> up = new ArrayList<>(); Set<Long> seen = t.appendOnly ? null : new HashSet<>(); int existing = 0;
            for (Object o : rows) {
                long id = id(o);
                if (t.appendOnly) {
                    if (t.saved.containsKey(id)) { existing++; continue; }
                    up.add(values(o, t.fields)); t.saved.put(id, 0);
                } else {
                    seen.add(id);
                    Object[] v = values(o, t.fields); int h = rowHash(o, t);
                    Integer old = t.saved.get(id);
                    if (old == null || old != h) {
                        up.add(v); t.saved.put(id, h);
                        if (o instanceof Invoice && t.lines != null) { // فاتورة جديدة أو معدّلة في مكانها: تُعاد كتابة سطورها
                            List<Object[]> ls = new ArrayList<>(); for (InvLine l : ((Invoice) o).lines) ls.add(values(l, LINE_FIELDS));
                            b.newLines.computeIfAbsent(t, k -> new LinkedHashMap<>()).put(id, ls);
                        }
                    }
                }
            }
            if (!up.isEmpty()) b.upserts.put(t, up);
            // الصفوف المحذوفة
            if (t.appendOnly && !full && existing < t.saved.size() - up.size()) {
                Set<Long> ids = new HashSet<>(); for (Object o : rows) ids.add(id(o));
                List<Long> del = new ArrayList<>(); for (Long id : t.saved.keySet()) if (!ids.contains(id)) del.add(id);
                for (Long id : del) t.saved.remove(id);
                if (!del.isEmpty()) b.deletes.put(t, del);
            } else if (!t.appendOnly && !full && seen.size() < t.saved.size()) {
                List<Long> del = new ArrayList<>(); for (Long id : t.saved.keySet()) if (!seen.contains(id)) del.add(id);
                for (Long id : del) t.saved.remove(id);
                if (!del.isEmpty()) b.deletes.put(t, del);
            }
        }
        return b;
    }

    /** يُنفَّذ على خيط القاعدة: كتابة الدفعة داخل معاملة واحدة */
    static void write(Batch b) {
        if (b.empty()) return;
        Sqlite c = conn;
        c.exec("BEGIN IMMEDIATE");
        try {
            if (!b.settings.isEmpty()) {
                try (Sqlite.Stmt set = c.prepare("INSERT OR REPLACE INTO settings (k, v) VALUES (?, ?)"); Sqlite.Stmt meta = c.prepare("INSERT OR REPLACE INTO meta (k, v) VALUES (?, ?)")) {
                    for (Map.Entry<String, String> e : b.settings.entrySet()) { Sqlite.Stmt st = e.getKey().startsWith("seq:") ? meta : set; st.bind(1, e.getKey()).bind(2, e.getValue()).run(); }
                }
            }
            for (String k : b.settingsDel) c.exec("DELETE FROM " + (k.startsWith("seq:") ? "meta" : "settings") + " WHERE k = '" + k.replace("'", "''") + "'");
            for (Table t : b.wipe) { c.exec("DELETE FROM " + t.name); if (t.lines != null) c.exec("DELETE FROM " + t.lines); }
            for (Map.Entry<Table, List<Long>> e : b.deletes.entrySet()) {
                try (Sqlite.Stmt st = c.prepare("DELETE FROM " + e.getKey().name + " WHERE id = ?");
                     Sqlite.Stmt ln = e.getKey().lines != null ? c.prepare("DELETE FROM " + e.getKey().lines + " WHERE invoice_id = ?") : null) {
                    for (Long id : e.getValue()) { st.bind(1, id).run(); if (ln != null) ln.bind(1, id).run(); }
                }
            }
            for (Map.Entry<Table, List<Object[]>> e : b.upserts.entrySet()) {
                Table t = e.getKey();
                try (Sqlite.Stmt st = c.prepare(insertSql(t.name, t.fields, "", 0))) {
                    for (Object[] v : e.getValue()) { for (int i = 0; i < v.length; i++) st.bind(i + 1, v[i]); st.run(); }
                }
            }
            for (Map.Entry<Table, Map<Long, List<Object[]>>> te : b.newLines.entrySet()) {
                String lt = te.getKey().lines;
                try (Sqlite.Stmt del = c.prepare("DELETE FROM " + lt + " WHERE invoice_id = ?");
                     Sqlite.Stmt st = c.prepare(insertSql(lt, LINE_FIELDS, "invoice_id, pos", 2))) {
                    for (Map.Entry<Long, List<Object[]>> e : te.getValue().entrySet()) {
                        del.bind(1, e.getKey()).run();
                        int pos = 0;
                        for (Object[] v : e.getValue()) { st.bind(1, e.getKey()).bind(2, (long) pos++); for (int i = 0; i < v.length; i++) st.bind(i + 3, v[i]); st.run(); }
                    }
                }
            }
            c.exec("COMMIT");
        } catch (RuntimeException ex) {
            try { c.exec("ROLLBACK"); } catch (Exception ignored) {}
            throw ex;
        }
    }

    /** حفظ التغييرات (الكتابة في الخلفية) */
    public static void save(Store s, boolean full) {
        Batch b = diff(s, full);
        if (full) remember(s);
        if (b.empty()) return;
        DBT.submit(() -> { try { write(b); lastError = null; } catch (Throwable t) { lastError = t; t.printStackTrace(); Main.report(t); } });
    }

    /** ينتظر انتهاء كل عمليات الكتابة */
    public static void flush() { onDb(() -> null); if (lastError != null) throw new RuntimeException("فشل حفظ البيانات: " + lastError.getMessage(), lastError); }

    /** نسخة احتياطية متسقة من القاعدة */
    public static void backup(Path out) {
        flush();
        onDb(() -> {
            conn.exec("PRAGMA wal_checkpoint(TRUNCATE)");
            Files.deleteIfExists(out);
            try { conn.exec("VACUUM INTO '" + out.toAbsolutePath().toString().replace("'", "''") + "'"); }
            catch (Exception e) { Files.copy(file, out, StandardCopyOption.REPLACE_EXISTING); } // نسخ SQLite قديمة
            return null;
        });
    }

    public static List<String> integrity() {
        return onDb(() -> { List<String> r = new ArrayList<>(); try (Sqlite.Stmt st = conn.prepare("PRAGMA integrity_check")) { while (st.step()) r.add(String.valueOf(st.get(0))); } return r; });
    }
    public static void vacuum() { flush(); onDb(() -> { conn.exec("VACUUM"); return null; }); }
    public static void close() { try { flush(); } catch (Exception ignored) {} onDb(() -> { if (conn != null) { conn.exec("PRAGMA wal_checkpoint(TRUNCATE)"); conn.close(); conn = null; } return null; }); }
    public static String info() { return "SQLite " + Sqlite.version() + " | " + file; }
    static <T> T sync(Supplier<T> s) { return s.get(); }
}
