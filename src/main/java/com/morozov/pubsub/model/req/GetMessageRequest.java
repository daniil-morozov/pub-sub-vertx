package com.morozov.pubsub.model.req;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Get message by subscriber request */
public class GetMessageRequest {
  private final String subId;

  @JsonCreator
  public GetMessageRequest(@JsonProperty("subId") String subId) {
    this.subId = subId;
  }

  public GetMessageRequest() {
    this(null);
  }

  @JsonGetter
  public String getSubId() {
    return subId;
  }
}
