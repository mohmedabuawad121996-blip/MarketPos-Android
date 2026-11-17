package com.market;

import com.market.Model.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** مخزن البيانات: يحفظ كل شيء في ملف واحد مضغوط مع حفظ ذري ونسخ احتياطي تلقائي */
public class Store implements Serializable {
    private static final long serialVersionUID = 1L;

    public Map<String, String> settings = new LinkedHashMap<>();
    public Map<String, Long> seq = new HashMap<>();
    public List<Category> categories = new ArrayList<>();
    public LinkedHashMap<Long, Product> products = new LinkedHashMap<>();
    public LinkedHashMap<Long, Party> parties = new LinkedHashMap<>();
    public List<Wallet> wallets = new ArrayList<>();
    public List<Invoice> invoices = new ArrayList<>();
    public List<Voucher> vouchers = new ArrayList<>();
    public List<ExpenseCat> expenseCats = new ArrayList<>();
    public List<Expense> expenses = new ArrayList<>();
    public List<CashMove> cash = new ArrayList<>();
    public List<StockMove> stock = new ArrayList<>();
    public List<Ledger> ledger = new ArrayList<>();
    public List<Damage> damages = new ArrayList<>();
    public List<Invoice> held = new ArrayList<>(); // فواتير معلّقة
    public List<User> users = new ArrayList<>();
    public List<Shift> shifts = new ArrayList<>();
    public List<Offer> offers = new ArrayList<>();
    public List<Warehouse> warehouses = new ArrayList<>();
    public List<Transfer> transfers = new ArrayList<>();
    public List<JournalEntry> journal = new ArrayList<>();
    public List<Account> accounts = new ArrayList<>();
    public List<Batch> batches = new ArrayList<>();
    public List<PurchaseOrder> orders = new ArrayList<>();

    // ---------------- المستودعات ----------------
    private transient Map<Long, Map<Long, Double>> whq; // صنف ← (مستودع ← كمية)
    public long mainWh() { return warehouses.isEmpty() ? 1 : warehouses.get(0).id; }
    public long whOf(long id) { return id == 0 ? mainWh() : id; }
    public List<Warehouse> activeWarehouses() { List<Warehouse> l = new ArrayList<>(); for (Warehouse w : warehouses) if (w.active) l.add(w); return l; }
    public boolean multiWh() { return activeWarehouses().size() > 1; }
    public Warehouse warehouse(long id) { long k = whOf(id); for (Warehouse w : warehouses) if (w.id == k) return w; return null; }
    public String whName(long id) { Warehouse w = warehouse(id); return w == null ? "" : w.name; }
    public void invalidateWh() { whq = null; }
    synchronized Map<Long, Map<Long, Double>> whIndex() {
        if (whq == null) { Map<Long, Map<Long, Double>> m = new HashMap<>();
            for (StockMove s : stock) m.computeIfAbsent(s.productId, k -> new HashMap<>()).merge(whOf(s.warehouseId), s.qty, Double::sum);
            whq = m; }
        return whq;
    }
    /** كمية الصنف في مستودع محدد */
    public double whQty(long productId, long wh) {
        Map<Long, Double> m = whIndex().get(productId); if (m == null) return 0;
        Double d = m.get(whOf(wh)); return d == null ? 0 : Ui.r(d, 3);
    }
    synchronized void whAdd(StockMove s) { if (whq != null) whq.computeIfAbsent(s.productId, k -> new HashMap<>()).merge(whOf(s.warehouseId), s.qty, Double::sum); }

    private transient Map<String, Product> byBarcode;

