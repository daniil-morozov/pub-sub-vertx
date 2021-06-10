# Scale out

## Scaling

In order to scale connections to Redis, you can use vert.x manual:

https://vertx.io/docs/vertx-redis-client/java/#_high_availability_mode

To build a highly-available Redis cluster you should try [Redis Sentinel](https://redis.io/topics/sentinel)

Redis is pretty much capable of providing 20K RPS:

```shell
#100 000 right pushes of size 12800 byte 
# (10% of the max size defined by the requirements)
redis-benchmark -t rpush -n 100000 -q -d 12800
RPUSH: 34530.39 requests per second, p50=0.735 msec #Easy

#100 000 right pushes of MAXIMUM 128Kb size
#Below the requirements, but the chances are there would be 20K RPS of 128Kb are low
redis-benchmark -t rpush -n 100000 -q -d 128000
RPUSH: 4112.01 requests per second, p50=6.575 msec

#100 000 lpops of messages sized 128Kb
redis-benchmark -t lpop -n 100000 -q -d 128000
LPOP: 23180.34 requests per second, p50=0.815 msec #Perfect
```
With clustered Redis we can [try](https://github.com/redis/redis/issues/4041) achieving 20k RPS of 128Kb rpushes

## Message loss prevention

If Redis fails to load, we can try [Guava LoadingCache](https://guava.dev/releases/19.0/api/docs/com/google/common/cache/LoadingCache.html) inside our verticles.
```java
// Try create the cache with some TTL defined 
// (for example, 1 day, within which we should be able to restore the Redis cluster)
public class CacheWithExpiryTime implements CacheProvider {

  private final Duration duration;

  private CacheWithExpiryTime(Duration duration) {
    this.duration = duration;
  }

  public static CacheWithExpiryTime ofDuration(Duration duration) {
    return new CacheWithExpiryTime(duration);
  }

  @Override
  public <Key> LoadingCache<Key, Message> getCache() {
    final CacheLoader<Key, Boolean> loader = CacheLoader.from((key) -> message);

    return CacheBuilder.newBuilder().expireAfterWrite(duration).build(loader);
  }
}
```
This Cache can be used in every API command inside `.onFailure` handler