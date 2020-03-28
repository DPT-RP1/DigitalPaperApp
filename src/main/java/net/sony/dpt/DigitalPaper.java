package net.sony.dpt;

import net.sony.dpt.command.register.RegisterCommand;
import net.sony.dpt.command.register.RegistrationResponse;
import net.sony.util.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
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

    public DigitalPaper(String addr,
                        SimpleHttpClient simpleHttpClient,
                        DiffieHelman diffieHelman,
                        CryptographyUtil cryptographyUtil,
                        LogWriter logWriter,
                        InputReader inputReader) {
        this.addr = addr;
        this.simpleHttpClient = simpleHttpClient;
        this.cryptographyUtil = cryptographyUtil;
        this.diffieHelman = diffieHelman;
        this.logWriter = logWriter;
        this.inputReader = inputReader;
    }

    public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, BadPaddingException, InvalidAlgorithmParameterException, IllegalBlockSizeException {
        RegistrationResponse registrationResponse = new DigitalPaper(
                "192.168.0.48",
                new SimpleHttpClient(),
                new DiffieHelman(),
                new CryptographyUtil(),
                System.out::println,
                () -> {
                    Scanner scanner = new Scanner(System.in);
                    return scanner.next();
                }
        ).register();
        System.out.println(registrationResponse);
    }

    public RegistrationResponse register() throws IllegalBlockSizeException, InterruptedException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IOException, BadPaddingException, NoSuchPaddingException, InvalidKeyException {
        return new RegisterCommand(addr, simpleHttpClient, diffieHelman, cryptographyUtil, logWriter, inputReader).register();
    }

}
