package net.sony.dpt.data.extmgr;

import net.sony.dpt.data.extmgr.entity.App;
import net.sony.dpt.data.extmgr.entity.LauncherEntry;
import net.sony.dpt.data.extmgr.entity.Strings;
import net.sony.util.LogWriter;

import java.nio.file.Path;
import java.sql.*;
import java.util.List;

/**
 * A simple data access object to edit the Extension Database
 */
public class ExtMgrDao implements AutoCloseable {

    private static final String[] LOCALES = new String[] {"zh_CN", "ja", "en"};

    private static final String JDBC_SQLITE_PREFIX = "jdbc:sqlite:";
    private static final String SELECT_TOP_SORT_ORDER_SQL = "SELECT sort_order FROM launcher_entry_table ORDER BY sort_order DESC LIMIT 1";
    private static final String CHECK_IF_EXISTS_SQL =
            "SELECT launcher.id, app.version " +
            "FROM launcher_entry_table launcher " +
            "INNER JOIN app_table app ON launcher.app_name = app.name " +
            "WHERE launcher.app_name = ?";
    private static final String DELETE_FROM_APPS_SQL = "DELETE FROM app_table WHERE name = ?";
    private static final String DELETE_FROM_LAUNCHER_SQL = "DELETE FROM launcher_entry_table WHERE id = ?";
    private static final String DELETE_FROM_STRING_SQL = "DELETE FROM string_table WHERE entry_id = ?";

    private static final String INSERT_APP_SQL = "INSERT INTO app_table (name, type, version, installed_path) VALUES (?, ?, ?, ?)";
    private static final String INSERT_LAUNCHER_SQL = "INSERT INTO launcher_entry_table (id, app_name, name, category, uri, string_id, icon_file, sort_order, hide) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String INSERT_STRING = "INSERT INTO string_table (entry_id, locale_string, string_value) VALUES (?, ?, ?)";

    private static final String SELECT_APP_SQL = "SELECT * FROM app_table WHERE name = ?";
    private static final String SELECT_LAUNCHER_SQL = "SELECT * FROM launcher_entry_table WHERE app_name = ?";
    private static final String SELECT_STRINGS = "SELECT * FROM string_table WHERE entry_id = ?";


    private final Path dbPath;
    private Connection conn;
    private final LogWriter logWriter;

    public ExtMgrDao(final Path dbPath, final LogWriter logWriter) throws SQLException {
        this.dbPath = dbPath;
        this.logWriter = logWriter;
        connect();
    }

    public void connect() throws SQLException {
        logWriter.log("Connecting to the extension database: " + JDBC_SQLITE_PREFIX + dbPath.toAbsolutePath().toString());
        conn = DriverManager.getConnection(JDBC_SQLITE_PREFIX + dbPath.toAbsolutePath().toString());
        logWriter.log("Connected");
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }

    public enum Order {
        INSERT_LAST,
        INSERT_FIRST
    }

    /**
     * Increments the version if already exists.
     * @param iconStringId STR_ICONMENU_1001 should work by default
     * @param insertMode If INSERT_LAST, the icon appears at the end of every other applications
     */
    public void insertExtension(final String appName,
                                final String installPath,
                                final String uri,
                                final String iconStringId,
                                final String iconPath,
                                final Order insertMode
                                ) throws SQLException {
        int sortOrder = 1;

        int version = deleteIfExists(appName);

        // If you want to insert last, we need to calculate this
        if (insertMode == Order.INSERT_LAST) {
            sortOrder = incrementHighestSortOrder();
        }

        String category = "Launcher";
        String type = "System";

        App app = new App(appName, type, version, installPath);
        LauncherEntry launcherEntry = new LauncherEntry(
                appName,
                appName,
                category,
                uri,
                iconStringId,
                iconPath,
                sortOrder,
                false
        );

        // We dont bother having a name per locale, but it would be trivial to add
        Strings strings = new Strings(launcherEntry.getId(), appName);

        insertApp(app);
        insertLauncher(launcherEntry);
        insertStrings(strings); // Does all locales
    }

    private void insertApp(App app) throws SQLException {
        try(PreparedStatement pstmt = conn.prepareStatement(INSERT_APP_SQL)) {
            pstmt.setString(1, app.getName());
            pstmt.setString(2, app.getType());
            pstmt.setInt(3, app.getVersion());
            pstmt.setString(4, app.getInstallPath());
            pstmt.executeUpdate();
        }
    }

    private void insertLauncher(LauncherEntry entry) throws SQLException {
        try(PreparedStatement pstmt = conn.prepareStatement(INSERT_LAUNCHER_SQL)) {
            pstmt.setString(1, entry.getId());
            pstmt.setString(2, entry.getAppName());
            pstmt.setString(3, entry.getName());
            pstmt.setString(4, entry.getCategory());
            pstmt.setString(5, entry.getUri());
            pstmt.setString(6, entry.getStringId());
            pstmt.setString(7, entry.getIconFile());
            pstmt.setInt(8, entry.getSortOrder());
            pstmt.setBoolean(9, entry.isHide());
            pstmt.executeUpdate();
        }
    }

