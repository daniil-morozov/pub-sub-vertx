# Pub/Sub Restful service
This API will be used to publish and receive messages for specified topics

Thus, must allow the following actions:

* Register a publisher to a topic
* Publish to a topic
* Subscribe to a topic
* Get a message from a topic
* Ack a message from a topic
## Tech Stack

* Java 11
* Gradle 7.0
* [Vert.x](https://vertx.io/) for non-blocking API
  * Based on **netty**
  * Decided to use to achieve higher RPS comparing to traditional Spring
* [Redis](https://redis.io/) for topic/message storage
  * Allows huge amount of RPS
* [wrk](https://github.com/wg/wrk) for load testing
  * Simple to peform load tests

## Installation

Clone the project from git repository
```shell
https://github.com/daniil-morozov/pub-sub-vertx.git
```

## Prerequisites

* JDK 11 or higher
* Gradle
* Docker or Redis installed locally with default port 6379
* wrk if you want to pefrorm load tests

## Usage

Just set up the environment and run the shadow jar file

```shell
# Go to the cloned repo folder
# If no local Redis was installed then 
#   Run docker compose file to set up the environment
docker-compose up
# Otherwise 
#   Make sure the local redis is up

# Build the project
gradle build
# Change dir
cd build/libs/
# Run the shadow jar
java -jar pub-sub-1.0.0-SNAPSHOT-fat.jar

# Now you can call the API by accessing localhost:8080/topic/register or other endpoints 
```

## Testing

### There is a postman collection to perform a quick try
```shell
#Just import the following collection Postman
PubSub.postman_collection.json
#And don't forget to check the further API usage manual to understand how to pefrorm requests
```

Unfortunately, I didn't have enough time to make the unit/integration tests, but I made few load tests
### Load testing

```shell
# For load tests you can install [wrk](https://github.com/wg/wrk)
# And use some of the scripts included in test/resources
# ...
# For example, publish messages with 40 threads using 40 connections during 60 seconds
wrk -t40 -c40 -d60s http://127.0.0.1:8080/message/publish/test-topic -s publish-message.lua
# Or ack messages with 40 threads using 40 connections during 120 seconds
wrk -t40 -c40 -d120s http://127.0.0.1:8080/message/ack/test-topic -s ack-message.lua
```

## Test results on MacBook Pro (16-inch, 2019) 2,4 GHz 8-Core Intel Core i9 32 GB 2667 MHz DDR4

| Command | Description |
| :--- | :--- |
| `wrk -t40 -c40 -d60s http://127.0.0.1:8080/message/publish/test-topic` | Publish a message around 128KB each |

|Thread Stats | Avg | Stdev | Max | +/- Stdev |
| :--- | :--- | :--- | :--- | :--- |
| Latency | 20.92ms | 40.42ms | 513.80ms |  93.48% |
| Req/Sec | 79.30 | 24.27 | 151.00 | 78.96% |
```
185105 requests in 1.00m, 9.00MB read
Requests/sec:   3080.23
Transfer/sec:    153.41KB
```

| Command | Description |
| :--- | :--- |
| `wrk -t40 -c40 -d120s http://127.0.0.1:8080/message/ack/test-topic` | Ack messages from the same topic |

|Thread Stats | Avg | Stdev | Max | +/- Stdev |
| :--- | :--- | :--- | :--- | :--- |
| Latency | 16.74ms | 21.42ms | 296.03ms |  89.95% |
| Req/Sec | 87.10 | 157.00 | 1.66k | 92.26% |
```
411356 requests in 2.00m, 11.06GB read
Requests/sec:   3425.43
Transfer/sec:     94.30MB
```

### Estimated system workload: 200 rps, 99% latency within one second is respected

# How the app works

## Basic logic

### Publishers
* Can register to a single topic
  * Registration will give you a publisher id (UUID), you need to remember it to publish messages
* Can publish messages to the registered topic
  * The order is FIFO

### Subscribers
* Can subscribe to multiple topics. 
  * After the subscription you need to remember the subscriber id in order to get/ack messages
* Can get messages concurrently from a subscribed topic
  * If publisher published "message-1" and then "message-2"
    * The subscriber will have to first **ack** "message-1" before it can **get** "message-2"
* Can ack messages concurrently from a subscribed topic
* If publisher published "message-1" and then "message-2"
    * The subscriber will have to first **ack** "message-1" before it can  **ack** "message-2"

## Redis explanation

I wasn't sure if I could use the existing message brokers, so I decided to use Redis, because:
* it's robust
* quite scalable
* convenient to build message queues

## How subscribers are delimited to consume messages

* Subscribers consume messages from the same topic **concurrently**
* A subscriber can only consume messages that are produced after it subscribed to a topic
  * This is achieved by adding timestamps to every message in the topic
    * The timestamp of the message is first compared to the timestamp of the subscriber registration
      and then the server decides if the subscribed has the right to read the message
# API Description

## Topic

**Register a publisher to a topic**
----
Register a publisher to a specified topic.

**You cannot register multiple publishers to the same topic**

* **URL**

  ```http
  POST /topic/register/:topicId
  ```
  
* **Path Params**

  | Parameter | Type | Description |
    | :--- | :--- | :--- |
  | `topicId` | `String` | **Required**. Topic id |

* **Data Params**

  None

* **Success Response:**

    * **Code:** 200 OK <br />
      **Content:**
      Publisher id
      ```json
      {
        "pubId": "c206fd8d-a105-4ba5-aadd-1efb946a8d84" 
      }
      ```

* **Error Response:**

    * **Code:** 400 BAD REQUEST <br />
      **Content:**
      ```json
      "Topic %topicId% already has a registered publisher!"
      ```

* **Sample Call:**

    ```shell
    curl --location --request POST '{{url:port}}/topic/register/test-topic' \
    --header 'Content-Type: application/json'
  ```
#### What happens internally:
* Redis performs `set`: key=`%topicId%-publisher` value = `%pubId%`

**Subscribe to a topic**
----
Subscribe to a specified topic.

**You can subscribe multiple subscribers to the same topic, but they will consume messages concurrently**

* **URL**

  ```http
  POST /topic/subscribe/:topicId
  ```

* **Path Params**

  | Parameter | Type | Description |
  | :--- | :--- | :--- |
  | `topicId` | `String` | **Required**. Topic id |

* **Data Params**

  None

* **Success Response:**

    * **Code:** 200 OK <br />
      **Content:**
      Publisher id
      ```json
      {
        "subId": "c206fd8d-a105-4ba5-aadd-1efb946a8d84",
        "topic": "test-topic" 
      }
      ```

* **Error Response:**

    * If topic was not set in URL
    * **Code:** 400 BAD REQUEST <br />
      **Content:**
      ```json
      "Topic was not set!"
      ```

    * **Code:** 404 NOT FOUND <br />
      **Content:**
      ```json
      "Topic was not found!"
      ```

* **Sample Call:**

    ```shell
    curl --location --request POST '{{url:port}}/topic/subscribe/test-topic' \
    --header 'Content-Type: application/json'
  ```

#### What happens internally:
* Redis performs `set`: key=`%subId%` value = 
  ```json
  {
    "subId": "%subId%",
    "topic": "%topicId%",
    "ts": "%timestamp at the moment of subscription%"
  }
  ```

## Message

**Publish a message to a topic**
----
Publish a message to a topic. <br>
Message size can be up to 128Kb <br>
If the published id is incorrect, the server will respond with an error<br>

* **URL**

  ```http
  POST /message/publish/topicId
  ```

* **Path Params**

  | Parameter | Type | Description |
  | :--- | :--- | :--- |
  | `topicId` | `String` | **Required**. Topic id |

* **Data Params**

  **Non required:**

  JSON Payload

  **Content:**

  | Parameter | Type | Description |
  | :--- | :--- | :--- |
  | `pubId` | `String (UUID)` | **Required**. publisher id |


* **Success Response:**

    * **Code:** 200 OK <br />
      **Content:**
      ```text
      Message sent
      ```

* **Error Response:**

    * If topic was not set in URL
    * **Code:** 400 BAD REQUEST <br />
      **Content:**
      ```json
      "Topic was not set!"
      ```

    * **Code:** 400 BAD REQUEST <br />
      **Content:**
      ```json
      "Couldn't read request body"
      ```
  * **Code:** 401 UNAUTHORIZED <br />
    **Content:**
    ```json
    "Publisher %publisher id% is not registered to topic %topicId% and cannot publish messages to it"
    ```

* **Sample Call:**

    ```shell
    curl --location --request POST '{{url:port}}/message/publish/test-topic' \
    --header 'Content-Type: application/json' \
    --data-raw '{
    "pubId": "d92714b1-93d5-422c-84b4-d41a671eb049"
    }'
  ```

#### What happens internally:
* Redis performs `rpush` a list key = `%topicId%`, value = 
```json
  {
    "message": "%message%",
    "ts": "%timestamp at the moment of publishing%"
  }
  ```

**Get a message from a topic**
----
Get a message from a topic. <br>
The message won't be deleted from the topic, the behaviour is like a `peek` method<br>

* **URL**

  ```http
  GET /message/get/topicId
  ```

* **Path Params**

  | Parameter | Type | Description |
  | :--- | :--- | :--- |
  | `topicId` | `String` | **Required**. Topic id |

* **Data Params**

  **Non required:**

  JSON Payload

  **Content:**

  | Parameter | Type | Description |
  | :--- | :--- | :--- |
  | `subId` | `String (UUID)` | **Required**. subscriber id |


* **Success Response:**

    * **Code:** 200 OK <br />
      **Content:**
      ```json
      {
        "message" : "Topic earliest message"
      } 
      ```
      or, if no messages are left, just a blank response
      ```text
      ```

* **Error Response:**

    * If topic was not set in URL
    * **Code:** 400 BAD REQUEST <br />
      **Content:**
      ```json
      "Topic was not set!"
      ```

    * **Code:** 400 BAD REQUEST <br />
      **Content:**
      ```json
      {
        "errorMessage" : "Couldn't read request body"
      }
      ```
      
    * If the subscriber id is not listed in subscribers
    * **Code:** 404 NOT FOUND <br />
      **Content:**
      ```json 
      {
        "errorMessage" : "Unknown subscriber id %subId%"
      }
      ```

* **Sample Call:**

    ```shell
    curl --location --request GET '{{url:port}}/message/get/test-topic' \
    --header 'Content-Type: application/json' \
    --data-raw '{
    "subId": "d92714b1-93d5-422c-84b4-d41a671eb049"
    }'
  ```

#### What happens internally:
* Redis performs `lrange 0 0` by a `%topicId%` key

**Ack a message from a topic**
----
Acknowledge a message from a topic. <br>
The message will be deleted from the topic, the behaviour is like a `pop` queue method<br>

* **URL**

  ```http
  DELETE /message/ack/topicId
  ```

* **Path Params**

  | Parameter | Type | Description |
  | :--- | :--- | :--- |
  | `topicId` | `String` | **Required**. Topic id |

* **Data Params**

  **Non required:**

  JSON Payload

  **Content:**

  | Parameter | Type | Description |
  | :--- | :--- | :--- |
  | `subId` | `String (UUID)` | **Required**. subscriber id |


* **Success Response:**

    * **Code:** 200 OK <br />
      **Content:**
      ```json
      {
        "message" : "Topic earliest message"
      } 
      ```
      or, if no messages are left, just a blank response
      ```text
      ```

* **Error Response:**

    * If topic was not set in URL
    * **Code:** 400 BAD REQUEST <br />
      **Content:**
      ```json
      "Topic was not set!"
      ```

    * **Code:** 400 BAD REQUEST <br />
      **Content:**
      ```json
      {
        "errorMessage" : "Couldn't read request body"
      }
      ```

    * If the subscriber id is not listed in subscribers
    * **Code:** 404 NOT FOUND <br />
      **Content:**
      ```json 
      {
        "errorMessage" : "Unknown subscriber id %subId%"
      }
      ```

* **Sample Call:**

    ```shell
    curl --location --request DELETE '{{url:port}}/message/ack/test-topic' \
    --header 'Content-Type: application/json' \
    --data-raw '{
    "subId": "d92714b1-93d5-422c-84b4-d41a671eb049"
    }'
  ```

#### What happens internally:
* Redis performs `lpop` from a list by a `%topicId%` key

## Improvement points

* Missing unit/integration tests
* Weak validation
* Code structure might be clearer

# Scale out plan is Below

[Scale Out](scale-out.md)


