package in.bushansirgur.cloudshareapi.service;

import in.bushansirgur.cloudshareapi.document.FileMetadataDocument;
import in.bushansirgur.cloudshareapi.document.ProfileDocument;
import in.bushansirgur.cloudshareapi.dto.FileMetadataDTO;
import in.bushansirgur.cloudshareapi.repository.FileMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileMetadataService {

    private final ProfileService profileService;
    private final UserCreditsService userCreditsService;
    private final FileMetadataRepository fileMetadataRepository;

    public List<FileMetadataDTO> uploadFiles(MultipartFile files[]) throws IOException {
        log.info("Starting file upload for {} files", files.length);
        ProfileDocument currentProfile = profileService.getCurrentProfile();
        log.debug("Retrieved current profile with clerkId: {}", currentProfile.getClerkId());
        List<FileMetadataDocument> savedFiles = new ArrayList<>();

        if (!userCreditsService.hasEnoughCredits(files.length)) {
            log.error("Insufficient credits for uploading {} files", files.length);
            throw new RuntimeException("Not enough credits to upload files. Please purchase more credits");
        }

        Path uploadPath = Paths.get("upload").toAbsolutePath().normalize();
        log.debug("Creating upload directory: {}", uploadPath);
        Files.createDirectories(uploadPath);

        for (MultipartFile file : files) {
            String originalFileName = file.getOriginalFilename();
            String fileName = UUID.randomUUID()+"."+ StringUtils.getFilenameExtension(originalFileName);
            Path targetLocation = uploadPath.resolve(fileName);
            log.debug("Uploading file: {} to location: {}", originalFileName, targetLocation);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            log.info("Successfully copied file: {} to {}", originalFileName, targetLocation);
            FileMetadataDocument fileMetadata = FileMetadataDocument.builder()
                    .fileLocation(targetLocation.toString())
                    .name(file.getOriginalFilename())
                    .size(file.getSize())
                    .type(file.getContentType())
                    .clerkId(currentProfile.getClerkId())
                    .isPublic(false)
                    .uploadedAt(LocalDateTime.now())
                    .build();

            userCreditsService.consumeCredit();
            log.debug("Consumed one credit for file: {}", originalFileName);

            savedFiles.add(fileMetadataRepository.save(fileMetadata));
        }
        return savedFiles.stream().map(fileMetadataDocument -> mapToDTO(fileMetadataDocument))
                .collect(Collectors.toList());

    }

    private FileMetadataDTO mapToDTO(FileMetadataDocument fileMetadataDocument) {
        log.debug("Mapping FileMetadataDocument with ID: {} to DTO", fileMetadataDocument.getId());
        return FileMetadataDTO.builder()
                .id(fileMetadataDocument.getId())
                .fileLocation(fileMetadataDocument.getFileLocation())
                .name(fileMetadataDocument.getName())
                .size(fileMetadataDocument.getSize())
                .type(fileMetadataDocument.getType())
                .clerkId(fileMetadataDocument.getClerkId())
                .isPublic(fileMetadataDocument.getIsPublic())
                .uploadedAt(fileMetadataDocument.getUploadedAt())
                .build();
    }

    public List<FileMetadataDTO> getFiles() {
        ProfileDocument currentProfile = profileService.getCurrentProfile();
        log.debug("Fetching files for clerkId: {}", currentProfile.getClerkId());
        List<FileMetadataDocument> files = fileMetadataRepository.findByClerkId(currentProfile.getClerkId());
        return files.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public FileMetadataDTO getPublicFile(String id) {
        Optional<FileMetadataDocument> fileOptional = fileMetadataRepository.findById(id);
        if (fileOptional.isEmpty() || !fileOptional.get().getIsPublic()) {
            log.error("File with ID: {} not found or not public", id);
            throw new RuntimeException("Unable to get the file");
        }

        FileMetadataDocument document = fileOptional.get();
        log.debug("Found public file with ID: {}", id);
        return mapToDTO(document);
    }

    public FileMetadataDTO getDownloadableFile(String id) {
        FileMetadataDocument file = fileMetadataRepository.findById(id).orElseThrow(() -> new RuntimeException("File not found"));
        log.debug("Found downloadable file with ID: {}", id);
        return mapToDTO(file);
    }

    public void deleteFile(String id) {
        log.info("Deleting file with ID: {}", id);
        try {
            ProfileDocument currentProfile = profileService.getCurrentProfile();
            FileMetadataDocument file = fileMetadataRepository.findById(id)
                    .orElseThrow(() -> {
                        log.error("File with ID: {} not found", id);
                        return new RuntimeException("File not found");
                    });

            if (!file.getClerkId().equals(currentProfile.getClerkId())) {
                log.error("File with ID: {} does not belong to user with clerkId: {}", id, currentProfile.getClerkId());
                throw new RuntimeException("File is not belong to current user");
            }

            Path filePath = Paths.get(file.getFileLocation());
            log.debug("Deleting file from path: {}", filePath);
            Files.deleteIfExists(filePath);
            log.info("Successfully deleted file from path: {}", filePath);

            fileMetadataRepository.deleteById(id);
            log.info("Successfully deleted file metadata with ID: {}", id);
        }catch (Exception e) {
            log.error("Failed to delete file with ID: {}", id, e);
            throw new RuntimeException("Error deleting the file");
        }
    }

    public FileMetadataDTO togglePublic(String id) {
        FileMetadataDocument file = fileMetadataRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found"));

        file.setIsPublic(!file.getIsPublic());
        fileMetadataRepository.save(file);

        return mapToDTO(file);
    }
}
