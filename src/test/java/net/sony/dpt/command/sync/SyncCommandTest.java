package net.sony.dpt.command.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sony.dpt.command.documents.DocumentListResponse;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class SyncCommandTest {

    private DocumentListResponse documentListResponse;

    @Before
    public void setup() throws IOException {
        documentListResponse = new ObjectMapper().readValue(
                getClass().getClassLoader().getResourceAsStream("pdf_list.json"),
                DocumentListResponse.class
        );
    }

    @Test
    public void initWithEmptyLocalFolderShouldFetchAll() throws IOException {
        List<String> messages = new ArrayList<>();

        SyncCommand syncCommand = new SyncCommand(null, null, null, message -> {
            if (message.contains("Fetching")) messages.add(message);
        }, null);

        syncCommand.loadLocalDocuments(Path.of(""));

        syncCommand.loadRemoteDocuments(documentListResponse);

        syncCommand.sync(null);
        assertThat(messages.size(), is((int) documentListResponse.getCount()));
    }

    @Test
    public void initWithEmptyRemoteFolderShouldSendAll() throws IOException {
        List<String> messages = new ArrayList<>();

        SyncCommand syncCommand = new SyncCommand(null, null, null, message -> {
            if (message.contains("Sending")) messages.add(message);
        }, null);

        syncCommand.loadLocalDocuments(
                Path.of(
                        this.getClass().getClassLoader().getResource("sync").getPath()
                )
        );

        DocumentListResponse empty = new DocumentListResponse();
        empty.setEntryList(new ArrayList<>());
        syncCommand.loadRemoteDocuments(empty);

        syncCommand.sync(null);
        assertThat(messages.size(), is(2));
    }

    @Test
    public void bothFoldersEmptyShouldDoNothing() throws IOException {
        List<String> messages = new ArrayList<>();

        SyncCommand syncCommand = new SyncCommand(null, null, null, messages::add, null);

        syncCommand.loadLocalDocuments(
                Path.of(
                        this.getClass().getClassLoader().getResource("empty").getPath()
                )
        );

        DocumentListResponse empty = new DocumentListResponse();
        empty.setEntryList(new ArrayList<>());
        syncCommand.loadRemoteDocuments(empty);

        syncCommand.sync(null);
        assertThat(messages.size(), is(1));
        assertThat(messages.get(0), is("There is nothing to sync: both your local folder and the device are empty."));
    }

}
