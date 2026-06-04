package com.example.digitaltourguide.model.admin;

public class FileUploadRequest {
    private String fileUrl;
    private String fileName;
    private String fileType;
    //private String attractionId;

    public FileUploadRequest(String fileUrl, String fileName, String fileType,String attractionId) {
        this.fileUrl = fileUrl;
        this.fileName = fileName;
        this.fileType = fileType;
        //this.attractionId = attractionId;
    }

    /*public String getAttractionId() {
        return attractionId;
    }

    public void setAttractionId(String attractionId) {
        this.attractionId = attractionId;
    }*/

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
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
