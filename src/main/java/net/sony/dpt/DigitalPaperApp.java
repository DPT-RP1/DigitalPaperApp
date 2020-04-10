package net.sony.dpt;

import net.sony.dpt.persistence.DeviceInfoStore;
import net.sony.dpt.persistence.RegistrationTokenStore;
import net.sony.dpt.persistence.SyncStore;
import net.sony.dpt.ui.cli.Command;
import net.sony.dpt.ui.cli.DigitalPaperCLI;
import net.sony.util.CryptographyUtils;
import net.sony.util.DiffieHelman;

import java.nio.file.Path;
import java.util.Scanner;

public class DigitalPaperApp {

    public static void main(String[] args)  {
        DigitalPaperCLI digitalPaperCLI = new DigitalPaperCLI(
                new DiffieHelman(),
                new CryptographyUtils(),
                System.out::println,
                () -> {
                    Scanner scanner = new Scanner(System.in);
                    return scanner.next();
                },
                new RegistrationTokenStore(Path.of(System.getProperty("user.home"))),
                new SyncStore(Path.of(System.getProperty("user.home"))),
                new DeviceInfoStore(Path.of(System.getProperty("user.home")))
        );
        try {
            digitalPaperCLI.execute(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(Command.printHelp());
        }
    }

}
