package net.sony.dpt.data.extmgr.entity;

public class App {

    private String name;

    // The only known type is System
    private String type;

    private int version;

    private String installPath;

    public App() {
    }

    public App(String name, String type, int version, String installPath) {
        this.name = name;
        this.type = type;
        this.version = version;
        this.installPath = installPath;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getInstallPath() {
        return installPath;
    }

    public void setInstallPath(String installPath) {
        this.installPath = installPath;
    }
}
