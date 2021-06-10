package com.morozov.pubsub.model.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Message in the topic */
public class Message {
  private final String message;
  private final Long ts;

  @JsonCreator
  public Message(@JsonProperty("message") String message, @JsonProperty("ts") Long ts) {
    this.message = message;
    this.ts = ts;
  }

  public Message() {
    this(null, null);
  }

  @JsonGetter
  public String getMessage() {
    return message;
  }

  @JsonGetter
  public Long getTs() {
    return ts;
  }
}
