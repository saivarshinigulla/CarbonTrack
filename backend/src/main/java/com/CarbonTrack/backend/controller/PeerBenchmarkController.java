package com.CarbonTrack.backend.controller;

import com.CarbonTrack.backend.dto.PeerBenchmarkResponse;
import com.CarbonTrack.backend.entity.User;
import com.CarbonTrack.backend.repository.UserRepository;
import com.CarbonTrack.backend.service.PeerBenchmarkService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/benchmarking")
public class PeerBenchmarkController {

    private final PeerBenchmarkService peerBenchmarkService;
    private final UserRepository userRepository;

    public PeerBenchmarkController(
            PeerBenchmarkService peerBenchmarkService,
            UserRepository userRepository) {
        this.peerBenchmarkService = peerBenchmarkService;
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public PeerBenchmarkResponse getMyBenchmark(
            @AuthenticationPrincipal Jwt jwt) {

        User user = userRepository.findByUsername(jwt.getSubject())
                .orElseThrow(() ->
                        new IllegalArgumentException("Authenticated user not found"));

        return peerBenchmarkService.getBenchmark(user.getId());
    }
}