package net.sony.dpt.command.root;

import net.sony.dpt.root.DiagnosticManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DiagnosticCommand {

    private final DiagnosticManager diagnosticManager;

    public DiagnosticCommand(final DiagnosticManager diagnosticManager) {
        this.diagnosticManager = diagnosticManager;
    }

    public void fetchFile(Path remote, Path local) throws IOException, InterruptedException {
        diagnosticManager.automount();
        byte[] file = diagnosticManager.fetchFile(remote);

        Files.write(local, file);
    }

    public void exitDiagnosticMode() throws IOException, InterruptedException {
        diagnosticManager.logout();
    }

}
