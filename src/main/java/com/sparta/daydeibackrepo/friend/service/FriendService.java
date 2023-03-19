package com.sparta.daydeibackrepo.friend.service;

import com.sparta.daydeibackrepo.friend.dto.FriendListResponseDto;
import com.sparta.daydeibackrepo.friend.dto.FriendResponseDto;
import com.sparta.daydeibackrepo.friend.dto.RelationResponseDto;
import com.sparta.daydeibackrepo.friend.entity.Friend;
import com.sparta.daydeibackrepo.friend.repository.FriendRepository;
import com.sparta.daydeibackrepo.notification.entity.NotificationType;
import com.sparta.daydeibackrepo.notification.service.NotificationService;
import com.sparta.daydeibackrepo.security.UserDetailsImpl;
import com.sparta.daydeibackrepo.user.dto.UserResponseDto;
import com.sparta.daydeibackrepo.user.entity.CategoryEnum;
import com.sparta.daydeibackrepo.user.entity.User;
import com.sparta.daydeibackrepo.user.repository.UserRepository;
import com.sparta.daydeibackrepo.userSubscribe.entity.UserSubscribe;
import com.sparta.daydeibackrepo.userSubscribe.repository.UserSubscribeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendService {
    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final UserSubscribeRepository userSubscribeRepository;
    private final NotificationService notificationService;
    @Transactional
    public FriendResponseDto requestFriend(Long userId, UserDetailsImpl userDetails) {
        User requestUser = userRepository.findByEmail(userDetails.getUser().getEmail()).orElseThrow(
                () -> new UsernameNotFoundException("인증된 유저가 아닙니다")
        );
        User responseUser = userRepository.findById(userId).orElseThrow(
                () -> new UsernameNotFoundException("유저가 존재하지 않습니다.")
        );
        if(Objects.equals(requestUser.getId(), responseUser.getId())){
            throw new IllegalArgumentException("올바르지 않은 친구 요청입니다.");
        }
        Friend friend1 = friendRepository.findByFriendRequestIdAndFriendResponseId(requestUser, responseUser);
        Friend friend2 = friendRepository.findByFriendRequestIdAndFriendResponseId(responseUser, requestUser);
        if(friend1 != null || friend2 != null){
            throw new IllegalArgumentException("이미 친구 상태입니다.");
        }
        Friend friend = new Friend(requestUser, responseUser, false);
        friendRepository.save(friend);
        notificationService.send(responseUser.getId() , NotificationType.FRIEND_REQUEST, NotificationType.FRIEND_REQUEST.makeContent(requestUser.getNickName()), NotificationType.FRIEND_REQUEST.makeUrl(requestUser.getId()));
        return new FriendResponseDto(friend);
    }
    @Transactional
    public FriendResponseDto setFriend(Long userId, UserDetailsImpl userDetails) {
        User responseUser = userRepository.findByEmail(userDetails.getUsername()).orElseThrow(
                () -> new UsernameNotFoundException("인증된 유저가 아닙니다")
        );
        User requestUser = userRepository.findById(userId).orElseThrow(
                () -> new UsernameNotFoundException("유저가 존재하지 않습니다.")
        );
        if(requestUser==responseUser){
            throw new IllegalArgumentException("올바르지 않은 친구 요청입니다.");
        }
        Friend friend = friendRepository.findByFriendRequestIdAndFriendResponseId(requestUser, responseUser);
        if (friend == null){
            throw new IllegalArgumentException("승인 가능한 친구 요청이 없습니다.");
        }
        friend.update(requestUser, responseUser, true);
        notificationService.send(requestUser.getId() , NotificationType.FRIEND_ACCEPT, NotificationType.FRIEND_ACCEPT.makeContent(responseUser.getNickName()), NotificationType.FRIEND_ACCEPT.makeUrl(responseUser.getId()));
        return new FriendResponseDto(friend);
    }
    @Transactional
    public String deleteFriend(Long userId, UserDetailsImpl userDetails) {
        User user1 = userRepository.findByEmail(userDetails.getUsername()).orElseThrow(
                () -> new UsernameNotFoundException("인증된 유저가 아닙니다")
        );
        User user2 = userRepository.findById(userId).orElseThrow(
                () -> new UsernameNotFoundException("유저가 존재하지 않습니다.")
        );
        Friend friend1 = friendRepository.findByFriendRequestIdAndFriendResponseId(user1, user2);
        Friend friend2 = friendRepository.findByFriendRequestIdAndFriendResponseId(user2, user1);
        if (friend1 != null && friend2 != null){
            throw new IllegalArgumentException("친구 상태가 올바르지 않습니다.");
        }
        else if (friend1 != null){
            friendRepository.delete(friend1);
            if (friend1.getFriendCheck()){
                return "친구를 삭제했습니다.";
            }
            else {
                return "친구 신청을 취소하였습니다.";
            }
        }
        else if (friend2 != null){
            friendRepository.delete(friend2);
            if (friend1.getFriendCheck()){
                return "친구를 삭제했습니다.";
            }
            else {
                return "친구 신청을 거절하였습니다.";
            }
        }
        else {
            throw new IllegalArgumentException("삭제 요청이 올바르지 않습니다.");
        }
    }
    @Transactional(readOnly = true)
    public RelationResponseDto getRelationList(UserDetailsImpl userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow(
                () -> new UsernameNotFoundException("인증된 유저가 아닙니다")
        );
        //친구 리스트
        List<Friend> friends = friendRepository.findFriends(user);
        List<UserResponseDto> friendResponseList = new ArrayList<>();
        for(Friend friend : friends){
            User user1 = null;
            if (friend.getFriendResponseId() != user){
                user1 = friend.getFriendResponseId();
            }
            else if (friend.getFriendRequestId() != user){
                user1 = friend.getFriendRequestId();
            }
            friendResponseList.add( new UserResponseDto(user1, true));
        }
        // 구독 리스트
        List<UserSubscribe> userSubscribes = userSubscribeRepository.findAllBySubscribingId(user);
        List<UserResponseDto> userSubscribeResponseList = new ArrayList<>();
        for(UserSubscribe userSubscribe : userSubscribes){
            userSubscribeResponseList.add(new UserResponseDto(userSubscribe, true));
        }
        // 일단위로 sorting 방법 변경 > 일단은 간단하게 구현
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        boolean isEven = Integer.parseInt(LocalDate.now().format(formatter)) % 2 == 0;
        if (isEven){
            Collections.sort(friendResponseList, Comparator.comparing(UserResponseDto::getEmail));
            Collections.sort(userSubscribeResponseList, Comparator.comparing(UserResponseDto::getEmail));
        }
        else {
            Collections.sort(friendResponseList, Comparator.comparing(UserResponseDto::getNickName));
            Collections.sort(userSubscribeResponseList, Comparator.comparing(UserResponseDto::getNickName));
        }
        return new RelationResponseDto(friendResponseList, userSubscribeResponseList);
    }
    @Transactional(readOnly = true)
    public List<UserResponseDto> getRecommendList(List<String> categories, String searchWord, UserDetailsImpl userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow(
                () -> new UsernameNotFoundException("인증된 유저가 아닙니다")
        );
        List<UserResponseDto> recommendResponseList = new ArrayList<>();
        List<Long> duplicationCheck =new ArrayList<>();
        List<User> recommendList = userRepository.findRecommmedList("%" + searchWord + "%", user);
        for (User user1 : recommendList) {
            for (String category : categories) {
                CategoryEnum categoryEnum = CategoryEnum.valueOf(category.toUpperCase());
                if (user1.getCategoryEnum().contains(categoryEnum)) {
                    Friend friend = friendRepository.findFriend(user, user1);
                    UserSubscribe userSubscribe = userSubscribeRepository.findBySubscribingIdAndSubscriberId(user, user1);
                    boolean friendCheck = false;
                    boolean userSubscribeCheck = false;
                    int friendCount = friendRepository.findFriends(user1).size();
                    int subscribingCount = userSubscribeRepository.findAllBySubscribingId(user1).size();
                    int subscriberCount = userSubscribeRepository.findAllBySubscriberId(user1).size();
                    if (friend != null) {
                        friendCheck = true;
                    }
                    if (userSubscribe != null) {
                        userSubscribeCheck = true;
                    }
                    if ((!friendCheck || !userSubscribeCheck) && !duplicationCheck.contains(user1.getId())) {
                        duplicationCheck.add(user1.getId());
                        if (friendRepository.findFirstOneRequest(user1, user) != null) {
                            boolean isRequestFriend = true;
                            recommendResponseList.add(new UserResponseDto(user1, friendCheck, isRequestFriend, userSubscribeCheck, friendCount, subscribingCount,subscriberCount));
                        } else if (friendRepository.findFirstOneRequest(user, user1) != null) {
                            boolean isRequestFriend = false;
                            recommendResponseList.add(new UserResponseDto(user1, friendCheck, isRequestFriend, userSubscribeCheck, friendCount, subscribingCount,subscriberCount));
                        } else {
                            recommendResponseList.add(new UserResponseDto(user1, friendCheck, userSubscribeCheck, friendCount, subscribingCount,subscriberCount));
                        }
                    }
                }
            }
            if (categories.isEmpty()) {
                Friend friend = friendRepository.findFriend(user, user1);
                UserSubscribe userSubscribe = userSubscribeRepository.findBySubscribingIdAndSubscriberId(user, user1);
                boolean friendCheck = false;
                boolean userSubscribeCheck = false;
                int friendCount = friendRepository.findFriends(user1).size();
                int subscribingCount = userSubscribeRepository.findAllBySubscribingId(user1).size();
                int subscriberCount = userSubscribeRepository.findAllBySubscriberId(user1).size();
                if (friend != null) {
                    friendCheck = true;
                }
                if (userSubscribe != null) {
                    userSubscribeCheck = true;
                }
                if ((!friendCheck || !userSubscribeCheck) && !duplicationCheck.contains(user1.getId())) {
                    duplicationCheck.add(user1.getId());
                    if (friendRepository.findFirstOneRequest(user1, user) != null) {
                        boolean isRequestFriend = true;
                        recommendResponseList.add(new UserResponseDto(user1, friendCheck, isRequestFriend, userSubscribeCheck, friendCount, subscribingCount,subscriberCount));
                    } else if (friendRepository.findFirstOneRequest(user, user1) != null) {
                        boolean isRequestFriend = false;
                        recommendResponseList.add(new UserResponseDto(user1, friendCheck, isRequestFriend, userSubscribeCheck, friendCount, subscribingCount,subscriberCount));
                    } else {
                        recommendResponseList.add(new UserResponseDto(user1, friendCheck, userSubscribeCheck, friendCount, subscribingCount,subscriberCount));
                    }
                }
            }
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        boolean isEven = Integer.parseInt(LocalDate.now().format(formatter)) % 2 == 0;
        if (isEven){
            Collections.sort(recommendResponseList, (o1, o2) -> o2.getFriendCount() - o1.getFriendCount());
        }
        else {
            Collections.sort(recommendResponseList, (o1, o2) -> o2.getSubscriberCount() - o1.getSubscriberCount());
        }
        return recommendResponseList;
        // 유저를 넣었을때 친구 숫자 보여주는 함수 + 유저를 넣었을때 구독자 수 / 구독하는 수 보여주는 함수
    }

    @Transactional
    public List<FriendListResponseDto> getFriendList(UserDetailsImpl userDetails){
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow(
                () -> new UsernameNotFoundException("사용자를 찾을 수 없습니다")
        );

//        List<Friend> friends = friendRepository.findFriends(user);
//        List<FriendListResponseDto> friendList = new ArrayList<>();
//        for (Friend friend : friends){
//            friendList.add(FriendListResponseDto(friend));
//        }

//        //친구 리스트
//        List<Friend> friends = friendRepository.findFriends(user); // user랑 친구인 friend를 찾기
//        List<User> friendList = new ArrayList<>();  //
//        List<FriendListResponseDto> friendResponseList = new ArrayList<>();
//        User friendUser = null;
//        for(Friend friend : friends){
//            if (friend.getFriendResponseId() != user){
//                friendUser = friend.getFriendResponseId();
//            }
//            else if (friend.getFriendRequestId() != user){
//                friendUser = friend.getFriendRequestId();
//            }
//            friendResponseList.add( new FriendListResponseDto(friendUser));
//        }
//        return friendResponseList;

        List<Friend> friends = friendRepository.findFriends(user);
        List<FriendListResponseDto> friendResponseList = new ArrayList<>();
        for(Friend friend : friends) {
            User friendUser = null; // friendUser 변수 초기화
            if (friend.getFriendResponseId() != user){
                friendUser = friend.getFriendResponseId();
            }
            else if (friend.getFriendRequestId() != user){
                friendUser = friend.getFriendRequestId();
            }
            friendResponseList.add(new FriendListResponseDto(friendUser));
        }
        return friendResponseList;
    }
    @Transactional(readOnly = true)
    public List<UserResponseDto> getRandomList(UserDetailsImpl userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow(
                () -> new UsernameNotFoundException("인증된 유저가 아닙니다")
        );
        List<UserResponseDto> randomList = new ArrayList<>();
        List<User> users = userRepository.findAll();


        // 구독한 유저 객체를 userSubscribers 리스트로 뽑아줍니다.
        List<User> userSubscribers = userSubscribeRepository.findAllSubscriberUser(user);

        // 나와 친구를 맺은 유저 객체를 user 리스트로 뽑아줍니다.
        List<User> friends = friendRepository.findAllFriends(user);


        for (User user1 : users){
            boolean friendCheck = false;
            boolean userSubscribeCheck = false;

            //얘네 3개는 유저 entity안에 붙여서 꺼내보자.
            int friendCount = friendRepository.findFriends(user1).size();
            int subscribingCount = userSubscribeRepository.findAllBySubscribingId(user1).size();
            int subscriberCount = userSubscribeRepository.findAllBySubscriberId(user1).size();


            if (friends.contains(user1)) {
                friendCheck = true;
            }
            if (userSubscribers.contains(user1)) {
                userSubscribeCheck = true;
            }

            if ((!friendCheck || !userSubscribeCheck)) {
                if (friendRepository.findFirstOneRequest(user1, user) != null) {
                    boolean isRequestFriend = true;
                    randomList.add(new UserResponseDto(user1, friendCheck, isRequestFriend, userSubscribeCheck, friendCount, subscribingCount,subscriberCount));
                } else if (friendRepository.findFirstOneRequest(user, user1) != null) {
                    boolean isRequestFriend = false;
                    randomList.add(new UserResponseDto(user1, friendCheck, isRequestFriend, userSubscribeCheck, friendCount, subscribingCount,subscriberCount));
                } else {
                    randomList.add(new UserResponseDto(user1, friendCheck, userSubscribeCheck, friendCount, subscribingCount,subscriberCount));
                }
            }
        }
        Collections.shuffle(randomList);
        return randomList.stream().limit(3).collect(Collectors.toList());
    }
}
