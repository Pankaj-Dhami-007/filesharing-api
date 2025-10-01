package in.bushansirgur.cloudshareapi.controller;

import in.bushansirgur.cloudshareapi.document.UserCredits;
import in.bushansirgur.cloudshareapi.dto.FileMetadataDTO;
import in.bushansirgur.cloudshareapi.service.FileMetadataService;
import in.bushansirgur.cloudshareapi.service.UserCreditsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/files")
@Slf4j
public class FileController {

    private final FileMetadataService fileMetadataService;
    private final UserCreditsService userCreditsService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFiles(@RequestPart("files") MultipartFile files[]) throws IOException {
        log.info("Received Req for file  uploading ...");
        Map<String, Object> response = new HashMap<>();
        List<FileMetadataDTO> list = fileMetadataService.uploadFiles(files);

        UserCredits finalCredits = userCreditsService.getUserCredits();

        response.put("files", list);
        response.put("remainingCredits", finalCredits.getCredits());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    public ResponseEntity<?> getFilesForCurrentUser() {
        List<FileMetadataDTO> files = fileMetadataService.getFiles();
        return ResponseEntity.ok(files);
    }

    @GetMapping("/public/{id}")
    public ResponseEntity<?> getPublicFile(@PathVariable String id) {
        FileMetadataDTO file = fileMetadataService.getPublicFile(id);
        return ResponseEntity.ok(file);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id) throws IOException {
        FileMetadataDTO downloadbleFile = fileMetadataService.getDownloadableFile(id);
        Path path = Paths.get(downloadbleFile.getFileLocation());
        Resource resource = new UrlResource(path.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachemnt; filename=\""+downloadbleFile.getName()+"\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable String id) {
        fileMetadataService.deleteFile(id);
        return ResponseEntity.noContent().build();
    }   

    @PatchMapping("/{id}/toggle-public")
    public ResponseEntity<?> togglePublic(@PathVariable String id) {
        FileMetadataDTO file = fileMetadataService.togglePublic(id);
        return ResponseEntity.ok(file);
    }
}
