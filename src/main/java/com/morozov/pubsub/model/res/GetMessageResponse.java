package com.morozov.pubsub.model.res;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Get message by subscriber response */
public class GetMessageResponse {
  private final String message;

  @JsonCreator
  public GetMessageResponse(@JsonProperty("message") String message) {
    this.message = message;
  }

  public GetMessageResponse() {
    this(null);
  }

  @JsonGetter
  public String getMessage() {
    return message;
  }
}
