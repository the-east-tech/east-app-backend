package com.eastapp.backend.points.service;

import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.points.UserPointAdjustment;
import com.eastapp.backend.points.UserPointAdjustmentRepository;
import com.eastapp.backend.points.api.AdjustUserPointsRequest;
import com.eastapp.backend.points.api.LeaderboardMemberResponse;
import com.eastapp.backend.points.api.LeaderboardResponse;
import com.eastapp.backend.points.api.UserPointAdjustmentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserPointsService {
    private final UserAccountRepository userRepository;
    private final UserPointAdjustmentRepository adjustmentRepository;

    public UserPointsService(
            UserAccountRepository userRepository,
            UserPointAdjustmentRepository adjustmentRepository
    ) {
        this.userRepository = userRepository;
        this.adjustmentRepository = adjustmentRepository;
    }

    @Transactional(readOnly = true)
    public LeaderboardResponse leaderboard(AuthenticatedUser principal) {
        List<UserAccount> activeUsers = userRepository
                .findAllByTenant_IdAndActiveTrueOrderByIdentity_FullNameAsc(principal.tenantId())
                .stream()
                .filter(user -> user.getRole().getSystemKey() != SystemRole.OWNER)
                .toList();
        Map<UUID, Long> totals = totalsByUser(principal.tenantId());

        List<RankedUser> rankedUsers = new ArrayList<>(activeUsers.size());
        for (UserAccount user : activeUsers) {
            rankedUsers.add(new RankedUser(user, totals.getOrDefault(user.getId(), 0L)));
        }
        rankedUsers.sort(
                Comparator.<RankedUser>comparingLong(RankedUser::totalPoints)
                        .reversed()
                        .thenComparing(item -> item.user().getFullName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(item -> item.user().getEmployeeId())
                        .thenComparing(item -> item.user().getId())
        );

        List<LeaderboardMemberResponse> members = new ArrayList<>(rankedUsers.size());
        Integer currentUserRank = null;
        int rank = 0;
        Long previousTotal = null;
        for (int index = 0; index < rankedUsers.size(); index++) {
            RankedUser item = rankedUsers.get(index);
            if (previousTotal == null || item.totalPoints() != previousTotal) {
                rank = index + 1;
                previousTotal = item.totalPoints();
            }
            boolean currentUser = item.user().getId().equals(principal.userId());
            if (currentUser) {
                currentUserRank = rank;
            }
            members.add(new LeaderboardMemberResponse(
                    item.user().getId(),
                    item.user().getEmployeeId(),
                    item.user().getFullName(),
                    item.user().getRole().getName(),
                    item.totalPoints(),
                    rank,
                    currentUser
            ));
        }

        long currentUserTotal = principal.isOwner()
                ? 0L
                : totals.getOrDefault(principal.userId(), 0L);
        return new LeaderboardResponse(currentUserTotal, currentUserRank, List.copyOf(members));
    }

    @Transactional
    public UserPointAdjustmentResponse adjust(
            AuthenticatedUser principal,
            AdjustUserPointsRequest request
    ) {
        int pointsDelta = request.pointsDelta();
        if (pointsDelta == 0 || pointsDelta < -10 || pointsDelta > 10) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_POINTS_DELTA",
                    "Points adjustment must be between -10 and 10 and must not be zero."
            );
        }

        UserAccount recipient = userRepository.findByIdAndTenant_Id(request.userId(), principal.tenantId())
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "The selected user was not found."));
        if (recipient.getRole().getSystemKey() == SystemRole.OWNER) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "OWNER_POINTS_NOT_APPLICABLE",
                    "Owner users are outside the employee leaderboard and cannot receive points."
            );
        }
        if (!recipient.isActive()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "USER_INACTIVE",
                    "Points can only be adjusted for an active user."
            );
        }
        UserAccount actor = userRepository.findByIdAndTenant_Id(principal.userId(), principal.tenantId())
                .orElseThrow(() -> notFound("CURRENT_USER_NOT_FOUND", "Current user was not found."));

        UserPointAdjustment saved = adjustmentRepository.saveAndFlush(new UserPointAdjustment(
                recipient.getTenant(),
                recipient,
                actor,
                pointsDelta,
                request.reason()
        ));
        long totalPoints = adjustmentRepository.totalForUser(principal.tenantId(), recipient.getId());
        return UserPointAdjustmentResponse.from(saved, totalPoints);
    }

    private Map<UUID, Long> totalsByUser(UUID tenantId) {
        Map<UUID, Long> totals = new HashMap<>();
        for (Object[] row : adjustmentRepository.findTotalsByTenant(tenantId)) {
            totals.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return totals;
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private record RankedUser(UserAccount user, long totalPoints) {}
}
