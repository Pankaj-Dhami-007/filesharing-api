package in.bushansirgur.cloudshareapi.controller;

import in.bushansirgur.cloudshareapi.dto.ProfileDTO;
import in.bushansirgur.cloudshareapi.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final ProfileService profileService;

    @PostMapping("/register")
    public ResponseEntity<?> registerProfile(@RequestBody ProfileDTO profileDTO) {
        log.info("Received req for create profile from clerk auth via webhook - ClerkId: {}, Email: {}",
                profileDTO.getClerkId(), profileDTO.getEmail());
        HttpStatus status = profileService.existsByClerkId(profileDTO.getClerkId()) ?
                HttpStatus.OK : HttpStatus.CREATED;
        ProfileDTO savedProfile = profileService.createProfile(profileDTO);

        return ResponseEntity.status(status).body(savedProfile);
    }
}
