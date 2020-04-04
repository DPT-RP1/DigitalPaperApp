package net.sony.dpt;

import net.sony.dpt.persistence.DeviceInfoStore;
import net.sony.dpt.persistence.RegistrationTokenStore;
import net.sony.dpt.persistence.SyncStore;
import net.sony.dpt.ui.cli.DigitalPaperCLI;
import net.sony.util.CryptographyUtil;
import net.sony.util.DiffieHelman;

import java.nio.file.Path;
import java.util.Scanner;

public class DigitalPaperApp {

    public static void main(String[] args) throws Exception {
        DigitalPaperCLI digitalPaperCLI = new DigitalPaperCLI(
                new DiffieHelman(),
                new CryptographyUtil(),
                System.out::println,
                () -> {
                    Scanner scanner = new Scanner(System.in);
                    return scanner.next();
                },
                new RegistrationTokenStore(Path.of(System.getProperty("user.home"))),
                new SyncStore(Path.of(System.getProperty("user.home"))),
                new DeviceInfoStore(Path.of(System.getProperty("user.home")))
        );
        digitalPaperCLI.execute(args);
    }

}
