package com.morozov.pubsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.morozov.pubsub.constants.CommonConstants;
import com.morozov.pubsub.constants.EndPoints;
import com.morozov.pubsub.model.dto.Message;
import com.morozov.pubsub.model.dto.SubscriberInfo;
import com.morozov.pubsub.model.req.GetMessageRequest;
import com.morozov.pubsub.model.req.PublishMessageRequest;
import com.morozov.pubsub.model.res.GetMessageResponse;
import com.morozov.pubsub.model.res.RegisterPublisherResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.RedisOptions;
import java.util.List;
import java.util.UUID;

/** Main verticle */
public class ServiceVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(ServiceVerticle.class);
  private static RedisConnection redisClient;
  private static RedisAPI redisApi;
  private static final RedisOptions options = new RedisOptions();
  private static final int MAX_RECONNECT_RETRIES = 16;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Main method: deploy the main verticle here
   *
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    createRedisClient()
        .onSuccess(
            conn -> {
              redisClient = conn;
              redisApi = RedisAPI.api(redisClient);
              logger.info("Created Redis client");
            });

    var vertx = Vertx.vertx();
    vertx.deployVerticle(new ServiceVerticle());
  }

  /**
   * Create Redis client connection with reconnection attempt
   *
   * @return Connection
   */
  private static Future<RedisConnection> createRedisClient() {
    Promise<RedisConnection> promise = Promise.promise();
    var vertx = Vertx.vertx();
    Redis.createClient(vertx, options)
        .connect()
        .onSuccess(
            conn -> {
              logger.info("Connected to Redis");
              conn.exceptionHandler(
                  e -> {
                    attemptReconnect(0);
                  });
              promise.complete(conn);
            });

    return promise.future();
  }

  /** Attempt to reconnect up to MAX_RECONNECT_RETRIES */
  private static void attemptReconnect(int retry) {
    var vertx = Vertx.vertx();

    if (retry > MAX_RECONNECT_RETRIES) {
      logger.error("Failed connecting to Redis");
    } else {
      // retry with backoff up to 10240 ms
      long backoff = (long) (Math.pow(2, Math.min(retry, 10)) * 10);

      vertx.setTimer(
          backoff,
          timer -> {
            createRedisClient().onFailure(t -> attemptReconnect(retry + 1));
          });
    }
  }

  @Override
  public void start() throws Exception {
    super.start();

    var router = makeRouter();

    vertx.createHttpServer().requestHandler(router).listen(getPort(), getAsyncResultHandler());
  }

  private Handler<AsyncResult<HttpServer>> getAsyncResultHandler() {
    return result -> {
      if (result.succeeded()) {
        logger.info("Started");
      } else {
        logger.info("Failed to start");
      }
    };
  }

  private Integer getPort() {
    return config().getInteger("http.port", 8080);
  }

  /**
   * Organize http routing
   *
   * @return Router object
   */
  private Router makeRouter() {
    Router router = Router.router(vertx);
    router
        .post(EndPoints.TOPIC_REGISTER.getVal() + CommonConstants.TOPIC_URL_PARAM)
        .produces(CommonConstants.APPLICATION_JSON)
        .handler(ServiceVerticle::registerPublisher);
    router
        .post(EndPoints.MESSAGE_PUBLISH.getVal() + CommonConstants.TOPIC_URL_PARAM)
        .produces(CommonConstants.APPLICATION_JSON)
        .handler(makeBodyHandler())
        .handler(ServiceVerticle::publishMessage);

    router
        .post(EndPoints.TOPIC_SUBSCRIBE.getVal() + CommonConstants.TOPIC_URL_PARAM)
        .produces(CommonConstants.APPLICATION_JSON)
        .handler(makeBodyHandler())
        .handler(ServiceVerticle::subscribe);

    router
        .get(EndPoints.MESSAGE_GET.getVal() + CommonConstants.TOPIC_URL_PARAM)
        .produces(CommonConstants.APPLICATION_JSON)
        .handler(makeBodyHandler())
        .handler(ServiceVerticle::getMessage);

    router
        .delete(EndPoints.MESSAGE_ACK.getVal() + CommonConstants.TOPIC_URL_PARAM)
        .produces(CommonConstants.APPLICATION_JSON)
        .handler(makeBodyHandler())
        .handler(ServiceVerticle::ackMessage);
    return router;
  }

  /**
   * Body handler: prevent messages larger than 128Kb
   *
   * @return
   */
  private static BodyHandler makeBodyHandler() {
    return BodyHandler.create().setBodyLimit(128000);
  }

  /**
   * Register Publisher POST Method handler
   *
   * @param rc routing context
   */
  private static void registerPublisher(RoutingContext rc) {
    final var topic = rc.request().getParam(CommonConstants.TOPIC);

    redisApi
        .get(topic + CommonConstants.PUBLISHER_SUFFIX)
        .onSuccess(value -> tryRegisterPublisherForTopic(rc, topic, value))
        .onFailure(throwable -> ServiceUtils.writeInternalServerError(rc, throwable));
  }

  /**
   * Try to register the publisher for the topic
   *
   * @param rc routing context
   * @param topic specified topic
   * @param publisherFromRedis publisher taken from Redis
   */
  private static void tryRegisterPublisherForTopic(
      RoutingContext rc, String topic, io.vertx.redis.client.Response publisherFromRedis) {
    if (publisherFromRedis == null) {
      final var pubId = UUID.randomUUID().toString();
      redisApi
          .set(List.of(topic + CommonConstants.PUBLISHER_SUFFIX, pubId))
          .onSuccess(
              val -> {
                final var response =
                    ServiceUtils.toJsonString(new RegisterPublisherResponse(pubId), rc);
                rc.response().setStatusCode(200).end(response);
              })
          .onFailure(throwable -> ServiceUtils.writeInternalServerError(rc, throwable));
    } else {
      rc.response()
          .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
          .end("Topic " + topic + " already has a registered publisher!");
    }
  }

  /**
   * Publisher Message POST Method handler
   *
   * @param rc routing context
   */
  private static void publishMessage(RoutingContext rc) {
    final var topic = rc.request().getParam(CommonConstants.TOPIC);

    if (topic == null) {
      rc.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end("Topic was not set");
      return;
    }

    try {
      final var request = MAPPER.readValue(rc.getBodyAsString(), PublishMessageRequest.class);

      redisApi
          .get(topic + CommonConstants.PUBLISHER_SUFFIX)
          .onSuccess(value -> tryPublishMessage(rc, topic, request, value))
          .onFailure(throwable -> ServiceUtils.writeInternalServerError(rc, throwable));
    } catch (JsonProcessingException e) {
      logger.warn(e.getMessage());
      rc.response()
          .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
          .end("Couldn't read request body");
    }
  }

  /**
   * Try to publish a message into the topic
   *
   * @param rc routing context
   * @param topic topic
   * @param request request data
   * @param topicFromRedis topic from Redis
   */
  private static void tryPublishMessage(
      RoutingContext rc,
      String topic,
      PublishMessageRequest request,
      io.vertx.redis.client.Response topicFromRedis) {

    if (topicFromRedis == null) {
      ServiceUtils.writeError(rc, HttpResponseStatus.NOT_FOUND.code(), "Topic was not found");
    } else {
      if (topicFromRedis.toString().equals(request.getPubId())) {

        ServiceUtils.publishMessage(redisApi, rc, request, topic);
      } else {
        ServiceUtils.writeError(
            rc,
            HttpResponseStatus.UNAUTHORIZED.code(),
            "Publisher "
                + request.getPubId()
                + " is not registered to topic "
                + topic
                + " and cannot publish messages to it");
      }
    }
  }

  /**
   * Subscribe Message POST Method handler
   *
   * @param rc routing context
   */
  private static void subscribe(RoutingContext rc) {
    final var topic = rc.request().getParam(CommonConstants.TOPIC);

    if (topic == null) {
      ServiceUtils.writeError(rc, HttpResponseStatus.BAD_REQUEST.code(), "Topic was not set");
      return;
    }

    redisApi
        .get(topic + CommonConstants.PUBLISHER_SUFFIX)
        .onSuccess(
            value -> {
              if (value == null) {
                ServiceUtils.writeError(
                    rc, HttpResponseStatus.NOT_FOUND.code(), "Topic was not found");
                return;
              }

              ServiceUtils.subscribeToTopic(redisApi, rc, topic);
            })
        .onFailure(throwable -> ServiceUtils.writeInternalServerError(rc, throwable));
  }

  /**
   * Get Message Get Method handler (get a message, but don't take it out from the topic)
   *
   * @param rc routing context
   */
  private static void getMessage(RoutingContext rc) {
    final var topic = rc.request().getParam(CommonConstants.TOPIC);

    if (topic == null) {
      ServiceUtils.writeError(rc, HttpResponseStatus.BAD_REQUEST.code(), "Topic was not set");
      return;
    }

    try {
      final var request = MAPPER.readValue(rc.getBodyAsString(), GetMessageRequest.class);
      final var subId = request.getSubId();

      redisApi
          .get(subId)
          .onSuccess(subInfoResponse -> tryGetMessage(rc, topic, subId, subInfoResponse))
          .onFailure(throwable -> ServiceUtils.writeInternalServerError(rc, throwable));
    } catch (JsonProcessingException e) {
      ServiceUtils.writeBadRequestError(rc, e, "Couldn't read request body");
    }
  }

  /**
   * Try to get the message
   *
   * @param rc routing context
   * @param topic topic
   * @param subId subscriber id
   * @param subInfoFromRedis subscriber info
   */
  private static void tryGetMessage(
      RoutingContext rc,
      String topic,
      String subId,
      io.vertx.redis.client.Response subInfoFromRedis) {
    if (subInfoFromRedis == null) {
      ServiceUtils.writeError(
          rc, HttpResponseStatus.NOT_FOUND.code(), "Unknown subscriber id " + subId);
      return;
    }

    final var subInfo =
        ServiceUtils.fromString(subInfoFromRedis.toString(), SubscriberInfo.class, rc);

    if (!subInfo.getTopic().equals(topic)) {
      ServiceUtils.writeError(
          rc,
          HttpResponseStatus.NOT_FOUND.code(),
          "The subscriber " + subId + "is not subscribed to topic " + topic);
      return;
    }

    redisApi
        .lrange(topic, "0", "0")
        .onSuccess(
            rangeValue -> {
              if (rangeValue == null || rangeValue.size() == 0) {
                rc.response().setStatusCode(HttpResponseStatus.OK.code()).end("");
                return;
              }

              final var message =
                  ServiceUtils.fromString(rangeValue.get(0).toString(), Message.class, rc);

              if (subInfo.getTs() > message.getTs()) {
                rc.response().setStatusCode(HttpResponseStatus.OK.code()).end("");
              } else {
                rc.response()
                    .setStatusCode(HttpResponseStatus.OK.code())
                    .putHeader(
                        CommonConstants.CONTENT_TYPE_HEADER, CommonConstants.APPLICATION_JSON)
                    .end(
                        ServiceUtils.toJsonString(
                            new GetMessageResponse(message.getMessage()), rc));
              }
            })
        .onFailure(throwable -> ServiceUtils.writeInternalServerError(rc, throwable));
  }

  /**
   * Ack Message Delete Method handler (get a message and take it out from the topic)
   *
   * @param rc routing context
   */
  private static void ackMessage(RoutingContext rc) {
    final var topic = rc.request().getParam(CommonConstants.TOPIC);

    if (topic == null) {
      ServiceUtils.writeError(rc, HttpResponseStatus.BAD_REQUEST.code(), "Topic was not set");
      return;
    }

    try {
      final var request = MAPPER.readValue(rc.getBodyAsString(), GetMessageRequest.class);
      final var subId = request.getSubId();

      redisApi
          .get(subId)
          .onSuccess(subInfoResponse -> tryAckMessage(rc, topic, subId, subInfoResponse))
          .onFailure(throwable -> ServiceUtils.writeInternalServerError(rc, throwable));
    } catch (JsonProcessingException e) {
      ServiceUtils.writeInternalServerError(rc, e);
    }
  }

  private static void tryAckMessage(
      RoutingContext rc,
      String topic,
      String subId,
      io.vertx.redis.client.Response subInfoResponse) {
    if (subInfoResponse == null) {
      ServiceUtils.writeError(
          rc, HttpResponseStatus.NOT_FOUND.code(), "Unknown subscriber id " + subId);
      return;
    }

    final var subInfo =
        ServiceUtils.fromString(subInfoResponse.toString(), SubscriberInfo.class, rc);

    if (!subInfo.getTopic().equals(topic)) {
      ServiceUtils.writeError(
          rc,
          HttpResponseStatus.NOT_FOUND.code(),
          "The subscriber " + subId + "is not subscribed to topic " + topic);
      return;
    }

    redisApi
        .lrange(topic, "0", "0")
        .onSuccess(
            rangeValue -> {
              if (rangeValue == null || rangeValue.size() == 0) {
                rc.response().setStatusCode(HttpResponseStatus.OK.code()).end("");
                return;
              }
              final var message =
                  ServiceUtils.fromString(rangeValue.get(0).toString(), Message.class, rc);

              if (subInfo.getTs() > message.getTs()) {
                rc.response().setStatusCode(HttpResponseStatus.OK.code()).end("");
              } else {
                redisApi
                    .lpop(topic)
                    .onSuccess(
                        responseValue -> {
                          if (responseValue == null) {
                            rc.response().setStatusCode(HttpResponseStatus.OK.code()).end("");
                            return;
                          }

                          rc.response()
                              .setStatusCode(HttpResponseStatus.OK.code())
                              .putHeader(
                                  CommonConstants.CONTENT_TYPE_HEADER,
                                  CommonConstants.APPLICATION_JSON)
                              .end(
                                  ServiceUtils.toJsonString(
                                      new GetMessageResponse(message.getMessage()), rc));
                        })
                    .onFailure(throwable -> ServiceUtils.writeInternalServerError(rc, throwable));
              }
            })
        .onFailure(throwable -> ServiceUtils.writeInternalServerError(rc, throwable));
  }
}
