package com.morozov.pubsub.model.req;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Publish message by publisher request */
public class PublishMessageRequest {
  private final String pubId;
  private final String message;

  @JsonCreator
  public PublishMessageRequest(
      @JsonProperty("pubId") String pubId, @JsonProperty("message") String message) {
    this.pubId = pubId;
    this.message = message;
  }

  public PublishMessageRequest() {
    this(null, null);
  }

  @JsonGetter
  public String getPubId() {
    return pubId;
  }

  @JsonGetter
  public String getMessage() {
    return message;
  }
}
