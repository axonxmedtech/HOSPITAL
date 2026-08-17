package com.hms.dto;

import com.hms.entity.DocumentType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * What the UI receives for one document.
 *
 * <p>Deliberately carries no path or stored filename: the browser asks for a document by its
 * public id and the server decides where that lives, so a client never learns anything about the
 * filesystem.
 */
public record PatientDocumentResponse(
        String publicId,
        String title,
        DocumentType documentType,
        LocalDate documentDate,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        String uploadedBy,
        LocalDateTime uploadedAt
) { }
