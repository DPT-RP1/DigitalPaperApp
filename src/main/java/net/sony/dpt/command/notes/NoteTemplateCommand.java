package net.sony.dpt.command.notes;

import net.sony.dpt.network.DigitalPaperEndpoint;

import java.io.IOException;
import java.nio.file.Path;

public class NoteTemplateCommand {

    private final DigitalPaperEndpoint digitalPaperEndpoint;

    public NoteTemplateCommand(final DigitalPaperEndpoint digitalPaperEndpoint) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
    }

    public void insertNoteTemplate(String templateName, Path sourceFile) throws IOException, InterruptedException {
        String id = digitalPaperEndpoint.createNoteTemplate(templateName);
        digitalPaperEndpoint.insertNoteTemplateFile(id, sourceFile);
    }

}
