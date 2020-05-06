package net.sony.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Objects;

public class ResourcesUtils {

    public static InputStream inputStream(final String resourcePath) {
        return ResourcesUtils.class.getClassLoader().getResourceAsStream(resourcePath);
    }

    public static Path folder(final String resourceFolderPath) throws IOException, URISyntaxException {
        URI uri = Objects.requireNonNull(ResourcesUtils.class.getClassLoader().getResource(resourceFolderPath)).toURI();
        if ("jar".equals(uri.getScheme())) {
            FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap(), null);
            return fileSystem.getPath(resourceFolderPath);
        } else {
            return Paths.get(uri);
        }
    }
}
