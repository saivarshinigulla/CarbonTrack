package com.CarbonTrack.backend.controller;

import com.CarbonTrack.backend.dto.UserBadgeResponse;
import com.CarbonTrack.backend.entity.User;
import com.CarbonTrack.backend.repository.UserBadgeRepository;
import com.CarbonTrack.backend.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/badges")
public class BadgeController {

    private final UserBadgeRepository userBadgeRepository;
    private final UserRepository userRepository;

    public BadgeController(
            UserBadgeRepository userBadgeRepository,
            UserRepository userRepository) {
        this.userBadgeRepository = userBadgeRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public List<UserBadgeResponse> myBadges(
            @AuthenticationPrincipal Jwt jwt) {
        User user = userRepository.findByUsername(jwt.getSubject())
                .orElseThrow(() ->
                        new IllegalArgumentException("Authenticated user not found"));

        return userBadgeRepository.findByUserId(user.getId())
                .stream()
                .map(UserBadgeResponse::from)
                .toList();
    }
}