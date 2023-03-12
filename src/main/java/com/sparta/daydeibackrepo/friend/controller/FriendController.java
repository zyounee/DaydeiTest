package com.sparta.daydeibackrepo.friend.controller;

import com.sparta.daydeibackrepo.friend.dto.FriendResponseDto;
import com.sparta.daydeibackrepo.friend.service.FriendService;
import com.sparta.daydeibackrepo.security.UserDetailsImpl;
import com.sparta.daydeibackrepo.util.StatusResponseDto;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/friends")
public class FriendController {
    private final FriendService friendService;
    @PostMapping("/{userId}")
    public StatusResponseDto<FriendResponseDto> requestFriend(@PathVariable Long userId, @Parameter(hidden = true) @AuthenticationPrincipal UserDetailsImpl userDetails){
        return StatusResponseDto.success(friendService.requestFriend(userId, userDetails));
    }

    @PutMapping("/{userId}")
    public StatusResponseDto<FriendResponseDto> setFriend(@PathVariable Long userId, @Parameter(hidden = true) @AuthenticationPrincipal UserDetailsImpl userDetails){
        return StatusResponseDto.success(friendService.setFriend(userId, userDetails));
    }

    @DeleteMapping("/{userId}")
    public StatusResponseDto<String> deleteFriend(@PathVariable Long userId, @Parameter(hidden = true) @AuthenticationPrincipal UserDetailsImpl userDetails){
        friendService.deleteFriend(userId, userDetails);
        return StatusResponseDto.success("친구 삭제가 완료되었습니다.");
    }
}
