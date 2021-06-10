package com.morozov.pubsub.model.res;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/** General error response */
public class ErrorResponse {

  private final String errorMessage;

  @JsonCreator
  public ErrorResponse(@JsonProperty("errorMessage") String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public ErrorResponse() {
    this(null);
  }

  @JsonGetter
  public String getErrorMessage() {
    return errorMessage;
  }
}
