# Quick Start Usage Guide

A practical guide to using the `@DistributedCacheable` annotation to prevent cache stampede in your Spring Boot application.

## 🚀 Quick Start (5 Minutes)

### 1. Add the Dependencies

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.35.0</version>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### 2. Configure Redis

In `application.properties`:

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### 3. Setup Cache Configuration

Create a configuration class:

```java
@Configuration
@EnableCaching
@EnableAspectJAutoProxy
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager(RedissonClient redissonClient) {
        Map<String, CacheConfig> config = new HashMap<>();
        
        // Configure cache with TTL
        CacheConfig myCache = new CacheConfig(
            TimeUnit.MINUTES.toMillis(30),  // TTL: 30 minutes
            TimeUnit.MINUTES.toMillis(15)   // Max idle: 15 minutes
        );
        
        config.put("myCache", myCache);
        
        return new RedissonSpringCacheManager(redissonClient, config);
    }
}
```

### 4. Use the Annotation

On any service method:

```java
@Service
public class UserService {
    
    @DistributedCacheable(value = "users", key = "#userId")
    public User getUserById(String userId) {
        // This expensive operation will only run once for concurrent requests
        return database.findUser(userId);
    }
}
```

**That's it!** 🎉 You now have cache stampede prevention.

---

## 📖 Common Usage Patterns

### Pattern 1: Simple Caching with Single Parameter

```java
@Service
public class ProductService {
    
    @DistributedCacheable(value = "products", key = "#productId")
    public Product getProduct(String productId) {
        return externalApi.fetchProduct(productId);
    }
}
```

**What happens:**
- First request: Executes method, caches result
- Concurrent requests: Wait for first request, then get cached result
- Subsequent requests: Return from cache immediately

### Pattern 2: Multiple Parameters

```java
@Service
public class OrderService {
    
    @DistributedCacheable(
        value = "orders", 
        key = "#userId + '-' + #orderId"
    )
    public Order getOrder(String userId, String orderId) {
        return database.findOrder(userId, orderId);
    }
}
```

**Key format:** `"user123-order456"`

### Pattern 3: Using Object Properties

```java
@Service
public class ReportService {
    
    @DistributedCacheable(
        value = "reports", 
        key = "#request.userId + '-' + #request.reportType"
    )
    public Report generateReport(ReportRequest request) {
        return reportGenerator.generate(request);
    }
}
```

### Pattern 4: Custom KeyGenerator

**Step 1:** Create a KeyGenerator bean:

```java
@Component("myKeyGenerator")
public class MyKeyGenerator implements KeyGenerator {
    
    @Override
    public Object generate(Object target, Method method, Object... params) {
        // Custom logic
        return method.getName() + "_" + Arrays.toString(params);
    }
}
```

**Step 2:** Use it:

```java
@Service
public class DataService {
    
    @DistributedCacheable(
        value = "data", 
        keyGenerator = "myKeyGenerator"
    )
    public Data getAllData() {
        return database.fetchAllData();
    }
}
```

### Pattern 5: Configurable Timeout

```java
@Service
public class SlowService {
    
    @DistributedCacheable(
        value = "slowData",
        key = "#id",
        lockTimeout = 30000  // 30 seconds (default is 10s)
    )
    public SlowData getSlowData(String id) {
        // This might take 20 seconds
        return verySlowExternalApi.fetch(id);
    }
}
```

---

## 🔧 Configuration Options

### Annotation Attributes

| Attribute | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `value` | String | ✅ Yes | - | Cache name |
| `key` | String | Conditional | `""` | SpEL expression for cache key |
| `keyGenerator` | String | Conditional | `""` | Name of KeyGenerator bean |
| `lockTimeout` | long | No | `10000` | Lock timeout in milliseconds |

⚠️ **Important:** Must specify either `key` OR `keyGenerator`, but not both.

---

## ⏱️ Controlling Lock Wait Time

### What is Lock Timeout?

The `lockTimeout` controls **how long waiting threads will wait** for the first thread to complete and cache the result.

### How It Works

```java
@DistributedCacheable(
    value = "users",
    key = "#userId",
    lockTimeout = 15000  // 15 seconds (in milliseconds)
)
public User getUser(String userId) {
    // Your operation
}
```

**Timeline:**
```
T=0s:   Thread 1 (cache miss) → acquires lock → starts execution
T=0.1s: Thread 2 (cache miss) → tries lock → WAITS (up to 15s)
T=0.2s: Thread 3 (cache miss) → tries lock → WAITS (up to 15s)
T=5s:   Thread 1 → completes → caches result → releases lock
T=5.1s: Thread 2 → acquires lock → finds cached result → returns immediately
T=5.2s: Thread 3 → acquires lock → finds cached result → returns immediately
```

