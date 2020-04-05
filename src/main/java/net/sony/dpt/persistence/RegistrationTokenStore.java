package net.sony.dpt.persistence;

import net.sony.dpt.command.register.RegistrationResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class RegistrationTokenStore {

    public enum TokenStoreLocation {
        DEFAULT(".dpt", "id_rsa", "cert.pem", "client_id.dat"),
        PYTHON(".dpapp", "privatekey.dat", null, "deviceid.dat"),
        OFFICIAL_WINDOWS("AppData/Roaming/Sony Corporation/Digital Paper App/DigitalPaperApp", "privatekey.dat", null, "deviceid.dat"),
        OFFICIAL_MAC("Library/Application Support/Sony Corporation/Digital Paper App/DigitalPaperApp", "privatekey.dat", null, "deviceid.dat");

        private final Path applicationPath;
        private final Path privateKeyName;
        private Path certName;
        private final Path clientIdName;

        TokenStoreLocation(String applicationPath, String privateKeyName, String certName, String clientIdName) {
            this.applicationPath = Path.of(applicationPath);
            this.privateKeyName = Path.of(privateKeyName);
            if (certName != null) this.certName = Path.of(certName);
            this.clientIdName = Path.of(clientIdName);
        }

        public static TokenStoreLocation[] nonDefaultLocations() {
            return Arrays.stream(values()).filter(tokenStoreLocation -> tokenStoreLocation != DEFAULT).toArray(TokenStoreLocation[]::new);
        }
    }

    private final Path root;

    public RegistrationTokenStore(Path storageRoot) {
        this.root = storageRoot;
    }

    public void storeRegistrationToken(RegistrationResponse registrationResponse) throws IOException {
        storeRegistrationToken(registrationResponse, TokenStoreLocation.DEFAULT);
    }


    public void storeRegistrationToken(RegistrationResponse registrationResponse, TokenStoreLocation tokenStoreLocation) throws IOException {
        Path storagePath = root.resolve(tokenStoreLocation.applicationPath);
        Path privateKeyPath = tokenStoreLocation.privateKeyName;
        Path clientIdPath = tokenStoreLocation.clientIdName;
        Path certificatePath = tokenStoreLocation.certName;

        Path lastWorkspace = findLastWorkspacePath(tokenStoreLocation);

        Files.createDirectories(storagePath.resolve(lastWorkspace));

        try {
            Files.createFile(storagePath.resolve(lastWorkspace).resolve(privateKeyPath));
            Files.createFile(storagePath.resolve(lastWorkspace).resolve(clientIdPath));
            if (certificatePath != null) Files.createFile(storagePath.resolve(lastWorkspace).resolve(certificatePath));
        } catch (FileAlreadyExistsException ignored) { }

        Files.write(storagePath.resolve(lastWorkspace).resolve(privateKeyPath), registrationResponse.getPrivateKey().getBytes(StandardCharsets.UTF_8));
        Files.write(storagePath.resolve(lastWorkspace).resolve(clientIdPath), registrationResponse.getClientId().getBytes(StandardCharsets.UTF_8));
        if (certificatePath != null) Files.write(storagePath.resolve(lastWorkspace).resolve(certificatePath), registrationResponse.getPemCertificate().getBytes(StandardCharsets.UTF_8));
    }

    public RegistrationResponse retrieveRegistrationToken() throws IOException {
        return retrieveRegistrationToken(TokenStoreLocation.DEFAULT);
    }

    public RegistrationResponse retrieveRegistrationToken(TokenStoreLocation tokenStoreLocation) throws IOException {
        Path storagePath = root.resolve(tokenStoreLocation.applicationPath);
        Path privateKeyPath = tokenStoreLocation.privateKeyName;
        Path clientIdPath = tokenStoreLocation.clientIdName;
        Path certificatePath = tokenStoreLocation.certName;

        Path lastWorkspace = findLastWorkspacePath(tokenStoreLocation);

        String privateKey = Files.readString(storagePath.resolve(lastWorkspace).resolve(privateKeyPath));
        String clientId = Files.readString(storagePath.resolve(lastWorkspace).resolve(clientIdPath));

        String certificate = null;
        // The certificate is optional, as it's only used for SSL with hostname validation
        if (certificatePath != null)
            certificate = Files.readString(storagePath.resolve(lastWorkspace).resolve(certificatePath));

        return new RegistrationResponse(certificate, privateKey, clientId);
    }

    public boolean registered(TokenStoreLocation tokenStoreLocation) {
        Path storagePath = root.resolve(tokenStoreLocation.applicationPath);
        Path privateKeyPath = tokenStoreLocation.privateKeyName;
        Path clientIdPath = tokenStoreLocation.clientIdName;
        Path lastWorkspace;
        try {
            lastWorkspace = findLastWorkspacePath(tokenStoreLocation);
        } catch (IOException ignored) {
            return false;
        }

        return Files.exists(storagePath)
                && Files.exists(storagePath.resolve(lastWorkspace))
                && Files.exists(storagePath.resolve(lastWorkspace).resolve(privateKeyPath))
                && Files.exists(storagePath.resolve(lastWorkspace).resolve(clientIdPath));
    }

    public Path findLastWorkspacePath(TokenStoreLocation tokenStoreLocation) throws IOException {
        switch (tokenStoreLocation) {
            case PYTHON:
            case DEFAULT:
                return Path.of("");
            case OFFICIAL_MAC:
            case OFFICIAL_WINDOWS:
                Path applicationPath = root.resolve(tokenStoreLocation.applicationPath);
                Path lastWorkspaceIdDescriptorPath = Path.of("lastworkspaceid.dat");
                return Path.of(
                    Files.readString(
                        applicationPath.resolve(
                            lastWorkspaceIdDescriptorPath
                        )
                    )

                );
            default:
                throw new IllegalArgumentException("Unknown location " + tokenStoreLocation.name());
        }
    }

    /**
     * Looks for registration everywhere possible
     * @return true if registered anywhere and migrated if not registered on the default path, false otherwise
     */
    public boolean registered() throws IOException {
        if (registered(TokenStoreLocation.DEFAULT)) return true; // We're already registered on this application
        for (TokenStoreLocation tokenStoreLocation : TokenStoreLocation.nonDefaultLocations()) {
            if (registered(tokenStoreLocation)) {
                storeRegistrationToken(retrieveRegistrationToken(tokenStoreLocation));
                return registered(TokenStoreLocation.DEFAULT); // We re-check that this time we're default-registered
            }
        }
        return false;
    }
}
