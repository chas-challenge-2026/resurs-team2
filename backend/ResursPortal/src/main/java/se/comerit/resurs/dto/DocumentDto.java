package se.comerit.resurs.dto;

import se.comerit.resurs.entity.Document;

import java.time.LocalDateTime;

public record DocumentDto(
        Long id,
        Long applicationId,
        String filename,
        String docType,
        LocalDateTime uploadedAt
) {
    public static DocumentDto from(Document document) {
        return new DocumentDto(
                document.getId(),
                document.getApplication().getId(),
                document.getFilename(),
                document.getDocType(),
                document.getUploadedAt()
        );
    }
}