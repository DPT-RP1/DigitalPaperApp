package net.sony.dpt.command.documents;

import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ListDocumentCommandTest {

    @Test
    public void entryListCanDeserialize() throws IOException {
        String pdf_list = new String(getClass().getClassLoader().getResourceAsStream("pdf_list.json").readAllBytes());
        DocumentListResponse documentListResponse = ListDocumentsCommand.fromJson(pdf_list);

        Set<EntryType> entryTypes = new HashSet<>();
        for (DocumentEntry entry : documentListResponse.getEntryList()) {
            entryTypes.add(entry.getEntryType());
        }
        assertEquals(documentListResponse.getCount(), documentListResponse.getEntryList().size());
    }

}
