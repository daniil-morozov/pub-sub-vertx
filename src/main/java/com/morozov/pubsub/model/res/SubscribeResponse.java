package com.morozov.pubsub.model.res;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Subscribe to the topic response */
public class SubscribeResponse {
  private final String subId;
  private final String topic;

  @JsonCreator
  public SubscribeResponse(
      @JsonProperty("subId") String subId, @JsonProperty("topic") String topic) {
    this.subId = subId;
    this.topic = topic;
  }

  public SubscribeResponse() {
    this(null, null);
  }

  @JsonGetter
  public String getSubId() {
    return subId;
  }

  @JsonGetter
  public String getTopic() {
    return topic;
  }
}
