package in.bushansirgur.cloudshareapi.service;

import in.bushansirgur.cloudshareapi.document.ProfileDocument;
import in.bushansirgur.cloudshareapi.dto.ProfileDTO;
import in.bushansirgur.cloudshareapi.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {


    /*
   
    Think of this service as a chef. The dependencies are ingredients:
    user profile (who's cooking), credits (your budget), and the fridge
    (database for storing recipes/files).
     */
    private final ProfileRepository profileRepository;

    public ProfileDTO createProfile(ProfileDTO profileDTO) {

        if (profileRepository.existsByClerkId(profileDTO.getClerkId())) {
            return updateProfile(profileDTO);
        }

        ProfileDocument profile = ProfileDocument.builder()
                .clerkId(profileDTO.getClerkId())
                .email(profileDTO.getEmail())
                .firstName(profileDTO.getFirstName())
                .lastName(profileDTO.getLastName())
                .photoUrl(profileDTO.getPhotoUrl())
                .credits(5)
                .createdAt(Instant.now())
                .build();

        profile = profileRepository.save(profile);

        log.info("______________>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        return ProfileDTO.builder()
                .id(profile.getId())
                .clerkId(profile.getClerkId())
                .email(profile.getEmail())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .photoUrl(profile.getPhotoUrl())
                .credits(profile.getCredits())
                .createdAt(profile.getCreatedAt())
                .build();

    }

    public ProfileDTO updateProfile(ProfileDTO profileDTO) {
        log.info("Updating profile for ClerkId: {}", profileDTO.getClerkId());
        ProfileDocument existingProfile = profileRepository.findByClerkId(profileDTO.getClerkId());

        if (existingProfile != null) {
            //update fields if provided
            if (profileDTO.getEmail() != null && !profileDTO.getEmail().isEmpty()) {
                existingProfile.setEmail(profileDTO.getEmail());
            }

            if (profileDTO.getFirstName() != null && !profileDTO.getFirstName().isEmpty()) {
                existingProfile.setFirstName(profileDTO.getFirstName());
            }

            if (profileDTO.getLastName() != null && !profileDTO.getLastName().isEmpty()) {
                existingProfile.setLastName(profileDTO.getLastName());
            }

            if (profileDTO.getPhotoUrl() != null && !profileDTO.getPhotoUrl().isEmpty()) {
                existingProfile.setPhotoUrl(profileDTO.getPhotoUrl());
            }

            profileRepository.save(existingProfile);

            return ProfileDTO.builder()
                    .id(existingProfile.getId())
                    .email(existingProfile.getEmail())
                    .clerkId(existingProfile.getClerkId())
                    .firstName(existingProfile.getFirstName())
                    .lastName(existingProfile.getLastName())
                    .credits(existingProfile.getCredits())
                    .createdAt(existingProfile.getCreatedAt())
                    .photoUrl(existingProfile.getPhotoUrl())
                    .build();
        }
        return null;
    }

    public boolean existsByClerkId(String clerkId) {
        return profileRepository.existsByClerkId(clerkId);
    }


    public void deleteProfile(String clerkId) {
        log.info("Deleting profile for ClerkId: {}", clerkId);
        ProfileDocument existingProfile = profileRepository.findByClerkId(clerkId);
        if (existingProfile != null) {
            profileRepository.delete(existingProfile);
        }
    }


    //Gets info about the current user (like their profile).
    public ProfileDocument getCurrentProfile() {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            throw new UsernameNotFoundException("User not authenticated");
        }

        String clerkId = SecurityContextHolder.getContext().getAuthentication().getName();
        return profileRepository.findByClerkId(clerkId);
    }
}
