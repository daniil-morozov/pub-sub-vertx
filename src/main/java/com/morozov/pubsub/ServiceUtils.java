package com.morozov.pubsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.morozov.pubsub.constants.CommonConstants;
import com.morozov.pubsub.model.dto.Message;
import com.morozov.pubsub.model.dto.SubscriberInfo;
import com.morozov.pubsub.model.req.PublishMessageRequest;
import com.morozov.pubsub.model.res.ErrorResponse;
import com.morozov.pubsub.model.res.SubscribeResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.RedisAPI;
import java.util.List;
import java.util.UUID;

/** Main service utils */
public class ServiceUtils {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Logger logger = LoggerFactory.getLogger(ServiceUtils.class);

  public static void publishMessage(
      RedisAPI redisApi, RoutingContext rc, PublishMessageRequest request, String topicQueue) {
    try {
      final var message = new Message(request.getMessage(), System.currentTimeMillis());
      final var messageJson = MAPPER.writeValueAsString(message);

      redisApi
          .rpush(List.of(topicQueue, messageJson))
          .onSuccess(val -> rc.response().setStatusCode(200).end("Message sent"))
          .onFailure(throwable -> writeInternalServerError(rc, throwable));
    } catch (JsonProcessingException e) {
      writeInternalServerError(rc, e);
    }
  }

  public static void subscribeToTopic(RedisAPI redisApi, RoutingContext rc, String topic) {
    final var subInfo =
        new SubscriberInfo(UUID.randomUUID().toString(), topic, System.currentTimeMillis());

    try {
      redisApi
          .set(List.of(subInfo.getSubId(), MAPPER.writeValueAsString(subInfo)))
          .onSuccess(
              val -> {
                final var response =
                    ServiceUtils.toJsonString(new SubscribeResponse(subInfo.getSubId(), topic), rc);
                rc.response()
                    .setStatusCode(200)
                    .putHeader(
                        CommonConstants.CONTENT_TYPE_HEADER, CommonConstants.APPLICATION_JSON)
                    .end(response);
              })
          .onFailure(throwable -> writeInternalServerError(rc, throwable));
    } catch (JsonProcessingException e) {
      writeInternalServerError(rc, e);
    }
  }

  /**
   * Try converting into an object from JSON string
   *
   * @param source source string
   * @param clazz target class
   * @param rc routing context to fill response
   * @param <T> target class type
   * @return converted object or null
   */
  public static <T> T fromString(String source, Class<T> clazz, RoutingContext rc) {
    try {
      return MAPPER.readValue(source, clazz);
    } catch (JsonProcessingException e) {
      writeInternalServerError(rc, e);
    }

    return null;
  }

  /**
   * Try converting to json string
   *
   * @param obj object
   * @param rc routing context to fill the response
   * @param <T> Source type
   * @return String or null
   */
  public static <T> String toJsonString(T obj, RoutingContext rc) {
    try {
      return MAPPER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      writeInternalServerError(rc, e);
    }

    return null;
  }

  /**
   * Write an error message to the response
   *
   * @param rc routing context
   * @param errorCode error code
   * @param message message
   */
  public static void writeError(RoutingContext rc, Integer errorCode, String message) {
    logger.warn(message);

    try {
      rc.response()
          .setStatusCode(errorCode)
          .putHeader(CommonConstants.CONTENT_TYPE_HEADER, CommonConstants.APPLICATION_JSON)
          .end(MAPPER.writeValueAsString(new ErrorResponse(message)));
    } catch (JsonProcessingException e) {
      logger.warn(e.getMessage());
    }
  }

  /**
   * Write an internal server error to the response
   *
   * @param rc routing context
   * @param throwable error info
   */
  public static void writeInternalServerError(RoutingContext rc, Throwable throwable) {
    logger.warn(throwable.getMessage());

    try {
      rc.response()
          .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
          .putHeader(CommonConstants.CONTENT_TYPE_HEADER, CommonConstants.APPLICATION_JSON)
          .end(MAPPER.writeValueAsString(new ErrorResponse("Something went wrong")));
    } catch (JsonProcessingException e) {
      logger.warn(e.getMessage());
    }
  }

  /**
   * Write a bad request error to the response
   *
   * @param rc routing context
   * @param throwable error info
   * @param message message
   */
  public static void writeBadRequestError(RoutingContext rc, Throwable throwable, String message) {
    logger.warn(throwable.getMessage());

    try {
      rc.response()
          .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
          .putHeader(CommonConstants.CONTENT_TYPE_HEADER, CommonConstants.APPLICATION_JSON)
          .end(MAPPER.writeValueAsString(new ErrorResponse(message)));
    } catch (JsonProcessingException e) {
      writeInternalServerError(rc, e);
    }
  }
}
