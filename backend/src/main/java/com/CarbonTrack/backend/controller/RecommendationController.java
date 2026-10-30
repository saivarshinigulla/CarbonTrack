package com.CarbonTrack.backend.controller;

import com.CarbonTrack.backend.dto.RecommendationResponse;
import com.CarbonTrack.backend.entity.User;
import com.CarbonTrack.backend.repository.UserRepository;
import com.CarbonTrack.backend.service.RecommendationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final UserRepository userRepository;

    public RecommendationController(
            RecommendationService recommendationService,
            UserRepository userRepository) {
        this.recommendationService = recommendationService;
        this.userRepository = userRepository;
    }

    @GetMapping("/weekly")
    public List<RecommendationResponse> getWeeklyRecommendations(
            @AuthenticationPrincipal Jwt jwt) {

        User user = userRepository.findByUsername(jwt.getSubject())
                .orElseThrow(() ->
                        new IllegalArgumentException("Authenticated user not found"));

        return recommendationService.getWeeklyRecommendations(user.getId());
    }
}