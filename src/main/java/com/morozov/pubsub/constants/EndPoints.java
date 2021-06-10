package com.morozov.pubsub.constants;

public enum EndPoints {
  TOPIC_REGISTER("/topic/register/"),
  TOPIC_SUBSCRIBE("/topic/subscribe/"),
  MESSAGE_PUBLISH("/message/publish/"),
  MESSAGE_GET("/message/get/"),
  MESSAGE_ACK("/message/ack/");

  public String getVal() {
    return val;
  }

  private final String val;

  EndPoints(String s) {
    val = s;
  }
}
