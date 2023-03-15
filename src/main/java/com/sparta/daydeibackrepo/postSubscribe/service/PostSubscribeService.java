package com.sparta.daydeibackrepo.postSubscribe.service;

import com.sparta.daydeibackrepo.friend.entity.Friend;
import com.sparta.daydeibackrepo.notification.entity.NotificationType;
import com.sparta.daydeibackrepo.notification.service.NotificationService;
import com.sparta.daydeibackrepo.post.entity.Post;
import com.sparta.daydeibackrepo.post.repository.PostRepository;
import com.sparta.daydeibackrepo.postSubscribe.entity.PostSubscribe;
import com.sparta.daydeibackrepo.postSubscribe.repository.PostSubscribeRepository;
import com.sparta.daydeibackrepo.security.UserDetailsImpl;
import com.sparta.daydeibackrepo.user.entity.User;
import com.sparta.daydeibackrepo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostSubscribeService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostSubscribeRepository postSubscribeRepository;
    private final NotificationService notificationService;
    @Transactional
    public void createJoin(Long postId, List<User> joiners, UserDetailsImpl userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow(
                () -> new UsernameNotFoundException("인증된 유저가 아닙니다")
        );
        Post post = postRepository.findById(postId).orElseThrow(
                () -> new EntityNotFoundException("작성글이 존재하지 않습니다.")
        );
        if (!Objects.equals(user.getId(), post.getUser().getId())){
            throw new IllegalArgumentException("올바르지 않은 공유일정 생성입니다.");
        }
        for(User joiner : joiners){
            PostSubscribe postSubscribe = postSubscribeRepository.findByPostIdAndUserId(post.getUser().getId(), joiner.getId());
            if(postSubscribe!=null){
                throw new IllegalArgumentException("해당 유저는 이미 일정 초대되었습니다.");
            }
            PostSubscribe postSubscribe1 = new PostSubscribe(post, joiner, false);
            postSubscribeRepository.save(postSubscribe1);
            notificationService.send(joiner.getId() , NotificationType.JOIN_REQUEST, NotificationType.JOIN_REQUEST.makeContent(user.getNickName()), NotificationType.JOIN_REQUEST.makeUrl(post.getId()));
        }
    }

    @Transactional
    public void approveJoin(Long postId, UserDetailsImpl userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow(
                () -> new UsernameNotFoundException("인증된 유저가 아닙니다")
        );
        Post post = postRepository.findById(postId).orElseThrow(
                () -> new EntityNotFoundException("작성글이 존재하지 않습니다.")
        );
        PostSubscribe postSubscribe = postSubscribeRepository.findByPostIdAndUserId(post.getUser().getId(), user.getId());
        if (postSubscribe==null){
            throw new EntityNotFoundException("수락 가능한 공유 일정이 없습니다.");
        }
        postSubscribe.update(true);
        notificationService.send(post.getUser().getId() , NotificationType.JOIN_ACCEPT, NotificationType.JOIN_ACCEPT.makeContent(user.getNickName()), NotificationType.JOIN_ACCEPT.makeUrl(post.getId()));
    }

    @Transactional
    public void rejectJoin(Long postId, UserDetailsImpl userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow(
                () -> new UsernameNotFoundException("인증된 유저가 아닙니다")
        );
        Post post = postRepository.findById(postId).orElseThrow(
                () -> new EntityNotFoundException("작성글이 존재하지 않습니다.")
        );
        PostSubscribe postSubscribe = postSubscribeRepository.findByPostIdAndUserId(post.getUser().getId(), user.getId());
        if (postSubscribe==null){
            throw new EntityNotFoundException("거절 가능한 공유 일정이 없습니다.");
        }
        postSubscribeRepository.delete(postSubscribe);
        notificationService.send(post.getUser().getId() , NotificationType.JOIN_REJECT, NotificationType.JOIN_REJECT.makeContent(user.getNickName()), NotificationType.JOIN_REJECT.makeUrl(post.getId()));
    }
}
