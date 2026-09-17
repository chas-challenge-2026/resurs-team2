package se.comerit.resurs.api.v1.service;

import org.springframework.core.io.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface FileStorageService {
    String upload(UUID documentId, String originalFilename, InputStream inputStream, long size) throws IOException;
    Resource download(String storedFilename) throws IOException;
    void delete(String storedFilename) throws IOException;
}
