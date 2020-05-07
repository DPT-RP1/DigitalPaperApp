package net.sony.dpt.data.extmgr;

import net.sony.dpt.data.extmgr.entity.App;
import net.sony.dpt.data.extmgr.entity.LauncherEntry;
import net.sony.dpt.data.extmgr.entity.Strings;
import net.sony.util.ResourcesUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ExtMgrDaoTest {

    private Path dbPath;
    private static final String TEST_DB_PATH = "extensions/ExtMgr.db";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() throws URISyntaxException, IOException {
        dbPath = ResourcesUtils.resource(TEST_DB_PATH);

        // We copy the file to avoid committing modifications
        dbPath = Files.copy(dbPath, temporaryFolder.getRoot().toPath().resolve("ExtMgr.db"));
    }

    @Test
    public void canConnect() throws SQLException {
        try(ExtMgrDao ignored = new ExtMgrDao(dbPath, System.out::println)) { }
    }

    @Test
    public void insertExtension() throws SQLException {
        try(ExtMgrDao extMgrDao = new ExtMgrDao(dbPath, System.out::println)) {
            extMgrDao.insertExtension("test", "/app/somewhere", "uri", "MENUICON", "/path/icon", ExtMgrDao.Order.INSERT_LAST);
        }

        try(ExtMgrDao extMgrDao = new ExtMgrDao(dbPath, System.out::println)) {
            App app = new App();
            LauncherEntry launcherEntry = new LauncherEntry();
            List<Strings> strings = new ArrayList<>();
            extMgrDao.fetchAppInfo("test", app, launcherEntry, strings);
            assertEquals("test", app.getName());
            assertEquals("System", app.getType());
            assertEquals(1, app.getVersion());
            assertEquals(2, launcherEntry.getSortOrder());
            assertEquals("test", launcherEntry.getName());
            assertEquals("Launcher", launcherEntry.getCategory());
            assertEquals("fefe3d4a0f263c1c0f521f641ee385c7dea33ca8ffa149de585bce98153916af", strings.get(0).getEntryId());
            assertTrue(launcherEntry.validate());
        }
    }

    @Test
    public void insertExtensionTwice() throws SQLException {
        try(ExtMgrDao extMgrDao = new ExtMgrDao(dbPath, System.out::println)) {
            extMgrDao.insertExtension("test", "/app/somewhere", "uri", "MENUICON", "/path/icon", ExtMgrDao.Order.INSERT_LAST);
            extMgrDao.insertExtension("test", "/app/somewhere/else", "uri2", "OTHERMENUICON", "/path/icon/else", ExtMgrDao.Order.INSERT_FIRST);
        }

        try(ExtMgrDao extMgrDao = new ExtMgrDao(dbPath, System.out::println)) {
            App app = new App();
            LauncherEntry launcherEntry = new LauncherEntry();
            List<Strings> strings = new ArrayList<>();
            extMgrDao.fetchAppInfo("test", app, launcherEntry, strings);
            assertEquals("test", app.getName());
            assertEquals("System", app.getType());
            assertEquals("/app/somewhere/else", app.getInstallPath());
            assertEquals(2, app.getVersion());
            assertEquals(1, launcherEntry.getSortOrder());
            assertEquals("uri2", launcherEntry.getUri());
            assertEquals("test", launcherEntry.getName());
            assertEquals("OTHERMENUICON", launcherEntry.getStringId());
            assertEquals("/path/icon/else", launcherEntry.getIconFile());
            assertEquals("Launcher", launcherEntry.getCategory());
            assertEquals("fefe3d4a0f263c1c0f521f641ee385c7dea33ca8ffa149de585bce98153916af", strings.get(0).getEntryId());
            assertTrue(launcherEntry.validate());
        }
    }

    @Test
    public void deleteExtension() throws SQLException {
        try(ExtMgrDao extMgrDao = new ExtMgrDao(dbPath, System.out::println)) {
            extMgrDao.insertExtension("test", "/app/somewhere", "uri", "MENUICON", "/path/icon", ExtMgrDao.Order.INSERT_LAST);
            extMgrDao.insertExtension("test", "/app/somewhere/else", "uri2", "OTHERMENUICON", "/path/icon/else", ExtMgrDao.Order.INSERT_FIRST);
            assertTrue( extMgrDao.exists("test"));
            assertEquals(3, extMgrDao.deleteIfExists("test"));
            assertFalse( extMgrDao.exists("test"));
        }
    }

    @Test
    public void verifyDatabase() {

    }

}
