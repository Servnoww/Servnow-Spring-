package servnow.servnow.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import servnow.servnow.api.dto.ServnowResponse;
import servnow.servnow.common.code.AuthErrorCode;
import servnow.servnow.common.code.ErrorCode;
import servnow.servnow.common.exception.UnauthorizedException;

import java.io.IOException;
import java.io.PrintWriter;

public class ExceptionHandlerFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (UnauthorizedException e) {
            handleUnauthorizedException(response, e);
        } catch (Exception ee) {
            handleException(response, ee);
        }
    }

    private void handleUnauthorizedException(
            HttpServletResponse response,
            Exception e) throws IOException {
        UnauthorizedException ue = (UnauthorizedException) e;
        ErrorCode authErrorCode = ue.getErrorCode();
        HttpStatus httpStatus = authErrorCode.getHttpStatus();
        setResponse(response, httpStatus, authErrorCode);
    }

    private void handleException(
            HttpServletResponse response,
            Exception e) throws IOException {
        setResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, AuthErrorCode.INTERNAL_SERVER_ERROR);
    }

    private void setResponse(
            HttpServletResponse response,
            HttpStatus httpStatus,
            ErrorCode authErrorCode) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("utf-8");
        response.setStatus(httpStatus.value());
        PrintWriter writer = response.getWriter();
        writer.write(objectMapper.writeValueAsString(ServnowResponse.fail(authErrorCode)));
    }
}