### Choosing the Right Timeout

**Golden Rule:** `lockTimeout` should be **longer than your worst-case method execution time**

```
lockTimeout = (worst-case execution time) + safety buffer

Example:
- Method typically takes: 8 seconds
- Worst case: 12 seconds  
- Set lockTimeout: 15-20 seconds (12s + 3-8s buffer)
```

### Examples by Operation Speed

#### Fast Operations (< 5 seconds)

```java
@DistributedCacheable(
    value = "quickData",
    key = "#id",
    lockTimeout = 8000  // 8 seconds
)
public Data getQuickData(String id) {
    // Fast API call: 2-3 seconds
    return fastApi.fetch(id);
}
```

#### Medium Operations (5-15 seconds)

```java
@DistributedCacheable(
    value = "mediumData",
    key = "#id",
    lockTimeout = 20000  // 20 seconds (default is 10s)
)
public Report generateReport(String id) {
    // Database aggregation: 8-12 seconds
    return database.aggregateReport(id);
}
```

#### Slow Operations (15-60 seconds)

```java
@DistributedCacheable(
    value = "slowData",
    key = "#datasetId",
    lockTimeout = 90000  // 90 seconds (1.5 minutes)
)
public AnalysisResult analyzeDataset(String datasetId) {
    // Heavy computation: 30-60 seconds
    return analyzer.performDeepAnalysis(datasetId);
}
```

#### Very Slow Operations (> 60 seconds)

```java
@DistributedCacheable(
    value = "verySlowData",
    key = "#jobId",
    lockTimeout = 300000  // 300 seconds (5 minutes)
)
public ProcessingResult processLargeJob(String jobId) {
    // Batch processing: 2-4 minutes
    return batchProcessor.process(jobId);
}
```

### What Happens When Timeout is Exceeded?

If a thread **cannot acquire the lock** within `lockTimeout`:

1. It **stops waiting**
2. It **executes the method itself** (fallback behavior)
3. A warning is logged

```java
WARN: Could not acquire lock for key: user:123 within 10000ms, executing method anyway
```

**Result:** Multiple executions occur (cache stampede happens for that request batch)

### ⚠️ Understanding the Fallback Behavior

**Important:** When a thread times out waiting for the lock, it **WILL execute the method** to get the value. This is intentional fallback behavior to prevent threads from hanging indefinitely.

#### Scenario 1: ✅ Timeout is Sufficient (Good)

```
Thread 1: Acquires lock → Executes method (8s) → Caches result → Releases lock
Thread 2: Waits 8s → Acquires lock → Finds cached value → Returns immediately ✓
Thread 3: Waits 8s → Acquires lock → Finds cached value → Returns immediately ✓

lockTimeout = 15000ms (15s) - Plenty of time!
Result: Only 1 execution ✅ Cache stampede prevented!
```

#### Scenario 2: ❌ Timeout is Too Short (Bad)

```
T=0s:   Thread 1: Acquires lock → Starts method execution (will take 10s)
T=0.1s: Threads 2-10: Try lock → Start waiting...
T=5s:   Threads 2-10: TIMEOUT after 5s! → Execute method themselves ❌
T=10s:  Thread 1: Completes → Caches result (but damage is done)

lockTimeout = 5000ms (5s) - TOO SHORT!
Result: 10 executions = Cache stampede NOT prevented! ❌
```

#### Why Does Fallback Exist?

The fallback prevents worse problems:

| Without Fallback | With Fallback (Current) |
|-----------------|-------------------------|
| Thread hangs forever if first thread crashes | Request completes (even if multiple executions) |
| Deadlock risk | Self-healing behavior |
| Poor user experience | Graceful degradation |

**Trade-offs:**
- ✅ **Timeout just right**: Only 1 execution (optimal)
- ⚠️ **Timeout too short**: Multiple executions (cache stampede)
- ⚠️ **Timeout too long**: Unnecessary waiting if something breaks

#### Real Example: What You'll See in Logs

**Good scenario (timeout is sufficient):**
```
INFO  [Thread-1] Acquiring lock for key: report:123
INFO  [Thread-1] Lock ACQUIRED - executing method
INFO  [Thread-2] Waiting for lock: report:123...
INFO  [Thread-3] Waiting for lock: report:123...
...method executes for 8 seconds...
INFO  [Thread-1] Cached result and released lock
INFO  [Thread-2] Lock acquired - cache hit! Returning cached value
INFO  [Thread-3] Lock acquired - cache hit! Returning cached value
```

