package servnow.servnow.api.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import servnow.servnow.api.result.service.surveyresultmemo.SurveyResultMemoFinder;
import servnow.servnow.api.result.service.surveyresultmemo.SurveyResultMemoUpdater;
import servnow.servnow.api.user.dto.request.SaveEditProfilePageRequest;
import servnow.servnow.common.code.SuccessCode;
import servnow.servnow.common.code.UserErrorCode;
import servnow.servnow.common.code.UserInfoErrorCode;
import servnow.servnow.common.exception.BadRequestException;
import servnow.servnow.common.exception.NotFoundException;
import servnow.servnow.domain.s3.S3Manager;
import servnow.servnow.domain.s3.uuid.model.Uuid;
import servnow.servnow.domain.s3.uuid.repository.UuidRepository;
import servnow.servnow.domain.user.model.User;
import servnow.servnow.domain.user.model.UserInfo;
import servnow.servnow.domain.user.repository.UserInfoRepository;
import servnow.servnow.domain.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserCommandService {

    private final UserInfoRepository userInfoRepository;
    private final UserRepository userRepository;
    private final UserInfoFinder userInfoFinder;
    private final UuidRepository uuidRepository;
    private final S3Manager s3Manager;


    public void profileSave(final Long userId, SaveEditProfilePageRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(UserErrorCode.USER_NOT_FOUND));
        UserInfo userInfo = userInfoRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(UserInfoErrorCode.USER_INFO_NOT_FOUND));

        String password = request.password() != null ? request.password().trim() : "";
        String reconfirmPassword = request.reconfirmPassword() != null ? request.reconfirmPassword().trim() : "";

        // 비밀번호 변경 로직
        if (!password.isEmpty()) {
            if (password.equals(reconfirmPassword)) {
                // 일반 로그인에서 사용할 암호화를 추후 적용할 예정
                user.setPassword(password);
            } else {
                throw new BadRequestException(UserErrorCode.WRONG_PASSWORD);
            }
        }

        // 프로필과 Serial ID는 항상 업데이트 가능
//        userInfo.setProfile_url(request.profileUrl());
        if (userInfoFinder.isEmailDuplicate(request.serialId())) {
            throw new BadRequestException(UserErrorCode.DUPLICATE_SERIAL_ID);
        }
        userInfo.setNickname(request.nickname());
        user.setSerialId(request.serialId());
        userRepository.save(user);
        userInfoRepository.save(userInfo);
        // 이메일 변경 시 인증번호 확인
        if (request.email() != null && !request.email().isEmpty()) {
            if (request.certificationNumber() == null || !request.certificationNumber().equalsIgnoreCase(EmailService.ePw)) {
                System.out.println("Stored Certification Number: " + EmailService.ePw);
                System.out.println("Received Certification Number: " + request.certificationNumber());
                throw new BadRequestException(UserErrorCode.CERTIFICATION_NUMBER_MISMATCH);
            }
            // 인증이 성공했으므로 이메일 업데이트
            userInfo.setEmail(request.email());
            userInfoRepository.save(userInfo);
        }
    }

    public void updateProfile(MultipartFile file, Long userId) {
            String url = null;
            if (file != null && !file.isEmpty()) {
                String uuid = UUID.randomUUID().toString();
                Uuid savedUuid = uuidRepository.save(Uuid.builder()
                        .uuid(uuid).build());

                url = s3Manager.uploadFile(s3Manager.generateImage(savedUuid), file);
                UserInfo userInfo = userInfoRepository.findByUserId(userId).orElseThrow(() -> new NotFoundException(UserInfoErrorCode.USER_INFO_NOT_FOUND));
                userInfo.setProfile_url(url);
                userInfoRepository.save(userInfo);
            }
    }
}