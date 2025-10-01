package in.bushansirgur.cloudshareapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.bushansirgur.cloudshareapi.dto.ProfileDTO;
import in.bushansirgur.cloudshareapi.service.ProfileService;
import in.bushansirgur.cloudshareapi.service.UserCreditsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


/*
clerk --> webhook(event)-->backend--> db
 */
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Slf4j
public class ClerkWebhookController {

    @Value("${clerk.webhook.secret}")
    private String webhookSecret;

    private final ProfileService profileService;
    private final UserCreditsService userCreditsService;

    @PostMapping("/clerk")
    public ResponseEntity<?> handleClerkWebhook(@RequestHeader("svix-id") String svixId,
                                                @RequestHeader("svix-timestamp") String svixTimestamp,
                                                @RequestHeader("svix-signature") String svixSignature,
                                                @RequestBody String payload) {

        log.info("Webhook received: {}", payload);
        log.info("svix-id={}, svix-timestamp={}, svix-signature={}", svixId, svixTimestamp, svixSignature);

        try {
            boolean isValid = verifyWebhookSignature(svixId, svixTimestamp, svixSignature, payload);
            if (!isValid) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid webhook signature");
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(payload);

            String eventType = rootNode.path("type").asText();

            switch (eventType) {
                case "user.created":
                    handleUserCreated(rootNode.path("data"));
                    break;
                case "user.updated":
                    handleUserUpdated(rootNode.path("data"));
                    break;
                case "user.deleted":
                    handleUserDeleted(rootNode.path("data"));
                    break;
            }
            return ResponseEntity.ok().build();
        }catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    private void handleUserDeleted(JsonNode data) {
        String clerkId = data.path("id").asText();
        profileService.deleteProfile(clerkId);
    }

    private void handleUserUpdated(JsonNode data) {
        String clerkId = data.path("id").asText();

        // Extract user information
        String email = "";
        JsonNode emailAddresses = data.path("email_addresses");
        if (emailAddresses.isArray() && emailAddresses.size() > 0) {
            email = emailAddresses.get(0).path("email_address").asText();
        }

        String firstName = data.path("first_name").asText("");
        String lastName = data.path("last_name").asText("");
        String photoUrl = data.path("image_url").asText("");

        ProfileDTO updatedProfile = ProfileDTO.builder()
                .clerkId(clerkId)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .photoUrl(photoUrl)
                .build();

        updatedProfile = profileService.updateProfile(updatedProfile);

        if (updatedProfile == null) {
            handleUserCreated(data);
        }

    }

    private void handleUserCreated(JsonNode data) {
        String clerkId = data.path("id").asText();

        String email = "";
        JsonNode emailAddresses = data.path("email_addresses");
        if (emailAddresses.isArray() && emailAddresses.size() > 0) {
            email = emailAddresses.get(0).path("email_address").asText();
        }

        String firstName = data.path("first_name").asText("");
        String lastName = data.path("last_name").asText("");
        String photoUrl = data.path("image_url").asText("");

        ProfileDTO newProfile = ProfileDTO.builder()
                .clerkId(clerkId)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .photoUrl(photoUrl)
                .build();

        log.info("Creating profile in DB for ClerkId: {}, email: {}", clerkId, email);
        profileService.createProfile(newProfile);
        userCreditsService.createInitialCredits(clerkId);

        log.info("User creation completed for ClerkId: {}", clerkId);
    }

    private boolean verifyWebhookSignature(String svixId, String svixTimestamp, String svixSignature, String payload) {
        return true;
    }
}