**Bad scenario (timeout too short):**
```
INFO  [Thread-1] Acquiring lock for key: report:123
INFO  [Thread-1] Lock ACQUIRED - executing method
INFO  [Thread-2] Waiting for lock: report:123...
WARN  [Thread-2] Could not acquire lock within 5000ms, executing method anyway ⚠️
WARN  [Thread-3] Could not acquire lock within 5000ms, executing method anyway ⚠️
WARN  [Thread-4] Could not acquire lock within 5000ms, executing method anyway ⚠️
...multiple threads execute the same expensive operation...
```

### How to Verify Correct Timeout

**Step 1:** Measure your method execution time:

```java
@DistributedCacheable(value = "reports", key = "#id")
public Report generateReport(String id) {
    long start = System.currentTimeMillis();
    
    Report result = expensiveOperation(id);
    
    long duration = System.currentTimeMillis() - start;
    logger.info("Method took {}ms to execute", duration);
    
    return result;
}
```

**Step 2:** Use P95 or P99 execution time:

```
Typical: 5s
P95: 12s     ← Use this for lockTimeout calculation!
P99: 18s
Max: 25s
```

**Step 3:** Set timeout with buffer:

```java
lockTimeout = P95 + buffer
            = 12s + 5s buffer
            = 17s
            = 17000ms (round to 20000ms)

@DistributedCacheable(
    value = "reports",
    key = "#id",
    lockTimeout = 20000  // Safe for P95 of 12s
)
```

### Timeout Strategy Decision Tree

```
Is your operation < 3 seconds?
├─ YES → lockTimeout = 5000 (5s)
└─ NO → Is it < 10 seconds?
    ├─ YES → lockTimeout = 15000 (15s)
    └─ NO → Is it < 30 seconds?
        ├─ YES → lockTimeout = 45000 (45s)
        └─ NO → Is it < 60 seconds?
            ├─ YES → lockTimeout = 90000 (90s)
            └─ NO → Consider async processing instead of caching
```

### Real-World Example: Multiple Endpoints

```java
@Service
public class ProductService {

    // Quick metadata lookup
    @DistributedCacheable(
        value = "productMeta",
        key = "#productId",
        lockTimeout = 5000  // 5 seconds
    )
    public ProductMeta getMetadata(String productId) {
        return quickDb.getMetadata(productId);  // ~1-2s
    }

    // Full product details with images
    @DistributedCacheable(
        value = "productDetails",
        key = "#productId",
        lockTimeout = 15000  // 15 seconds
    )
    public Product getProduct(String productId) {
        return api.getFullProduct(productId);  // ~5-8s
    }

    // Product recommendations (ML model)
    @DistributedCacheable(
        value = "recommendations",
        key = "#userId + '-' + #productId",
        lockTimeout = 30000  // 30 seconds
    )
    public List<Product> getRecommendations(String userId, String productId) {
        return mlService.generateRecs(userId, productId);  // ~10-20s
    }

    // Full analytics report
    @DistributedCacheable(
        value = "analytics",
        key = "#productId",
        lockTimeout = 120000  // 2 minutes
    )
    public AnalyticsReport getAnalytics(String productId) {
        return analyticsEngine.generateReport(productId);  // ~45-90s
    }
}
```

### Monitoring Lock Timeouts

Enable debug logging to see lock behavior:

**application.properties:**
```properties
logging.level.com.example.demo.aspect.DistributedCacheableAspect=DEBUG
```

**Expected logs:**
```
DEBUG [Thread-1] Acquiring lock for key: user:123
DEBUG [Thread-1] Lock ACQUIRED for key: user:123
DEBUG [Thread-2] Waiting for lock: user:123...
DEBUG [Thread-3] Waiting for lock: user:123...
DEBUG [Thread-1] Executing method for key: user:123 (cache miss after lock)
DEBUG [Thread-1] Put result for key: user:123 into cache
DEBUG [Thread-1] Lock RELEASED for key: user:123
DEBUG [Thread-2] Lock ACQUIRED for key: user:123
DEBUG [Thread-2] Double-check cache hit for key: user:123
DEBUG [Thread-2] Lock RELEASED for key: user:123
```

### Quick Reference Table