    // ======================= الوصول العام =======================
    public static Store db;
    public static Path dataDir = Paths.get(System.getProperty("user.home", "/sdcard"), "MarketPro"); // يضبطه التطبيق على مجلد بياناته عند التشغيل
    /** true = البيانات في قاعدة SQLite حقيقية، false = ملف احتياطي (أجهزة لا تدعم SQLite) */
    public static boolean SQL;
    public static Path dataFile() { return dataDir.resolve(SQL ? "market.db" : "market.dat"); }
    public static String ext() { return SQL ? ".db" : ".dat"; }
    public static String engine() { return SQL ? "قاعدة بيانات SQLite" : "ملف بيانات"; }
    private static final ExecutorService saver = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "saver"); t.setDaemon(false); return t; });
    private static final Object fileLock = new Object();

    public long next(String key) { long v = seq.getOrDefault(key, 0L) + 1; seq.put(key, v); return v; }

    public String get(String k, String def) { return settings.getOrDefault(k, def); }
    public double getD(String k, double def) { try { return Double.parseDouble(settings.get(k)); } catch (Exception e) { return def; } }
    public long getL(String k, long def) { try { return Long.parseLong(settings.get(k)); } catch (Exception e) { return def; } }
    public boolean getB(String k, boolean def) { String v = settings.get(k); return v == null ? def : v.equals("1"); }
    public void set(String k, String v) { settings.put(k, v); }
    public void setB(String k, boolean v) { settings.put(k, v ? "1" : "0"); }

    public Product byBarcode(String code) {
        if (byBarcode == null) reindex();
        return byBarcode.get(code.trim());
    }
    public void reindexOne(Product p) { if (byBarcode == null) reindex(); else { if (p.barcode != null && !p.barcode.trim().isEmpty()) byBarcode.put(p.barcode.trim(), p); if (!p.bigCode().isEmpty()) byBarcode.putIfAbsent(p.bigCode(), p); for (String c : p.extraCodes()) byBarcode.putIfAbsent(c, p); } }
    /** هل هذا الباركود هو باركود الوحدة الكبرى للصنف؟ */
    public static boolean isBigCode(Product p, String code) { return p != null && p.hasBig() && !p.bigCode().isEmpty() && p.bigCode().equals(code.trim()) && !p.bigCode().equals(p.barcode == null ? "" : p.barcode.trim()); }
    public void reindex() {
        whq = null;
        byBarcode = new HashMap<>();
        for (Product p : products.values()) if (p.barcode != null && !p.barcode.trim().isEmpty()) byBarcode.put(p.barcode.trim(), p);
        for (Product p : products.values()) if (!p.bigCode().isEmpty()) byBarcode.putIfAbsent(p.bigCode(), p);
        for (Product p : products.values()) for (String c : p.extraCodes()) byBarcode.putIfAbsent(c, p);
    }
    public Category category(long id) { for (Category c : categories) if (c.id == id) return c; return null; }
    public String categoryName(long id) { Category c = category(id); return c == null ? "" : c.name; }
    public Wallet wallet(long id) { for (Wallet w : wallets) if (w.id == id) return w; return null; }
    public String walletName(long id) { Wallet w = wallet(id); return w == null ? "" : w.name; }
    public Party party(long id) { return parties.get(id); }
    public String partyName(long id) { Party p = parties.get(id); return p == null ? "نقدي" : p.name; }
    public ExpenseCat expenseCat(long id) { for (ExpenseCat c : expenseCats) if (c.id == id) return c; return null; }
    public Invoice invoice(long id) { for (Invoice i : invoices) if (i.id == id) return i; return null; }
    public Invoice invoiceByNo(InvType t, long no) { for (Invoice i : invoices) if (i.type == t && i.no == no) return i; return null; }
    public List<Party> parties(int kind) { List<Party> l = new ArrayList<>(); for (Party p : parties.values()) if (p.kind == kind && p.active) l.add(p); return l; }
    public List<Wallet> activeWallets() { List<Wallet> l = new ArrayList<>(); for (Wallet w : wallets) if (w.active) l.add(w); return l; }
    public long defaultWallet() { return wallets.isEmpty() ? 0 : wallets.get(0).id; }

    // ======================= التهيئة =======================
    /** القيم الافتراضية للإعدادات (تُستخدم عند أول تشغيل وعند استعادة الإعدادات الافتراضية) */
    static final String[][] DEFAULTS = {
            {"storeName", "سوبر ماركت"}, {"address", "عمّان - الأردن"}, {"phone", "07xxxxxxxx"}, {"taxNo", ""}, {"currency", "د.أ"},
            {"decimals", "3"}, {"footer", "شكراً لتسوقكم معنا"}, {"taxEnabled", "1"}, {"taxRate", "16"}, {"taxInclusive", "1"},
            {"allowBelowCost", "0"}, {"allowNegative", "1"}, {"costMethod", "avg"}, {"paper", "80"}, {"printPreview", "1"},
            {"autoPrint", "0"}, {"copies", "1"}, {"lanPort", "8085"}};
    /** مجموعات الإعدادات التي يمكن إرجاعها للافتراضي */
    public static final String[] G_SALES = {"allowBelowCost", "allowNegative", "allowNegativeCash", "allowFreeReturn", "costMethod", "decimals", "taxEnabled", "taxInclusive", "taxRate", "shiftsEnabled", "defaultFloat", "goldPrice", "rememberUser", "askNewProduct"},
            G_LOOK = {"theme", "fontFamily", "fontSize", "fontWeight", "fontWidth", "sidebarAuto", "sidebarCollapsed", "animations"},
            G_PRINT = {"printer", "paper", "copies", "printPreview", "reportBold", "autoPrint", "printWidthMM", "printWidthMM_80", "printWidthMM_58", "printOffsetMM", "printFont", "printLang", "headerText", "footer", "termsText", "lblW", "lblH", "lblLayout",
                    "lblStore", "lblName", "lblPrice", "lblDigits", "lblBorder", "lblFsStore", "lblFsName", "lblFsPrice", "lblFsDigits", "drawerAuto", "drawerPin"},
            G_EXTRA = {"scaleOn", "scalePrefixes", "scaleCodeLen", "scaleMode", "scaleDecimals", "pointsOn", "pointsPer", "pointValue", "pointsMin", "custDisplayOn", "custDisplay", "custDisplayMsg", "expiryDays", "waCountry"},
            G_STORE = {"storeName", "address", "phone", "taxNo", "currency", "currencyCode"},
            G_LINKS = {"jofEnabled", "jofAuto", "jofCity", "jofClientId", "jofName", "jofSecret", "jofSeq", "jofTin", "jofType", "jofUrl", "gdriveAuto", "gdriveDir", "gdriveKeep", "gdriveMode",
                    "lanAuto", "lanPin", "lanPort", "branchFolder", "branchName", "branchSync", "cloud"};
    /** يحذف مفاتيح المجموعات المختارة ويضع قيمها الافتراضية؛ يرجع عدد الإعدادات التي أُعيدت */
    public int resetSettings(java.util.Collection<String[]> groups) {
        int n = 0;
        for (String[] g : groups) for (String k : g) { String old = settings.remove(k); String d = null;
            for (String[] x : DEFAULTS) if (x[0].equals(k)) d = x[1];
            if (d != null) settings.put(k, d);
            if (old != null && !old.equals(d)) n++; }
        return n;
    }

    private void seed() {
        for (String[] x : DEFAULTS) set(x[0], x[1]);
        set("fiscalStart", LocalDateTime.now().getYear() + "-01-01");
        set("installDate", java.time.LocalDate.now().toString());
        String[] ws = {"الصندوق الرئيسي", "البنك", "محفظة إلكترونية"}; String[] wt = {"نقدي", "بنك", "محفظة"};
        for (int i = 0; i < ws.length; i++) { Wallet w = new Wallet(); w.id = next("wallet"); w.name = ws[i]; w.type = wt[i]; wallets.add(w); }
        for (String c : new String[]{"عام", "مواد غذائية", "مشروبات", "منظفات", "ألبان وأجبان", "خضار وفواكه"}) {
            Category ct = new Category(); ct.id = next("cat"); ct.name = c; categories.add(ct);
        }
        for (String c : new String[]{"إيجار", "كهرباء وماء", "رواتب", "نقل", "صيانة", "متفرقة"}) {
            ExpenseCat e = new ExpenseCat(); e.id = next("expcat"); e.name = c; expenseCats.add(e);
        }
    }

    // ======================= الحفظ والتحميل =======================
    public static void load() {
        License.resetCache();
        try { Files.createDirectories(dataDir); } catch (IOException e) { throw new UncheckedIOException(e); }
        Path dbf = dataDir.resolve("market.db"), dat = dataDir.resolve("market.dat");
        if (!"0".equals(System.getProperty("marketpro.sqlite")) && SqlStore.available()) {
            try {
                if (!Files.exists(dbf)) {
                    // إنشاء القاعدة لأول مرة (مع ترحيل البيانات القديمة إن وجدت) في ملف مؤقت ثم اعتمادها
                    Store s;
                    if (Files.exists(dat)) s = read(dat); else { s = new Store(); s.seed(); }
                    Path tmp = dataDir.resolve("market.db.creating");
                    Files.deleteIfExists(tmp); Files.deleteIfExists(dataDir.resolve("market.db.creating-wal"));
                    SqlStore.open(tmp); SQL = true; db = s;
                    SqlStore.save(s, true); SqlStore.close();
                    Files.move(tmp, dbf, StandardCopyOption.ATOMIC_MOVE);
                    if (Files.exists(dat)) Files.move(dat, dataDir.resolve("market.dat.migrated"), StandardCopyOption.REPLACE_EXISTING);
                }
                db = SqlStore.open(dbf); SQL = true; db.reindex();
                return;
            } catch (Throwable t) {
                SQL = false;
                Main.report(new RuntimeException("تعذر فتح قاعدة SQLite، سيتم استخدام ملف البيانات", t));
                if (Files.exists(dbf) && !Files.exists(dat)) throw new RuntimeException("تعذر فتح قاعدة البيانات: " + t.getMessage(), t);
            }
        }
        try {
            Path f = dataFile();
            if (Files.exists(f)) { db = read(f); }
            else { db = new Store(); db.seed(); saveNow(); }
            db.reindex();
        } catch (Exception e) {
            throw new RuntimeException("تعذر تحميل البيانات: " + e.getMessage(), e);
        }
    }

    public static Store read(Path f) throws IOException, ClassNotFoundException {
        if (f.getFileName().toString().toLowerCase().endsWith(".db")) {
            if (!SqlStore.available()) throw new IOException("هذا الجهاز لا يدعم ملفات قاعدة البيانات .db");
            return SqlStore.readFile(f);
        }
        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(f))))) {
            Store s = (Store) in.readObject();
            if (s.held == null) s.held = new ArrayList<>();
            if (s.users == null) s.users = new ArrayList<>();
            if (s.shifts == null) s.shifts = new ArrayList<>();
            if (s.offers == null) s.offers = new ArrayList<>();
            if (s.warehouses == null) s.warehouses = new ArrayList<>();
            if (s.transfers == null) s.transfers = new ArrayList<>();
            if (s.journal == null) s.journal = new ArrayList<>();
            if (s.accounts == null) s.accounts = new ArrayList<>();
            if (s.batches == null) s.batches = new ArrayList<>();
            if (s.orders == null) s.orders = new ArrayList<>();
            return s;
        }
    }

    private static byte[] snapshot() throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 20);
        try (ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(bo))) { out.writeObject(db); }
        return bo.toByteArray();
    }

    private static void writeBytes(byte[] data) throws IOException {
        synchronized (fileLock) {
            Path tmp = dataDir.resolve("market.tmp");
            Files.write(tmp, data);
            try { Files.move(tmp, dataFile(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, dataFile(), StandardCopyOption.REPLACE_EXISTING); }
        }
    }

    /** حفظ بعد كل عملية: اللقطة تؤخذ فوراً والكتابة على القرص في الخلفية */
    /** يزيد مع كل حفظ — لإبطال الحسابات المخزّنة مؤقتاً */
    public static volatile long rev;
    public static void save() {
        rev++;
        if (SQL) { SqlStore.save(db, false); return; }
        try {
            byte[] data = snapshot();
            saver.submit(() -> { try { writeBytes(data); saveWarned = false; } catch (IOException e) { saveFailed(e); } });
        } catch (IOException e) { saveFailed(e); }
    }
    /** فشل الكتابة على القرص لا يجب أن يمرّ بصمت: يُسجَّل في error.log وينبّه المستخدم مرة واحدة حتى ينجح الحفظ */
    static volatile boolean saveWarned;
    /** تسجيل خطأ غير حرج في error.log دون إزعاج المستخدم (بدل ابتلاعه بصمت) */
    public static void logQuiet(String where, Throwable e) {
        try {
            java.io.StringWriter sw = new java.io.StringWriter(); e.printStackTrace(new java.io.PrintWriter(sw));
            Files.write(dataDir.resolve("error.log"), ("\n==== " + java.time.LocalDateTime.now() + " | " + where + "\n" + sw).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }
    static void saveFailed(Throwable e) {
        e.printStackTrace();
        try {
            java.nio.file.Files.createDirectories(dataDir);
            java.nio.file.Files.write(dataDir.resolve("error.log"), ("\n==== " + java.time.LocalDateTime.now() + " | فشل حفظ الملف\n" + e + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
        if (!saveWarned) {
            saveWarned = true;
            Ui.err("تعذّر حفظ البيانات: " + e.getMessage());
        }
    }

    public static void saveNow() {
        if (SQL) { SqlStore.save(db, false); SqlStore.flush(); return; }
        try { writeBytes(snapshot()); } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    public static void shutdown() {
        saveNow();
        autoBackup();
        CloudBackup.auto(); // نسخة إلى Google Drive إن كانت مفعّلة
        if (SQL) SqlStore.close();
        saver.shutdown();
        try { saver.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }

    public static Path backupTo(Path dir, String prefix) throws IOException {
        Files.createDirectories(dir);
        saveNow();
        String name = prefix + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ext();
        Path out = dir.resolve(name);
        if (SQL) SqlStore.backup(out);
        else synchronized (fileLock) { Files.copy(dataFile(), out, StandardCopyOption.REPLACE_EXISTING); }
        return out;
    }

    /** نسخة احتياطية يومية تلقائية مع الاحتفاظ بآخر 30 نسخة */
    public static void autoBackup() {
        try {
            Path dir = dataDir.resolve("backups");
            Files.createDirectories(dir);
            String today = java.time.LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "auto_" + today + "*")) { if (ds.iterator().hasNext()) return; }
            backupTo(dir, "auto");
            List<Path> all = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "auto_*")) { ds.forEach(all::add); }
            all.sort(Comparator.naturalOrder());
            while (all.size() > 30) Files.deleteIfExists(all.remove(0));
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void restore(Path f) throws Exception {
        Store s = read(f); // تحقق من صلاحية الملف أولاً
        backupTo(dataDir.resolve("backups"), "before_restore");
        db = s; db.reindex();
        if (SQL) { SqlStore.save(db, true); SqlStore.flush(); } else saveNow();
    }
}
