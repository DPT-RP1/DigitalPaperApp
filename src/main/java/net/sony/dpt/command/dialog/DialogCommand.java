package net.sony.dpt.command.dialog;

import net.sony.dpt.DigitalPaperEndpoint;

import java.io.IOException;
import java.util.UUID;

public class DialogCommand {

    private final DigitalPaperEndpoint digitalPaperEndpoint;

    public DialogCommand(final DigitalPaperEndpoint digitalPaperEndpoint) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
    }

    public void show(String title, String text, String buttonText) throws IOException, InterruptedException {
        digitalPaperEndpoint.showDialog(title, text, buttonText);
    }

    /**
     * If a UUID is specified, it will edit an existing dialog. If null, it will create a new one.
     *
     * @param UUID       A UUID identifier for the dialog. Allows to edit dialog along the way
     * @param title      The title of the dialog
     * @param text       The content of the dialog
     * @param buttonText The text on the "hide" button
     * @return The UUID of the dialog
     */
    public UUID show(UUID UUID, String title, String text, String buttonText, boolean showLoadingIcon) throws IOException, InterruptedException {
        if (UUID == null) {
            UUID = java.util.UUID.randomUUID();
        }

        digitalPaperEndpoint.showDialog(UUID.toString(), title, text, buttonText, showLoadingIcon);

        return UUID;
    }

    public void hide(UUID modalId) throws IOException, InterruptedException {
        digitalPaperEndpoint.hideDialog(modalId.toString());
    }
}