| Operation Type | Typical Duration | Recommended Timeout | Example |
|---------------|------------------|---------------------|---------|
| Database query (indexed) | 1-2s | 5000ms | User lookup by ID |
| External API call | 2-5s | 10000ms | Weather API |
| Database aggregation | 5-15s | 20000ms | Sales report |
| ML inference | 10-30s | 45000ms | Recommendation engine |
| Complex computation | 30-120s | 180000ms | Data analytics |
| Batch processing | > 120s | Consider async | Large file processing |

### Tips for Optimal Timeout Configuration

1. **Monitor your method execution times** in production
   ```java
   long start = System.currentTimeMillis();
   Result result = expensiveOperation();
   long duration = System.currentTimeMillis() - start;
   logger.info("Operation took {}ms", duration);
   ```

2. **Use percentile, not average**
   - P50: 5 seconds
   - P95: 12 seconds ← Use this!
   - P99: 20 seconds

3. **Add safety buffer**
   ```
   lockTimeout = P95 execution time + (20-50% buffer)
   Example: 12s + 40% = 16.8s → use 20000ms
   ```

4. **Too short = cache stampede on timeout**
   ```java
   // ❌ BAD - Method takes 8s, timeout is 5s
   @DistributedCacheable(value = "data", key = "#id", lockTimeout = 5000)
   public Data slowMethod(String id) {
       Thread.sleep(8000);  // Threads will timeout and all execute!
   }
   ```

5. **Too long = unnecessary waiting**
   ```java
   // ⚠️ SUBOPTIMAL - Method takes 2s, timeout is 60s
   @DistributedCacheable(value = "data", key = "#id", lockTimeout = 60000)
   public Data fastMethod(String id) {
       Thread.sleep(2000);  // Works, but wastes time if something goes wrong
   }
   ```

---

### Cache TTL Configuration

Control how long items stay in cache:

```java
// Short-lived cache (5 minutes)
CacheConfig shortCache = new CacheConfig(
    TimeUnit.MINUTES.toMillis(5),   // TTL
    TimeUnit.MINUTES.toMillis(2)    // Max idle
);

// Long-lived cache (24 hours)
CacheConfig longCache = new CacheConfig(
    TimeUnit.HOURS.toMillis(24),    // TTL
    TimeUnit.HOURS.toMillis(6)      // Max idle
);
```

---

## 🧪 Testing Your Implementation

### Manual Test

**Terminal 1 - Start your app:**
```bash
mvn spring-boot:run
```

**Terminal 2 - Send concurrent requests:**
```bash
# Send 10 requests at the same time
for i in {1..10}; do
  curl http://localhost:8081/api/user/123 &
done
wait
```

**Expected Result:** Only ONE execution in your logs, but 10 successful responses.

### Verify It's Working

Look for these log patterns:

```
✅ GOOD - Single execution:
[Thread-1] Acquiring lock for key: user:123
[Thread-1] Executing method (cache miss)
[Thread-2] Cache hit for key: user:123
[Thread-3] Cache hit for key: user:123
...

❌ BAD - Cache stampede:
[Thread-1] Executing method (cache miss)
[Thread-2] Executing method (cache miss)  ← Should not happen!
[Thread-3] Executing method (cache miss)  ← Should not happen!
```

---

## 🎯 Real-World Examples

### Example 1: External API with Rate Limits

```java
@Service
public class WeatherService {
    
    @DistributedCacheable(
        value = "weather",
        key = "#city",
        lockTimeout = 15000
    )
    public Weather getWeather(String city) {
        // API has rate limit of 100 requests/hour
        // Without caching: 1000 concurrent requests = rate limit exceeded
        // With @DistributedCacheable: 1 API call, 999 cache hits ✅
        return weatherApi.fetch(city);
    }
}
```

### Example 2: Expensive Database Query

```java
@Service
public class AnalyticsService {
    
    @DistributedCacheable(
        value = "analytics",
        key = "#userId + '-' + #startDate + '-' + #endDate"
    )
    public AnalyticsReport generateReport(String userId, LocalDate startDate, LocalDate endDate) {
        // Complex aggregation query taking 10+ seconds
        return database.runComplexAggregation(userId, startDate, endDate);
    }
}
```

### Example 3: Microservice Communication

```java
@Service
public class CustomerService {
    
    @DistributedCacheable(
        value = "customers",
        key = "#customerId"
    )
    public Customer getCustomerDetails(String customerId) {
        // Calls 3 different microservices and aggregates data
        Profile profile = profileService.getProfile(customerId);
        Orders orders = orderService.getOrders(customerId);
        Preferences prefs = preferencesService.get(customerId);
        
        return new Customer(profile, orders, prefs);
    }
}
```

