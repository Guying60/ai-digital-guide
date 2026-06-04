package com.example.digitaltourguide.model.admin;

public class FileItem {
    private Long id;
    private String ossUrl;
    private String fileName;
    private String fileType;

    public FileItem() {
    }

    public FileItem(String ossUrl, String fileName, String fileType) {
        this.ossUrl = ossUrl;
        this.fileName = fileName;
        this.fileType = fileType;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOssUrl() {
        return ossUrl;
    }

    public void setOssUrl(String ossUrl) {
        this.ossUrl = ossUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }
}
