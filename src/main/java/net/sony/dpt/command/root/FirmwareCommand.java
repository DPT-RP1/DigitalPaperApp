package net.sony.dpt.command.root;

import net.sony.dpt.root.FirmwarePacker;
import net.sony.util.CryptographyUtils;
import net.sony.util.LogWriter;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

public class FirmwareCommand {

    private final LogWriter logWriter;
    private final FirmwarePacker firmwarePacker;

    public FirmwareCommand(final LogWriter logWriter, final FirmwarePacker firmwarePacker) {
        this.logWriter = logWriter;
        this.firmwarePacker = firmwarePacker;
    }

    public void unpack(final Path pkg, final Path target) throws IOException, NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, SignatureException, InvalidAlgorithmParameterException {
        InputStream firmware = Files.newInputStream(pkg);

        Files.createDirectories(target);

        Path targetData = target.resolve("decryptedData.tar.gz");
        Path targetAnimation = target.resolve("decryptedAnimation.tar.gz");

        firmwarePacker.unpack(firmware, targetData, targetAnimation);
        logWriter.log("Unpacked data to " + targetData);
        logWriter.log("Unpacked animation to " + targetAnimation);
    }

}
