package net.sony.dpt.data.extmgr.entity;

import net.sony.util.CryptographyUtils;
import net.sony.util.HashUtils;

public class LauncherEntry {

    // SHA-256 of appname + name + category
    private String id;
    private String appName;
    private String name;
    private String category;
    private String uri;

    // This looks more like a relic of a past data modeling mistake:
    // - the only other place this is referenced in is the xml file
    // - this is supposed to give the id of the string to use for display, but not the locate
    // - there is a string table able to match app -> locale -> string
    // - Using the same name multiple times causes no visible damage
    private String stringId;

    private String iconFile;
    private int sortOrder;
    private boolean hide;

    public LauncherEntry() {
    }

    public LauncherEntry(String appName, String name, String category, String uri, String stringId, String iconFile, int sortOrder, boolean hide) {
        this.appName = appName;
        this.name = name;
        this.category = category;
        this.uri = uri;
        this.stringId = stringId;
        this.iconFile = iconFile;
        this.sortOrder = sortOrder;
        this.hide = hide;
        this.id = getId();
    }

    public LauncherEntry(String id, String appName, String name, String category, String uri, String stringId, String iconFile, int sortOrder, boolean hide) {
        this.id = id;
        this.appName = appName;
        this.name = name;
        this.category = category;
        this.uri = uri;
        this.stringId = stringId;
        this.iconFile = iconFile;
        this.sortOrder = sortOrder;
        this.hide = hide;
    }

    public String getId() {
        if (id == null && appName != null) {
            id = HashUtils.sha256Hex(appName + name + category).toLowerCase();
        }
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getStringId() {
        return stringId;
    }

    public void setStringId(String stringId) {
        this.stringId = stringId;
    }

    public String getIconFile() {
        return iconFile;
    }

    public void setIconFile(String iconFile) {
        this.iconFile = iconFile;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isHide() {
        return hide;
    }

    public void setHide(boolean hide) {
        this.hide = hide;
    }

    public boolean validate() {
        return id != null
        && id.equals(
                HashUtils.sha256Hex(appName + name + category).toLowerCase()
        );
    }
}
