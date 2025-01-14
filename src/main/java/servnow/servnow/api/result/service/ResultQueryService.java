package servnow.servnow.api.result.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import servnow.servnow.api.result.dto.response.MySurveysResultMemoResponse;
import servnow.servnow.api.result.dto.response.MySurveysResultResponse;
import servnow.servnow.api.result.dto.response.UserSurveyAnswerResultResponse;
import servnow.servnow.api.survey.service.SurveyFinder;
import servnow.servnow.api.user.dto.response.MySurveyResponse;
import servnow.servnow.common.code.ErrorCode;
import servnow.servnow.common.code.SurveyErrorCode;
import servnow.servnow.common.exception.NotFoundException;
import servnow.servnow.domain.question.model.enums.QuestionType;
import servnow.servnow.domain.section.model.Section;
import servnow.servnow.domain.survey.repository.SurveyRepository;
import servnow.servnow.domain.surveyresult.repository.SurveyResultRepository;
import servnow.servnow.domain.survey.model.Survey;
import servnow.servnow.domain.question.model.Question;
import servnow.servnow.domain.surveyresult.model.SurveyResult;
import servnow.servnow.domain.subjectiveresult.model.SubjectiveResult;
import servnow.servnow.domain.question.model.MultipleChoice;
import servnow.servnow.domain.surveyresultmemo.model.SurveyResultMemo;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResultQueryService {

    private final SurveyResultRepository surveyResultRepository;
    private final SurveyRepository surveyRepository;
    private final SurveyFinder surveyFinder;


    @Transactional(readOnly = true)
    public MySurveysResultResponse getMySurveysResult(long surveyId) {
        Survey survey = surveyResultRepository.findSurveyWithSectionsById(surveyId);
        if (survey == null) {
            throw new NotFoundException(SurveyErrorCode.SURVEY_NOT_FOUND);
        }

        List<Question> questions = surveyResultRepository.findQuestionsBySurveyId(surveyId);
        List<SurveyResult> surveyResults = surveyResultRepository.findSurveyResultsBySurveyId(surveyId);
        List<SubjectiveResult> subjectiveResults = surveyResultRepository.findSubjectiveResultsBySurveyId(surveyId);

        // MultipleChoiceResult를 통해 응답 수 집계
        Map<Long, Long> choiceResultsMap = surveyResults.stream()
                .flatMap(surveyResult -> surveyResult.getMultipleChoiceResults().stream())
                .collect(Collectors.groupingBy(result -> result.getMultipleChoice().getId(), Collectors.counting()));

        Map<Long, List<SubjectiveResult>> subjectiveResultMap = subjectiveResults.stream()
                .collect(Collectors.groupingBy(sr -> sr.getQuestion().getId()));

        List<MySurveysResultResponse.SectionResult> sectionResults = survey.getSections().stream()
                .map(section -> {
                    List<MySurveysResultResponse.QuestionResult> questionResults = questions.stream()
                            .filter(question -> question.getSection().equals(section))
                            .map(question -> {
                                MySurveysResultResponse.QuestionResult.QuestionType questionType =
                                        mapQuestionType(question.getQuestionType());

                                List<MySurveysResultResponse.QuestionResult.ChoiceResult> choices =
                                        mapChoices(question.getMultipleChoices(), choiceResultsMap);

                                List<MySurveysResultResponse.QuestionResult.SubjectiveResponse> responses =
                                        mapSubjectiveResponses(subjectiveResultMap.getOrDefault(question.getId(), new ArrayList<>()));

                                return new MySurveysResultResponse.QuestionResult(
                                        question.getQuestionOrder(),
                                        question.getTitle(),
                                        question.getContent(),
                                        questionType,
                                        question.isEssential(),
                                        question.isDuplicate(),
                                        question.isHasNextSection(),
                                        choices,
                                        questionType == MySurveysResultResponse.QuestionResult.QuestionType.SUBJECTIVE_SHORT ? responses : List.of()
                                );
                            })
                            .collect(Collectors.toList());

                    return new MySurveysResultResponse.SectionResult(
                            section.getTitle(),
                            section.getContent(),
                            section.getNextSectionNo(),
                            questionResults
                    );
                })
                .collect(Collectors.toList());

        return new MySurveysResultResponse(
                survey.getId(),
                surveyResults.size(),
                sectionResults
        );
    }

    private MySurveysResultResponse.QuestionResult.QuestionType mapQuestionType(QuestionType type) {
        return switch (type) {
            case MULTIPLE_CHOICE -> MySurveysResultResponse.QuestionResult.QuestionType.MULTIPLE_CHOICE;
            case SUBJECTIVE_LONG -> MySurveysResultResponse.QuestionResult.QuestionType.SUBJECTIVE_LONG;
            case SUBJECTIVE_SHORT -> MySurveysResultResponse.QuestionResult.QuestionType.SUBJECTIVE_SHORT;
            default -> throw new IllegalArgumentException("Unknown question type: " + type);
        };
    }

    private List<MySurveysResultResponse.QuestionResult.ChoiceResult> mapChoices(List<MultipleChoice> choices, Map<Long, Long> choiceResultsMap) {
        return choices.stream()
                .map(choice -> new MySurveysResultResponse.QuestionResult.ChoiceResult(
                        choice.getId(),
                        choice.getContent(),
                        choiceResultsMap.getOrDefault(choice.getId(), 0L).intValue() // 응답 수 계산
                ))
                .collect(Collectors.toList());
    }

    private List<MySurveysResultResponse.QuestionResult.SubjectiveResponse> mapSubjectiveResponses(List<SubjectiveResult> results) {
        return results.stream()
                .map(result -> new MySurveysResultResponse.QuestionResult.SubjectiveResponse(
                        result.getSurveyResult().getId(),
                        result.getContent()
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MySurveysResultMemoResponse getInsightMemo(long surveyId) {
        // 1. Survey와 Section을 먼저 로드
        Survey survey = surveyRepository.findByIdWithSections(surveyId)
                .orElseThrow(() -> new NotFoundException(SurveyErrorCode.SURVEY_NOT_FOUND));

        // 2. Section에 속한 Question과 관련된 Memo를 로드
        survey.getSections().forEach(section -> section.getQuestions().forEach(question -> {
            question.getSurveyResultMemos().size();  // Lazy loading
        }));

        // 3. 질문과 메모를 매핑하여 응답 객체를 생성
        List<Question> questions = survey.getSections().stream()
                .flatMap(section -> section.getQuestions().stream())
                .toList();

        List<MySurveysResultMemoResponse.QuestionMemo> questionMemos = questions.stream()
                .map(question -> new MySurveysResultMemoResponse.QuestionMemo(
                        question.getId(),
                        question.getQuestionOrder(),
                        question.getTitle(),
                        question.getSurveyResultMemos().stream()
                                .collect(Collectors.groupingBy(
                                        SurveyResultMemo::getId,
                                        Collectors.mapping(SurveyResultMemo::getContent, Collectors.toList())
                                ))
                                .entrySet().stream()
                                .map(entry -> Map.of(entry.getKey(), entry.getValue().get(0)))
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());

        return new MySurveysResultMemoResponse(questionMemos);
    }

    public List<MySurveyResponse> getJoinSurveys(final long userId, String sort) {
        List<Survey> userSurveys;

        switch (sort) {
            case "newest":
                userSurveys = surveyRepository.findJoinedSurveysOrderByNewest(userId);
                break;
            case "oldest":
                userSurveys = surveyRepository.findJoinedSurveysOrderByOldest(userId);
                break;
            case "participants":
                userSurveys = surveyRepository.findJoinedSurveysOrderByParticipantCountDesc(userId);
                break;
            default:
                throw new IllegalArgumentException("Invalid sort option: " + sort);
        }

        return userSurveys.stream()
                .map(MySurveyResponse::of)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserSurveyAnswerResultResponse getMyAnswerResult(long surveyId, Long userId) {
        // Retrieve the survey along with sections
        Survey survey = surveyResultRepository.findSurveyWithSectionsById(surveyId);
        if (survey == null) {
            throw new NotFoundException(SurveyErrorCode.SURVEY_NOT_FOUND);
        }

        // Retrieve questions related to the survey
        List<Question> questions = surveyResultRepository.findQuestionsBySurveyId(surveyId);

        // Retrieve the user's survey responses and subjective responses
        List<SurveyResult> userSurveyResults = surveyResultRepository.findSurveyResultsBySurveyIdAndUserId(surveyId, userId);
        List<SubjectiveResult> userSubjectiveResults = surveyResultRepository.findSubjectiveResultsBySurveyIdAndUserId(surveyId, userId);

        // Map results into response format
        List<UserSurveyAnswerResultResponse.SectionResult> sectionResults = survey.getSections().stream()
                .map(section -> {
                    List<UserSurveyAnswerResultResponse.QuestionResult> questionResults = questions.stream()
                            .filter(question -> question.getSection().equals(section))
                            .map(question -> {
                                UserSurveyAnswerResultResponse.QuestionResult.QuestionType questionType =
                                        mapQuestionType1(question.getQuestionType());

                                List<UserSurveyAnswerResultResponse.QuestionResult.ChoiceResult> choices =
                                        mapChoices(question.getMultipleChoices(), userSurveyResults);

                                List<UserSurveyAnswerResultResponse.QuestionResult.SubjectiveResponse> responses =
                                        mapSubjectiveResponses(question.getId(), userSubjectiveResults);

                                return new UserSurveyAnswerResultResponse.QuestionResult(
                                        question.getQuestionOrder(),
                                        question.getTitle(),
                                        question.getContent(),
                                        questionType,
                                        question.isEssential(),
                                        question.isDuplicate(),
                                        question.isHasNextSection(),
                                        choices,
                                        responses
                                );
                            })
                            .collect(Collectors.toList());

                    return new UserSurveyAnswerResultResponse.SectionResult(
                            section.getTitle(),
                            section.getContent(),
                            section.getNextSectionNo(),
                            questionResults
                    );
                })
                .collect(Collectors.toList());

        return new UserSurveyAnswerResultResponse(
                survey.getId(),
                sectionResults
        );
    }

    private UserSurveyAnswerResultResponse.QuestionResult.QuestionType mapQuestionType1(QuestionType type) {
        return switch (type) {
            case MULTIPLE_CHOICE -> UserSurveyAnswerResultResponse.QuestionResult.QuestionType.MULTIPLE_CHOICE;
            case SUBJECTIVE_LONG -> UserSurveyAnswerResultResponse.QuestionResult.QuestionType.SUBJECTIVE_LONG;
            case SUBJECTIVE_SHORT -> UserSurveyAnswerResultResponse.QuestionResult.QuestionType.SUBJECTIVE_SHORT;
            default -> throw new IllegalArgumentException("Unknown question type: " + type);
        };
    }

    private List<UserSurveyAnswerResultResponse.QuestionResult.ChoiceResult> mapChoices(List<MultipleChoice> choices, List<SurveyResult> surveyResults) {
        return choices.stream()
                .map(choice -> new UserSurveyAnswerResultResponse.QuestionResult.ChoiceResult(
                        choice.getId(),
                        choice.getContent()
                ))
                .collect(Collectors.toList());
    }

    private List<UserSurveyAnswerResultResponse.QuestionResult.SubjectiveResponse> mapSubjectiveResponses(Long questionId, List<SubjectiveResult> subjectiveResults) {
        return subjectiveResults.stream()
                .filter(result -> result.getQuestion().getId().equals(questionId))
                .map(result -> new UserSurveyAnswerResultResponse.QuestionResult.SubjectiveResponse(
                        result.getSurveyResult().getId(),
                        result.getContent()
                ))
                .collect(Collectors.toList());
    }
}