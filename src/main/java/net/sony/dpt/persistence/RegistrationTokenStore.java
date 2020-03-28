package net.sony.dpt.persistence;

import net.sony.dpt.command.register.RegistrationResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RegistrationTokenStore {

    private static final Path privateKeyPath = Path.of("id_rsa");
    private static final Path clientIdPath = Path.of("client_id.dat");
    private static final Path certificatePath = Path.of("cert.pem");
    private static final Path applicationPath = Path.of(".dpt");
    private Path storagePath;

    public RegistrationTokenStore(Path storagePath) {
        this.storagePath = storagePath.resolve(applicationPath);
    }

    public void storeRegistrationToken(RegistrationResponse registrationResponse) throws IOException {
        Files.createDirectories(storagePath);

        try {
            Files.createFile(storagePath.resolve(privateKeyPath));
            Files.createFile(storagePath.resolve(clientIdPath));
            Files.createFile(storagePath.resolve(certificatePath));
        } catch (FileAlreadyExistsException ignored) {
        }

        Files.write(storagePath.resolve(privateKeyPath), registrationResponse.getPrivateKey().getBytes(StandardCharsets.UTF_8));
        Files.write(storagePath.resolve(clientIdPath), registrationResponse.getClientId().getBytes(StandardCharsets.UTF_8));
        Files.write(storagePath.resolve(certificatePath), registrationResponse.getPemCertificate().getBytes(StandardCharsets.UTF_8));
    }

    public RegistrationResponse retrieveRegistrationToken() throws IOException {
        String privateKey = Files.readString(storagePath.resolve(privateKeyPath));
        String clientId = Files.readString(storagePath.resolve(clientIdPath));
        String certificate = Files.readString(storagePath.resolve(certificatePath));

        return new RegistrationResponse(certificate, privateKey, clientId);
    }

    public boolean registered() {
        return Files.exists(storagePath)
                && Files.exists(storagePath.resolve(privateKeyPath))
                && Files.exists(storagePath.resolve(clientIdPath))
                && Files.exists(storagePath.resolve(certificatePath));
    }

}