    private void insertStrings(Strings strings) throws SQLException {
        for (String locale : LOCALES) {
            try(PreparedStatement pstmt = conn.prepareStatement(INSERT_STRING)) {
                pstmt.setString(1, strings.getEntryId());
                pstmt.setString(2, locale);
                pstmt.setString(3, strings.getStringValue());
                pstmt.executeUpdate();
            }
        }
    }

    /**
     * This method cleans up the db of any traces of the previous application, so that a reinstallation makes sense.
     * @return The next version number to use when recreating
     */
    public int deleteIfExists(String appName) throws SQLException {
        String id;
        int version = 1;
        try(PreparedStatement pstmt = conn.prepareStatement(CHECK_IF_EXISTS_SQL)) {
            pstmt.setString(1, appName);
            try(ResultSet rs = pstmt.executeQuery()) {

                if (!rs.next()) return version;

                id = rs.getString("id");
                version = rs.getInt("version") + 1;
            }
        }

        // So now that we have the app name and the id, we can delete from all three tables
        deleteFromApps(appName);
        deleteFromLauncher(id);
        deleteFromString(id);

        return version;
    }

    private void deleteFromString(String id) throws SQLException {
        try(PreparedStatement pstmt = conn.prepareStatement(DELETE_FROM_STRING_SQL)) {
            pstmt.setString(1, id);
            pstmt.executeUpdate();
        }
    }

    private void deleteFromLauncher(String id) throws SQLException {
        try(PreparedStatement pstmt = conn.prepareStatement(DELETE_FROM_LAUNCHER_SQL)) {
            pstmt.setString(1, id);
            pstmt.executeUpdate();
        }
    }


    private void deleteFromApps(String appName) throws SQLException {
        try(PreparedStatement pstmt = conn.prepareStatement(DELETE_FROM_APPS_SQL)) {
            pstmt.setString(1, appName);
            pstmt.executeUpdate();
        }
    }

    private int incrementHighestSortOrder() throws SQLException {

        try(Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(SELECT_TOP_SORT_ORDER_SQL)) {

            if (!rs.next()) return 1; // the sort order starts at 1

            return rs.getInt("sort_order") + 1;
        }
    }

    public boolean fetchAppInfo(String appName, App appResult, LauncherEntry launcherEntryResult, List<Strings> stringsResults) throws SQLException {
        if (appName == null || appResult.getName() != null || launcherEntryResult.getId() != null || !stringsResults.isEmpty()) {
            throw new IllegalArgumentException("You must send empty container objects for the results");
        }
        boolean found = exists(appName);
        if (found) {
            fetchApp(appName, appResult);
            fetchLauncher(appName, launcherEntryResult);
            fetchStrings(launcherEntryResult.getId(), stringsResults);
        }
        return found;
    }

    public boolean exists(String appName) throws SQLException {
        try(PreparedStatement pstmt = conn.prepareStatement(CHECK_IF_EXISTS_SQL)) {
            pstmt.setString(1, appName);
            try(ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }


    }

    private void fetchApp(String name, App appResult) throws SQLException {
        try(PreparedStatement pstmt = conn.prepareStatement(SELECT_APP_SQL)) {
            pstmt.setString(1, name);
            try(ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) return;

                appResult.setName(rs.getString("name"));
                appResult.setType(rs.getString("type"));
                appResult.setVersion(rs.getInt("version"));
                appResult.setInstallPath(rs.getString("installed_path"));
            }
        }
    }

    private void fetchLauncher(String appName, LauncherEntry launcherResult) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(SELECT_LAUNCHER_SQL)) {
            pstmt.setString(1, appName);

            try (ResultSet rs = pstmt.executeQuery()) {

                if (!rs.next()) return;
                launcherResult.setId(rs.getString("id"));
                launcherResult.setAppName(rs.getString("app_name"));
                launcherResult.setName(rs.getString("name"));
                launcherResult.setCategory(rs.getString("category"));
                launcherResult.setUri(rs.getString("uri"));
                launcherResult.setStringId(rs.getString("string_id"));
                launcherResult.setIconFile(rs.getString("icon_file"));
                launcherResult.setSortOrder(rs.getInt("sort_order"));
                launcherResult.setHide(rs.getBoolean("hide"));
            }
        }
    }

    private void fetchStrings(String id, List<Strings> stringsList) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(SELECT_STRINGS)) {
            pstmt.setString(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {

                if (!rs.next()) return;
                do {
                    Strings strings = new Strings();
                    strings.setEntryId(rs.getString("entry_id"));
                    strings.setLocaleString(rs.getString("locale_string"));
                    strings.setStringValue(rs.getString("string_value"));
                    stringsList.add(strings);
                } while (rs.next());
            }
        }
    }

    /**
     * This will check the entire database to verify:
     *  - The table are coherent
     *  - The filesystem makes sense
     *  TODO: implement if really interesting
     *  TODO: use a special parameter to callback adb pull and check the FS (like ExtensionChecker or whatnot)
     * @return true as long as not implemented
     */
    public boolean checkIntegrity(final List<String> extensionsExpected) {
        return true;
    }

}
