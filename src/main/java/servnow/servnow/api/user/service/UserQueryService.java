package servnow.servnow.api.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import servnow.servnow.api.user.dto.request.SaveEditProfilePageRequest;
import servnow.servnow.api.user.dto.response.EditProfilePageResponse;
import servnow.servnow.api.user.dto.response.KakaoEditProfilePageResponse;
import servnow.servnow.api.user.dto.response.MyPageResponse;
import servnow.servnow.common.code.UserErrorCode;
import servnow.servnow.common.code.UserInfoErrorCode;
import servnow.servnow.common.exception.BadRequestException;
import servnow.servnow.common.exception.NotFoundException;
import servnow.servnow.domain.user.model.User;
import servnow.servnow.domain.user.model.UserInfo;
import servnow.servnow.api.user.dto.response.ServnowEditProfilePageResponse;
import servnow.servnow.domain.user.model.enums.Platform;
import servnow.servnow.domain.user.repository.UserInfoRepository;
import servnow.servnow.domain.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserInfoRepository userInfoRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MyPageResponse getMyPage() {
        User user = userRepository.findById(3L)
                .orElseThrow(() -> new NotFoundException(UserErrorCode.USER_NOT_FOUND));
        UserInfo userinfo = userInfoRepository.findById(1L)
                .orElseThrow(() -> new NotFoundException(UserErrorCode.USER_NOT_FOUND));
        return MyPageResponse.of(userinfo, user);
    }

    public EditProfilePageResponse getEditProfilePage() {
        User user = userRepository.findById(2L)
                .orElseThrow(() -> new NotFoundException(UserErrorCode.USER_NOT_FOUND));
        UserInfo userInfo = userInfoRepository.findById(2L)
                .orElseThrow(() -> new NotFoundException(UserInfoErrorCode.USER_NOT_FOUND));

        if (user.getPlatform() == Platform.KAKAO) {
            return KakaoEditProfilePageResponse.of(userInfo);
        } else if (user.getPlatform() == Platform.SERVNOW) {
            return ServnowEditProfilePageResponse.of(userInfo, user);
        } else {
            throw new NotFoundException(UserErrorCode.PLATFORM_NOT_FOUND);
        }
    }

    public boolean emailDuplicate(String email) {
        return userInfoRepository.existsByEmail(email);
    }

    public void profileSave(SaveEditProfilePageRequest request) {
        User user = userRepository.findById(2L)
                .orElseThrow(() -> new NotFoundException(UserErrorCode.USER_NOT_FOUND));
        UserInfo userInfo = userInfoRepository.findById(2L)
                .orElseThrow(() -> new NotFoundException(UserInfoErrorCode.USER_NOT_FOUND));

        String password = request.password() != null ? request.password().trim() : "";
        String reconfirmPassword = request.reconfirmPassword() != null ? request.reconfirmPassword().trim() : "";

        // 비번은 바꾸지 읺을 때
        if (password.isEmpty() && reconfirmPassword.isEmpty()) {
            userInfo.setProfile_url(request.profileUrl());
            user.setSerialId(request.serialId());
            userInfo.setEmail(request.email());
            userRepository.save(user);
            userInfoRepository.save(userInfo);
        } else if (password.equals(reconfirmPassword)) {
            userInfo.setProfile_url(request.profileUrl());
            user.setSerialId(request.serialId());
            userInfo.setEmail(request.email());
            // 일반 로그인에서 사용할 암호화를 추후 적용할 예정
            user.setPassword(request.password());
            userRepository.save(user);
            userInfoRepository.save(userInfo);
        } else {
            throw new BadRequestException(UserErrorCode.WRONG_PASSWORD);
        }
    }

    public boolean getSerialIdDuplicate(String serialId) {
        // 임시 유저 사용
        return userRepository.existsBySerialId(serialId);
    }
}
