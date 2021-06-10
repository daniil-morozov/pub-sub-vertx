package com.morozov.pubsub.model.res;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Register publisher response */
public class RegisterPublisherResponse {
  private final String pubId;

  @JsonCreator
  public RegisterPublisherResponse(@JsonProperty("pubId") String pubId) {
    this.pubId = pubId;
  }

  public RegisterPublisherResponse() {
    this(null);
  }

  @JsonGetter
  public String getPubId() {
    return pubId;
  }
}
