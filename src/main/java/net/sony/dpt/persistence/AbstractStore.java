package net.sony.dpt.persistence;

import java.nio.file.Path;

public abstract class AbstractStore {

    // This is the path were all configs are stored. TODO: eventually make configurable ?
    protected static final Path applicationPath = Path.of(".dpt");

    protected final Path storageRoot;
    protected final Path storagePath;

    public AbstractStore(final Path storageRoot) {
        this.storageRoot = storageRoot;
        this.storagePath = storageRoot.resolve(applicationPath);
    }

    public Path getStorageRoot() {
        return storageRoot;
    }

}
