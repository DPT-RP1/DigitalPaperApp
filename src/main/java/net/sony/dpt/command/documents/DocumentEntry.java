package net.sony.dpt.command.documents;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

public class DocumentEntry {

    private String author;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'hh:mm:ss'Z'")
    @JsonProperty("created_date")
    private Date createDate;

    @JsonProperty("current_page")
    private long currentPage;
    @JsonProperty("document_source")
    private String documentSource;
    @JsonProperty("document_type")
    private String documentType;
    @JsonProperty("entry_id")
    private String entryId;
    @JsonProperty("entry_name")
    private String entryName;
    @JsonProperty("entry_path")
    private String entryPath;
    @JsonProperty("entry_type")
    private EntryType entryType;
    @JsonProperty("file_revision")
    private String fileRevision;
    @JsonProperty("file_size")
    private long fileSize;
    @JsonProperty("is_new")
    private boolean isNew;
    @JsonProperty("mime_type")
    private String mimeType;

    @JsonProperty("modified_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'hh:mm:ss'Z'")
    private Date modifiedDate;

    @JsonProperty("parent_folder_id")
    private String parentFolderId;

    @JsonProperty("reading_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'hh:mm:ss'Z'")
    private Date readingDate;

    private String title;
    @JsonProperty("total_page")
    private long totalPage;
    @JsonProperty("ext_id")
    private String extId;

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public String getDocumentSource() {
        return documentSource;
    }

    public void setDocumentSource(String documentSource) {
        this.documentSource = documentSource;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getEntryId() {
        return entryId;
    }

    public void setEntryId(String entryId) {
        this.entryId = entryId;
    }

    public String getEntryName() {
        return entryName;
    }

    public void setEntryName(String entryName) {
        this.entryName = entryName;
    }

    public String getEntryPath() {
        return entryPath;
    }

    public void setEntryPath(String entryPath) {
        this.entryPath = entryPath;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public String getFileRevisiion() {
        return fileRevision;
    }

    public void setFileRevisiion(String fileRevision) {
        this.fileRevision = fileRevision;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean aNew) {
        isNew = aNew;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Date getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(Date modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getParentFolderId() {
        return parentFolderId;
    }

    public void setParentFolderId(String parentFolderId) {
        this.parentFolderId = parentFolderId;
    }

    public Date getReadingDate() {
        return readingDate;
    }

    public void setReadingDate(Date readingDate) {
        this.readingDate = readingDate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getTotalPage() {
        return totalPage;
    }

    public void setTotalPage(long totalPage) {
        this.totalPage = totalPage;
    }



    @Override
    public String toString() {
        return "author: " + author +
                ", createDate: " + createDate +
                ", currentPage: " + currentPage +
                ", documentSource: " + documentSource +
                ", documentType: " + documentType +
                ", entryId: " + entryId +
                ", entryName: " + entryName +
                ", entryPath: " + entryPath +
                ", entryType: " + entryType +
                ", fileRevision: " + fileRevision +
                ", fileSize: " + fileSize +
                ", isNew: " + isNew +
                ", mimeType: " + mimeType +
                ", modifiedDate: " + modifiedDate +
                ", parentFolderId: " + parentFolderId +
                ", readingDate: " + readingDate +
                ", title: " + title +
                ", totalPage: " + totalPage +
                ", extId: " + extId;
    }
}