---

## 🐛 Troubleshooting

### Problem: Cache not working (method executes every time)

**Checklist:**
1. ✅ Is Redis running? `redis-cli ping`
2. ✅ Did you add `@EnableCaching` to a `@Configuration` class?
3. ✅ Did you add `@EnableAspectJAutoProxy` to a `@Configuration` class?
4. ✅ Is the method `public`? (AOP doesn't work on private methods)
5. ✅ Are you calling the method from outside the class? (Not `this.method()`)

### Problem: IllegalStateException - "Must specify either 'key' or 'keyGenerator'"

**Solution:** Add either `key` or `keyGenerator`:

```java
// Option 1: Use key
@DistributedCacheable(value = "cache", key = "#id")

// Option 2: Use keyGenerator
@DistributedCacheable(value = "cache", keyGenerator = "myGenerator")
```

### Problem: Lock timeout - method still executes multiple times

**Solution:** Increase `lockTimeout` to be longer than your method execution:

```java
// If method takes 15 seconds, set timeout to 20+ seconds
@DistributedCacheable(
    value = "cache",
    key = "#id",
    lockTimeout = 25000  // 25 seconds
)
```

### Problem: OutOfMemoryError in Redis

**Solution:** Set appropriate TTL values:

```java
// Don't cache forever - set reasonable TTL
CacheConfig config = new CacheConfig(
    TimeUnit.HOURS.toMillis(1),     // 1 hour TTL (not infinite!)
    TimeUnit.MINUTES.toMillis(30)   // 30 min idle
);
```

---

## 📊 Monitoring & Metrics

### Check Cache Hit Rate

```java
@Service
public class CacheMonitoringService {
    
    @Autowired
    private CacheManager cacheManager;
    
    public void logCacheStats() {
        Cache cache = cacheManager.getCache("myCache");
        // Log cache statistics
        logger.info("Cache stats: {}", cache);
    }
}
```

### Monitor Redis

```bash
# Connect to Redis CLI
redis-cli

# Check all cached keys
KEYS *

# Check specific cache
KEYS books:*

# Monitor commands in real-time
MONITOR

# Get cache statistics
INFO stats
```

---

## 🔒 Security Considerations

### 1. Sensitive Data

Don't cache sensitive data without encryption:

```java
// ❌ BAD - Caching sensitive data in plain text
@DistributedCacheable(value = "passwords", key = "#userId")
public String getPassword(String userId) { ... }

// ✅ GOOD - Don't cache passwords at all
public String getPassword(String userId) {
    return database.getPassword(userId);
}
```

### 2. Cache Poisoning

Validate input before using in cache keys:

```java
@DistributedCacheable(value = "users", key = "#userId")
public User getUser(String userId) {
    // Validate userId format
    if (!userId.matches("^[a-zA-Z0-9-]+$")) {
        throw new IllegalArgumentException("Invalid user ID");
    }
    return database.findUser(userId);
}
```

---

## 🚀 Performance Tips

### 1. Choose Appropriate Cache Names

```java
// Organize by data type and lifetime
@DistributedCacheable(value = "users:short", key = "#id")  // 5 min TTL
@DistributedCacheable(value = "users:long", key = "#id")   // 1 hour TTL
@DistributedCacheable(value = "reference", key = "#code")  // 24 hour TTL
```

### 2. Use Specific Cache Keys

```java
// ❌ BAD - Too generic
@DistributedCacheable(value = "cache", key = "#param")

// ✅ GOOD - Specific and descriptive
@DistributedCacheable(value = "userProfiles", key = "'profile:' + #userId")
```

### 3. Set Appropriate Timeouts

```java
// Fast operations: Short timeout
@DistributedCacheable(value = "fast", key = "#id", lockTimeout = 5000)

// Slow operations: Longer timeout
@DistributedCacheable(value = "slow", key = "#id", lockTimeout = 30000)
```

---

## 📚 Additional Resources

- **Main README**: [README.md](README.md) - Complete project documentation
- **Integration Tests**: [CacheStampedeIntegrationTest.java](src/test/java/com/example/demo/CacheStampedeIntegrationTest.java)
- **Example Service**: [BookService.java](src/main/java/com/example/demo/service/BookService.java)

---

## 💡 Need Help?

1. Check the [Troubleshooting](#troubleshooting) section
2. Review the [Real-World Examples](#real-world-examples)
3. Look at the test cases for working examples
4. Verify your Redis connection: `redis-cli ping`

**Happy Caching! 🎉**

