package servnow.servnow.common.code;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

  HttpStatus getHttpStatus();
  String getMessage();

}