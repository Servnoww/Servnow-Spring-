package servnow.servnow.common.exception;

import lombok.Getter;
import servnow.servnow.common.code.ErrorCode;

@Getter
public class BadRequestException extends RuntimeException {
  private final ErrorCode errorCode;

  public BadRequestException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }
}
