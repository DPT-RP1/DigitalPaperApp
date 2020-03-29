package net.sony.dpt;

import net.sony.dpt.command.authenticate.AuthenticateCommand;
import net.sony.dpt.command.authenticate.AuthenticationCookie;
import net.sony.dpt.command.documents.DocumentListResponse;
import net.sony.dpt.command.documents.ListDocumentsCommand;
import net.sony.dpt.command.documents.TransferDocumentCommand;
import net.sony.dpt.command.register.RegisterCommand;
import net.sony.dpt.command.register.RegistrationResponse;
import net.sony.dpt.persistence.RegistrationTokenStore;
import net.sony.util.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

public class DigitalPaper {

    private final String addr;

    private final SimpleHttpClient simpleHttpClient;
    private final CryptographyUtil cryptographyUtil;
    private final DiffieHelman diffieHelman;

    private final LogWriter logWriter;
    private final InputReader inputReader;

    private final DigitalPaperEndpoint digitalPaperEndpoint;

    public DigitalPaper(String addr,
                        SimpleHttpClient simpleHttpClient,
                        DiffieHelman diffieHelman,
                        CryptographyUtil cryptographyUtil,
                        LogWriter logWriter,
                        InputReader inputReader,
                        DigitalPaperEndpoint digitalPaperEndpoint) {
        this.addr = addr;
        this.simpleHttpClient = simpleHttpClient;
        this.cryptographyUtil = cryptographyUtil;
        this.diffieHelman = diffieHelman;
        this.logWriter = logWriter;
        this.inputReader = inputReader;
        this.digitalPaperEndpoint = digitalPaperEndpoint;
    }

    public static void main(String[] args) throws Exception {
        String addr = "192.168.0.48";

        SimpleHttpClient simpleHttpClient = new SimpleHttpClient();
        DigitalPaperEndpoint digitalPaperEndpoint = new DigitalPaperEndpoint(addr, simpleHttpClient);
        DigitalPaper digitalPaper = new DigitalPaper(
                addr,
                simpleHttpClient,
                new DiffieHelman(),
                new CryptographyUtil(),
                System.out::println,
                () -> {
                    Scanner scanner = new Scanner(System.in);
                    return scanner.next();
                },
                digitalPaperEndpoint
        );

        RegistrationTokenStore registrationTokenStore = new RegistrationTokenStore(Path.of(System.getProperty("user.home")));
        RegistrationResponse registrationResponse;
        if (!registrationTokenStore.registered()) {
            registrationResponse = digitalPaper.register();
            registrationTokenStore.storeRegistrationToken(registrationResponse);
        } else {
            registrationResponse = registrationTokenStore.retrieveRegistrationToken();
        }

        System.out.println(registrationResponse);

        AuthenticationCookie cookie = digitalPaper.authenticate(registrationResponse);
        System.out.println(cookie);
        cookie.insertInCookieManager(digitalPaperEndpoint.getURI(), (CookieManager) CookieHandler.getDefault());
        cookie.insertInRequest(simpleHttpClient::addDefaultHeader);

        DocumentListResponse documents = digitalPaper.listDocuments();
        System.out.println("Documents listed");

        Path testUpload = Path.of("/Users/Pierre/DigitalPaperApp/src/test/resources/sample.pdf");
        String id = new TransferDocumentCommand(digitalPaperEndpoint).upload(testUpload, Path.of("Document/Test/sample.pdf"));
        System.out.println("Document id = " + id);
    }

    public RegistrationResponse register() throws IllegalBlockSizeException, InterruptedException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IOException, BadPaddingException, NoSuchPaddingException, InvalidKeyException {
        return new RegisterCommand(addr, simpleHttpClient, diffieHelman, cryptographyUtil, logWriter, inputReader).register();
    }

    public AuthenticationCookie authenticate(RegistrationResponse registrationResponse) throws Exception {
        return new AuthenticateCommand(addr, digitalPaperEndpoint, cryptographyUtil, simpleHttpClient).authenticate(registrationResponse);
    }

    public DocumentListResponse listDocuments() throws IOException, InterruptedException {
        return new ListDocumentsCommand(digitalPaperEndpoint).listDocuments();
    }

}
