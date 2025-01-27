package servnow.servnow.api.result.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import servnow.servnow.api.result.dto.request.MySurveysResultMemoRequest;
import servnow.servnow.api.result.dto.request.ResultPostRequest;
import servnow.servnow.api.result.dto.request.SurveyResultMemosPatchRequest;
import servnow.servnow.api.result.service.surveyresultmemo.SurveyResultMemoFinder;
import servnow.servnow.api.result.service.surveyresultmemo.SurveyResultMemoUpdater;
import servnow.servnow.api.user.service.UserFinder;
import servnow.servnow.api.user.service.UserInfoFinder;
import servnow.servnow.api.user.service.UserInfoUpdater;
import servnow.servnow.common.code.*;
import servnow.servnow.common.exception.BadRequestException;
import servnow.servnow.common.exception.NotFoundException;
import servnow.servnow.domain.multiplechoiceresult.model.MultipleChoiceResult;
import servnow.servnow.domain.question.model.MultipleChoice;
import servnow.servnow.domain.question.model.Question;
import servnow.servnow.domain.question.repository.MultipleChoiceRepository;
import servnow.servnow.domain.question.repository.QuestionRepository;
import servnow.servnow.domain.subjectiveresult.model.SubjectiveResult;
import servnow.servnow.domain.survey.model.Survey;
import servnow.servnow.domain.survey.repository.SurveyRepository;
import servnow.servnow.domain.surveyresult.model.SurveyResult;
import servnow.servnow.domain.surveyresult.repository.SurveyResultRepository;
import servnow.servnow.domain.surveyresultmemo.model.SurveyResultMemo;
import servnow.servnow.domain.surveyresultmemo.repository.SurveyResultMemoRepository;
import servnow.servnow.domain.user.model.UserInfo;

import java.util.List;

import servnow.servnow.common.code.QuestionErrorCode;
import servnow.servnow.common.code.SurveyResultErrorCode;


@Service
@RequiredArgsConstructor
public class ResultCommandService {

    public final ResultUpdater resultUpdater;
    public final UserFinder userFinder;
    public final SurveyRepository surveyRepository;
    private final UserInfoUpdater userInfoUpdater;
    private final UserInfoFinder userInfoFinder;
    private final MultipleChoiceRepository multipleChoiceRepository;
    private final QuestionRepository questionRepository;
    private final SubjectiveResultUpdater subjectiveResultUpdater;
    private final MultipleChoiceResultUpdater multipleChoiceResultUpdater;
    private final SurveyResultRepository surveyResultRepository;
    private final SurveyResultMemoRepository surveyResultMemoRepository;
    private final SurveyResultMemoFinder surveyResultMemoFinder;
    private final SurveyResultMemoUpdater surveyResultMemoUpdater;

    @Transactional
    public void createResult(final long userId, final ResultPostRequest resultPostRequest, final long surveyId) {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new NotFoundException(SurveyErrorCode.SURVEY_NOT_FOUND));

        if (surveyResultRepository.existsByUserIdAndSurveyId(userId, survey.getId())) {
            throw new BadRequestException(SurveyResultErrorCode.SURVEY_ALREADY_SUBMITTED);
        }

        SurveyResult surveyResult = resultUpdater.save(resultPostRequest.toEntity(userFinder.findById(userId), survey));
        saveMultipleChoiceAndSubjective(resultPostRequest, surveyResult);
        updateUserPoint(userId);
    }

    @Transactional
    public void createResultForGuest(final ResultPostRequest resultPostRequest, final long surveyId) {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new NotFoundException(SurveyErrorCode.SURVEY_NOT_FOUND));

        SurveyResult surveyResult = resultUpdater.save(resultPostRequest.toEntity(null, survey));
        saveMultipleChoiceAndSubjective(resultPostRequest, surveyResult);
    }


    private void updateUserPoint(final long userId) {
        UserInfo userInfo = userInfoFinder.findByUserId(userId);
        userInfoUpdater.updatePointById(100, userInfo.getId());
    }

    private void saveMultipleChoiceAndSubjective(final ResultPostRequest resultPostRequest, final SurveyResult surveyResult) {
        List<ResultPostRequest.Answer> answers = resultPostRequest.answers();
        answers.stream().forEach(answer -> {
            if (answer.multipleChoiceId() != null) {
                // 객관식 결과 저장
                if (answer.content() != null) {
                    throw new BadRequestException(MultipleChoiceResultErrorCode.MULTIPLE_CHOICE_CONTENT_PRESENT);
                }

                MultipleChoice multipleChoice = multipleChoiceRepository.findById(answer.multipleChoiceId())
                        .orElseThrow(() -> new NotFoundException(MultipleChoiceErrorCode.MULTIPLE_CHOICE_NOT_FOUND));
                Question question = questionRepository.findById(answer.questionId())
                        .orElseThrow(() -> new NotFoundException(QuestionErrorCode.QUESTION_NOT_FOUND));

                MultipleChoiceResult multipleChoiceResult = answer.toMultipleChoiceEntity(
                        surveyResult,
                        multipleChoice,
                        question
                );
                multipleChoiceResultUpdater.save(multipleChoiceResult);
            } else {
                // 주관식 결과 저장
                if (answer.content() == null) {
                    throw new BadRequestException(SubjectiveResultErrorCode.SUBJECTIVE_RESULT_MULTIPLE_CHOICE_ID_PRESENT);
                }

                Question question = questionRepository.findById(answer.questionId())
                        .orElseThrow(() -> new NotFoundException(QuestionErrorCode.QUESTION_NOT_FOUND));

                SubjectiveResult subjectiveResult = answer.toSubjectiveEntity(
                        surveyResult,
                        question,
                        answer.content()
                );
                subjectiveResultUpdater.save(subjectiveResult);
            }

        });
    }

    @Transactional
    public void saveInsightMemo(long surveyId, MySurveysResultMemoRequest request) {
        for (MySurveysResultMemoRequest.QuestionMemo questionMemo : request.questions()) {
            Question question = questionRepository.findById(questionMemo.questionId())
                    .orElseThrow(() -> new NotFoundException(QuestionErrorCode.QUESTION_NOT_FOUND));

            Long memoCount = surveyResultMemoRepository.countByQuestionId(question.getId());

            // 메모의 개수를 확인하고 4개 이상일 경우 예외를 발생시킵니다.
            if (memoCount + questionMemo.memos().size() > 4) {
                throw new BadRequestException(SurveyResultErrorCode.ERROR_MEMO_LIMIT_EXCEEDED);
            } else {
                for (String memoContent : questionMemo.memos()) {
                    SurveyResultMemo newMemo = SurveyResultMemo.create(question, memoContent);
                    surveyResultMemoRepository.save(newMemo);
                }
            }
        }
    }

    @Transactional
    public void deleteSurveyMemo(final long id) {
        surveyResultMemoUpdater.deleteById(surveyResultMemoFinder.findById(id).getId());
    }

    @Transactional
    public void updateSurveyMemos(final SurveyResultMemosPatchRequest surveyResultMemosPatchRequest) {
        surveyResultMemosPatchRequest.memos().forEach(memos ->
            surveyResultMemoFinder.findById(memos.id()).updateContent(memos.content()));
    }
}