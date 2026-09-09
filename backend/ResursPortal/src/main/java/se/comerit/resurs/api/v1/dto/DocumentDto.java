package se.comerit.resurs.api.v1.dto;

import se.comerit.resurs.entity.Document;

import java.time.LocalDateTime;

public record DocumentDto(
        Long uuid,
        Long applicationId,
        String filename,
        String docType,
        LocalDateTime uploadedAt
) {
    public static DocumentDto from(Document document) {
        return new DocumentDto(
                document.getUuid(),
                document.getApplication().getId(),
                document.getFilename(),
                document.getDocType(),
                document.getUploadedAt()
        );
    }
}