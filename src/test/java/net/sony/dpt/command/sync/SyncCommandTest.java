package net.sony.dpt.command.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sony.dpt.command.documents.DocumentListResponse;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    public void initWithEmptyLocalFolderShouldFetchAll() throws IOException, InterruptedException, URISyntaxException {
        List<String> messages = new ArrayList<>();

        SyncCommand syncCommand = new SyncCommand(null, null, null, null, message -> {
            if (message.contains("We will")) messages.add(message);
        }, null, null);

        syncCommand.loadLocalDocuments(Path.of(
                Objects.requireNonNull(this.getClass().getClassLoader().getResource("empty")).toURI())
        , true);

        syncCommand.loadRemoteDocuments(documentListResponse);

        syncCommand.sync(null, true);
        assertThat(messages.size(), is(4));
        assertThat(messages.get(0), is("We will send 0 files to the Digital Paper"));
        assertThat(messages.get(1), is("We will receive 622 files from the Digital Paper"));
        assertThat(messages.get(2), is("We will delete 0 files locally"));
        assertThat(messages.get(3), is("We will delete 0 files remotely"));
    }

    @Test
    public void initWithEmptyRemoteFolderShouldSendAll() throws IOException, InterruptedException {
        List<String> messages = new ArrayList<>();

        SyncCommand syncCommand = new SyncCommand(null, null, null, null, message -> {
            if (message.contains("We will")) messages.add(message);
        }, null, null);

        syncCommand.loadLocalDocuments(
                Path.of(
                        Objects.requireNonNull(this.getClass().getClassLoader().getResource("sync")).getPath()
                ), true
        );

        DocumentListResponse empty = new DocumentListResponse();
        empty.setEntryList(new ArrayList<>());
        syncCommand.loadRemoteDocuments(empty);

        syncCommand.sync(null, true);
        assertThat(messages.size(), is(4));
        assertThat(messages.get(0), is("We will send 2 files to the Digital Paper"));
        assertThat(messages.get(1), is("We will receive 0 files from the Digital Paper"));
        assertThat(messages.get(2), is("We will delete 0 files locally"));
        assertThat(messages.get(3), is("We will delete 0 files remotely"));
    }

    @Test
    public void bothFoldersEmptyShouldDoNothing() throws IOException, InterruptedException, URISyntaxException {
        List<String> messages = new ArrayList<>();

        SyncCommand syncCommand = new SyncCommand(null, null, null, null, messages::add, null, null);

        syncCommand.loadLocalDocuments(
                Path.of(
                        Objects.requireNonNull(this.getClass().getClassLoader().getResource("empty")).toURI()
                ), true
        );

        DocumentListResponse empty = new DocumentListResponse();
        empty.setEntryList(new ArrayList<>());
        syncCommand.loadRemoteDocuments(empty);

        syncCommand.sync(null, true);
        assertThat(messages.size(), is(6));
        assertThat(messages.get(1), is("There is nothing to synchronize: both your local folder and the device are empty."));
        assertThat(messages.get(2), is("We will send 0 files to the Digital Paper"));
        assertThat(messages.get(3), is("We will receive 0 files from the Digital Paper"));
        assertThat(messages.get(4), is("We will delete 0 files locally"));
        assertThat(messages.get(5), is("We will delete 0 files remotely"));

    }

}
