package net.sony.dpt.root;

import net.sony.util.PosixUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * This will use the FactoryReset.pkg provided by Sony as base signed package, with a fake animation signature
 * appended at the end, which will be used as IV of the openssl command by the standard Sony updater:
 * 1. dd if=$1 of=$ANIM_SIG_FILE bs=1 skip=$(($ANIM_HEADER_OFFSET + 12)) count=$(($ANIM_SIG_SIZE)) 2>/dev/null
 * 2. /tmp/anim_sig.dat is a tar.gz, the "payload" after 1.
 * 2. The IV contains "55  -none  -in /tmp/anim_sig.dat", which simply echoes /tmp/anim_sig.dat binary content (-none forces openssl to do nothing)
 * 3. openssl enc -d -aes-256-cbc -K `cat ${AES256_KEY}` -iv `cat ${IV}` simply echoes /tmp/anim_sig.dat to the successive pipe
 * 4. | tar -xz -C $2 will therefore extract our "payload" as if it was the data tar.gz used to update
 */
public class RootPacker {

    private static final String BASE_LOCATION = "root/FactoryReset.pkg";
    private static final String FAKE_IV = "55  -none  -in /tmp/anim_sig.dat";
    private static final String STANDARD_ROOT_RESOURCE_LOCATION = "root/updates/standard";

    public void appendBase(final byte[] basePackage, final ByteBuffer buffer) {
        buffer.clear();
        buffer.put(basePackage);
    }

    // dd bs=8 count=4 seek=67
    public void appendFakeIV(final ByteBuffer buffer) {
        int position = buffer.position();
        buffer.position(67 * 8);
        buffer.put(FAKE_IV.getBytes(StandardCharsets.US_ASCII));
        buffer.position(position);
    }

    // 00 00 00 00 headerSize int
    // 00 00 00 00 animationDataSize int
    public void paddUpToAnimationSig(final ByteBuffer buffer) {
        buffer.putInt(0);
        buffer.putInt(0);
    }

    public void appendPayloadAsAnimationSignature(final byte[] payload, final ByteBuffer buffer) {
        buffer.putInt(payload.length);
        buffer.put(payload);
    }

    public byte[] createRootPackage(final InputStream basePackage, final byte[] tarGzPayload) throws IOException {
        byte[] basePackageBytes = IOUtils.toByteArray(basePackage);

        ByteBuffer wrap = ByteBuffer.allocate(basePackageBytes.length + 4 * 3 + tarGzPayload.length);
        wrap.order(ByteOrder.LITTLE_ENDIAN);

        appendBase(basePackageBytes, wrap);
        appendFakeIV(wrap);
        paddUpToAnimationSig(wrap);
        appendPayloadAsAnimationSignature(tarGzPayload, wrap);

        return wrap.array();
    }

    public void untarGz(byte[] tarGz, Path targetFolder) throws IOException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(tarGz);
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
             TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(gzipInputStream)) {

            TarArchiveEntry tarArchiveEntry;
            while ((tarArchiveEntry = tarArchiveInputStream.getNextTarEntry()) != null) {
                tarArchiveEntry.getMode();

            }

        }
    }

    public byte[] tarGz(Path location) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
             GzipCompressorOutputStream gzipOutputStream = new GzipCompressorOutputStream(bufferedOutputStream);
             TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(gzipOutputStream)) {

            tarArchiveOutputStream.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            List<File> files = new ArrayList<>(FileUtils.listFilesAndDirs(
                    location.resolve("FwUpdater").toFile(),
                    new RegexFileFilter("^(.*?)"),
                    DirectoryFileFilter.DIRECTORY
            ));
            files.sort(new PosixUtils.SortIgnoreCase());
            for (File file : files) {
                Path path = Path.of(file.toURI());
                String relativeFilePath = location.relativize(path).toString();

                TarArchiveEntry tarEntry = new TarArchiveEntry(path.toFile(), relativeFilePath);
                tarEntry.setSize(Files.size(path));
                tarEntry.setUserName("dptrp1");
                tarEntry.setGroupName("staff");
                tarEntry.setIds(501, 20);
                tarEntry.setMode(PosixUtils.mode(path));

                tarArchiveOutputStream.putArchiveEntry(tarEntry);
                if (file.isFile()) tarArchiveOutputStream.write(Files.readAllBytes(path));
                tarArchiveOutputStream.closeArchiveEntry();
            }
        }
        return outputStream.toByteArray();
    }

    public byte[] createStandardRootPackage() throws URISyntaxException, IOException {
        InputStream basePackage = RootPacker.class.getClassLoader().getResourceAsStream(BASE_LOCATION);

        return createRootPackage(basePackage, tarGz(Path.of(Objects.requireNonNull(RootPacker.class.getClassLoader().getResource(STANDARD_ROOT_RESOURCE_LOCATION)).toURI())));
    }

    public void unpackRootPackage(InputStream rootPackage, Path targetPath) throws IOException {
        // To unpack, the idea is to simply get the tar at the animation offset
        byte[] rootPackageBytes = IOUtils.toByteArray(rootPackage);
        PkgWrap rootPackageWrap = new PkgWrap(rootPackageBytes);

        untarGz(rootPackageWrap.getAnimationSignature(), null);

        Files.write(targetPath, rootPackageWrap.getAnimationSignature());
    }


}
