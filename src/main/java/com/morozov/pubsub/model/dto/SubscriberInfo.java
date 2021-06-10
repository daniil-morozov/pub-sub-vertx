package com.morozov.pubsub.model.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Subscriber information */
public class SubscriberInfo {
  private final String subId;
  private final String topic;
  private final Long ts;

  @JsonCreator
  public SubscriberInfo(
      @JsonProperty("subId") String subId,
      @JsonProperty("topic") String topic,
      @JsonProperty("ts") Long ts) {
    this.subId = subId;
    this.topic = topic;
    this.ts = ts;
  }

  public SubscriberInfo() {
    this(null, null, null);
  }

  @JsonGetter
  public String getSubId() {
    return subId;
  }

  @JsonGetter
  public Long getTs() {
    return ts;
  }

  @JsonGetter
  public String getTopic() {
    return topic;
  }
}
