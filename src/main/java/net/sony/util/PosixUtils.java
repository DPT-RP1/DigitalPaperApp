package net.sony.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

import static org.apache.commons.compress.archivers.tar.TarArchiveEntry.DEFAULT_DIR_MODE;
import static org.apache.commons.compress.archivers.tar.TarArchiveEntry.DEFAULT_FILE_MODE;

/** Utilities for files on Posix based systems. */
public class PosixUtils {

    public static int mode(Path path) throws IOException {
        return permissionsToMode(Files.getPosixFilePermissions(path));
    }

    /** Convert integer mode to {@link PosixFilePermission} object. */
    public static Set<PosixFilePermission> getPosixFilePermissions(int mode) {
        Set<PosixFilePermission> result = EnumSet.noneOf(PosixFilePermission.class);

        if ((mode & 0400) != 0) {
            result.add(PosixFilePermission.OWNER_READ);
        }
        if ((mode & 0200) != 0) {
            result.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((mode & 0100) != 0) {
            result.add(PosixFilePermission.OWNER_EXECUTE);
        }
        if ((mode & 040) != 0) {
            result.add(PosixFilePermission.GROUP_READ);
        }
        if ((mode & 020) != 0) {
            result.add(PosixFilePermission.GROUP_WRITE);
        }
        if ((mode & 010) != 0) {
            result.add(PosixFilePermission.GROUP_EXECUTE);
        }
        if ((mode & 04) != 0) {
            result.add(PosixFilePermission.OTHERS_READ);
        }
        if ((mode & 02) != 0) {
            result.add(PosixFilePermission.OTHERS_WRITE);
        }
        if ((mode & 01) != 0) {
            result.add(PosixFilePermission.OTHERS_EXECUTE);
        }
        return result;
    }

    public static int permissionsToMode(Set<PosixFilePermission> permissions) {
        PosixFilePermission[] allPermissions = PosixFilePermission.values();
        int result = 0;
        for (PosixFilePermission allPermission : allPermissions) {
            result <<= 1;
            result |= permissions.contains(allPermission) ? 1 : 0;
        }
        return result;
    }

    public static class SortIgnoreCase implements Comparator<File> {
        public int compare(File f1, File f2) {
            return f1.getPath().toLowerCase().compareTo(f2.getPath().toLowerCase());
        }
    }
}
