# Distributed Cache Stampede Prevention with Spring Boot

A Spring Boot application demonstrating how to prevent cache stampede (thundering herd problem) in distributed systems using Redis, Redisson, and custom AOP-based caching annotations.

## Table of Contents

- [Overview](#overview)
- [The Problem: Cache Stampede](#the-problem-cache-stampede)
- [The Solution](#the-solution)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Usage](#usage)
- [How It Works](#how-it-works)
- [Configuration](#configuration)
- [Testing](#testing)
- [Project Structure](#project-structure)

## Overview

This project provides a production-ready solution for preventing cache stampede in distributed Spring Boot applications. When multiple threads or service instances simultaneously request the same uncached data, only one will execute the expensive operation while others wait for the cached result.

## The Problem: Cache Stampede

**Cache Stampede** (also known as the Thundering Herd problem) occurs when:

1. Multiple requests arrive simultaneously for the same resource
2. The cache is empty or expired
3. All requests bypass the cache and hit the backend (database, external API, etc.)
4. The backend gets overwhelmed with duplicate requests

**Example:**
```
10 concurrent requests for the same book ISBN
↓
Cache is empty
↓
All 10 requests call the external API simultaneously
↓
API gets hit 10 times for the same data (inefficient and expensive!)
```

## The Solution

This project implements a **custom `@DistributedCacheable` annotation** that uses:

- **Double-Check Locking Pattern**: Checks cache before and after acquiring lock
- **Distributed Locks (Redisson)**: Ensures only one thread across all instances executes
- **Automatic Cache Management**: Transparent caching with TTL support
- **Spring AOP**: Clean, declarative syntax similar to `@Cacheable`

**Result:**
```
10 concurrent requests for the same book ISBN
↓
Cache is empty
↓
Thread 1 acquires distributed lock and calls API
Threads 2-10 wait for lock
↓
Thread 1 caches result and releases lock
Threads 2-10 retrieve from cache
↓
API gets hit only ONCE! ✅
```

## Features

✅ **Distributed Lock-Based Caching**: Prevents cache stampede across multiple service instances
✅ **Flexible Key Generation**: Supports both SpEL expressions and custom `KeyGenerator` beans
✅ **Conditional Caching**: Control when to cache using `condition` and `unless` expressions
✅ **Complex Object Support**: SpEL expressions work with nested properties, method calls, and domain objects
✅ **Spring-Compatible API**: Familiar syntax for developers using Spring's `@Cacheable`
✅ **Configurable Timeouts**: Lock wait times and cache TTL are fully configurable
✅ **Comprehensive Testing**: Full integration test suite with Testcontainers
✅ **Production-Ready**: Handles edge cases, timeouts, and error scenarios

## Prerequisites

- **Java 21** or higher
- **Maven 3.6+**
- **Redis 7.x** (or Docker for running Redis locally)
- **Docker** (optional, for Testcontainers in tests)

## Getting Started

### 1. Clone the Repository

```bash
git clone <repository-url>
cd distributedCache
```

### 2. Start Redis

**Using Docker:**
```bash
docker run -d --name redis -p 6379:6379 redis:7.2-alpine
```

**Or install Redis locally:**
```bash
# macOS
brew install redis
redis-server

# Ubuntu/Debian
sudo apt-get install redis-server
sudo systemctl start redis
```

### 3. Build the Project

```bash
mvn clean install
```

### 4. Run the Application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8081`

## Usage

### Basic Usage with SpEL Expression

```java
@Service
public class BookService {
    
    @DistributedCacheable(value = "books", key = "#isbn")
    public String getBookByIsbn(String isbn) {
        // Expensive operation (e.g., external API call)
        return callExternalApi(isbn);
    }
}
```

### Using Custom KeyGenerator

**1. Create a custom KeyGenerator:**

```java
@Component("customKeyGenerator")
public class CustomKeyGenerator implements KeyGenerator {
    
    @Override
    public Object generate(Object target, Method method, Object... params) {
        return target.getClass().getSimpleName() + "." + method.getName() 
               + "(" + Arrays.toString(params) + ")";
    }
}
```

**2. Use it in your service:**

```java
@Service
public class BookService {
    
    @DistributedCacheable(value = "books", keyGenerator = "customKeyGenerator")
    public String getAllBooks() {
        // Expensive operation
        return fetchAllBooksFromDatabase();
    }
}
```

### Using Conditional Caching

**1. Don't cache null results:**

```java
@Service
public class ProductService {
    
    @DistributedCacheable(
        value = "products",
        key = "#productId",
        unless = "#result == null"  // Don't cache null results
    )
    public Product getProduct(String productId) {
        return database.findProduct(productId); // May return null
    }
}
```

**2. Only cache for valid parameters:**

```java
@Service
public class UserService {
    
    @DistributedCacheable(
        value = "users",
        key = "#userId",
        condition = "#userId != null && #userId.length() > 0"
    )
    public User getUser(String userId) {
        return database.findUser(userId);
    }
}
```

**3. Combine condition and unless:**

```java
@Service
public class DataService {

    @DistributedCacheable(
        value = "data",
        key = "#id",
        condition = "#id != null",                      // Validate input
        unless = "#result == null || #result.isEmpty()" // Don't cache empty results
    )
    public String getData(String id) {
        return externalApi.fetch(id);
    }
}
```

### Advanced: Complex Objects

The `condition` and `unless` parameters support complex object properties and method calls:

```java
@DistributedCacheable(
    value = "books",
    key = "#book.isbn",
    condition = "#book.isExpensive()",              // Method call
    unless = "#result == null || #result.isEmpty()" // Complex validation
)
public Book processBook(Book book) {
    return expensiveOperation(book);
}
```

For detailed examples and patterns, see the [README.md usage guide](README.md#advanced-complex-objects-in-conditional-expressions).

### Annotation Attributes

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `value` | String | ✅ Yes | Cache name |
| `key` | String | Conditional* | SpEL expression for cache key |
| `keyGenerator` | String | Conditional* | Bean name of custom KeyGenerator |
| `lockTimeout` | long | No | Lock wait timeout in milliseconds (default: 10000) |
| `condition` | String | No | SpEL expression - cache only if true (evaluated before execution) |
| `unless` | String | No | SpEL expression - don't cache if true (evaluated after execution) |

\* *Must specify either `key` or `keyGenerator`, but not both*

### REST API Endpoints

Test the functionality using the provided REST endpoints:

```bash
# Single request
curl http://localhost:8081/api/book/978-0134685991

# Test concurrent access (10 simultaneous requests)
curl http://localhost:8081/api/test-concurrent/978-0134685991
```

## How It Works

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│  @DistributedCacheable Annotation                       │
│  (Applied to service methods)                           │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│  DistributedCacheableAspect (AOP Interceptor)           │
│  • Intercepts annotated method calls                    │
│  • Implements double-check locking                      │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│  Redis + Redisson                                        │
│  • Distributed cache storage                            │
│  • Distributed lock coordination                        │
└─────────────────────────────────────────────────────────┘
```

### Flow Diagram

```
Request arrives
    ↓
Check cache (first check)
    ↓
Cache hit? → YES → Return cached value ✅
    ↓ NO
Try to acquire distributed lock
    ↓
Lock acquired?
    ↓ YES
Check cache again (double-check)
    ↓
Still empty? → NO → Return cached value ✅
    ↓ YES
Execute method
    ↓
Cache result
    ↓
Release lock
    ↓
Return result ✅
```

### Key Components

**1. Custom Annotation (`@DistributedCacheable`)**
- Marks methods for distributed caching
- Supports SpEL expressions and custom KeyGenerator

**2. AspectJ Aspect (`DistributedCacheableAspect`)**
- Intercepts annotated method calls
- Implements double-check locking pattern
- Manages distributed locks via Redisson

**3. Cache Key Resolver (`CacheKeyResolver`)**
- Evaluates SpEL expressions
- Resolves dynamic cache keys from method parameters

**4. Redisson Client**
- Provides distributed lock (`RLock`)
- Manages Redis connections
- Handles lock leasing and renewal

## Configuration

### Application Properties

```properties
# Server Configuration
server.port=8081

# Redis Configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=60000

# Logging
logging.level.com.example.demo=INFO
```

### Cache Configuration

Customize cache behavior in `CacheConfig.java`:

```java
@Bean
public CacheManager cacheManager(RedissonClient redissonClient) {
    Map<String, CacheConfig> config = new HashMap<>();
    
    CacheConfig booksConfig = new CacheConfig(
        TimeUnit.MINUTES.toMillis(30),  // TTL: 30 minutes
        TimeUnit.MINUTES.toMillis(15)   // Max idle: 15 minutes
    );
    
    config.put("books", booksConfig);
    
    return new RedissonSpringCacheManager(redissonClient, config);
}
```

## Testing

### Run All Tests

```bash
mvn test
```

### Test Coverage

The project includes comprehensive integration tests:

✅ **Cache stampede prevention** - Verifies only one execution with concurrent requests (`CacheStampedeIntegrationTest`)
✅ **Conditional caching with complex objects** - Tests nested properties, method calls, and complex result validation (`ConditionalCachingTest`)
✅ **Different cache keys** - Ensures independent key execution
✅ **Cache hits** - Validates cached values are reused
✅ **KeyGenerator support** - Tests custom key generation
✅ **Validation** - Ensures proper annotation usage  

### Integration Tests with Testcontainers

Tests use **Testcontainers** to spin up a real Redis instance:

```java
@SpringBootTest
@Import(TestRedisConfiguration.class)
class CacheStampedeIntegrationTest {

    @Test
    void testCacheStampedePrevention_SameKey_OnlyOneExecution() {
        // 10 concurrent threads request same ISBN
        // Only 1 execution should occur
        // All threads receive same cached result
    }
}
```

### Test Organization

The project includes two comprehensive integration test suites:

**CacheStampedeIntegrationTest** (18 tests)
- Focus: Concurrent access and distributed lock behavior
- Tests cache stampede prevention across multiple threads
- Validates distributed locking with primitive types

**ConditionalCachingTest** (24 tests)
- Focus: SpEL conditional expressions with complex objects
- Tests organized in 4 categories:
  - Complex object properties (7 tests) - Price conditions, method calls, nested properties
  - Complex request objects (4 tests) - Request validation, multiple property checks
  - Complex result objects (5 tests) - Result validation with unless
  - Combined condition and unless (8 tests) - Both parameters working together
- Validates nested property access (`#book.author.country`)
- Tests method calls in conditions (`#book.isExpensive()`)
- Validates complex result filtering (`#result.isEmpty()`, `#result.hasErrors()`)

### Manual Testing

**1. Start the application**
```bash
mvn spring-boot:run
```

**2. Monitor logs in real-time**
```bash
tail -f logs/spring.log
```

**3. Trigger concurrent requests**
```bash
curl http://localhost:8081/api/test-concurrent/test-isbn-123
```

**4. Observe the logs**
You should see only ONE "FETCHING book" message despite multiple concurrent requests.

## Project Structure

```
distributedCache/
├── src/
│   ├── main/
│   │   ├── java/com/example/demo/
│   │   │   ├── annotation/
│   │   │   │   └── DistributedCacheable.java      # Custom annotation
│   │   │   ├── aspect/
│   │   │   │   └── DistributedCacheableAspect.java # AOP interceptor
│   │   │   ├── config/
│   │   │   │   ├── CacheConfig.java               # Cache configuration
│   │   │   │   └── CustomKeyGenerator.java        # Example KeyGenerator
│   │   │   ├── controller/
│   │   │   │   └── BookController.java            # REST endpoints
│   │   │   ├── service/
│   │   │   │   └── BookService.java               # Business logic
│   │   │   ├── util/
│   │   │   │   └── CacheKeyResolver.java          # SpEL resolver
│   │   │   └── DemoApplication.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/example/demo/
│           ├── config/
│           │   └── TestRedisConfiguration.java    # Test setup
│           └── CacheStampedeIntegrationTest.java  # Integration tests
├── pom.xml
└── README.md
```

## Key Dependencies

```xml
<!-- Distributed caching and locking -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.35.0</version>
</dependency>

<!-- AOP support -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>

<!-- Testing -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

## Performance Characteristics

### Without Cache Stampede Prevention

- **10 concurrent requests** → 10 API calls → ~50 seconds total (5s each)
- High backend load
- Wasted resources
- Potential rate limit issues

### With @DistributedCacheable

- **10 concurrent requests** → 1 API call → ~5 seconds total
- **90% reduction** in backend calls
- Consistent sub-100ms response for cache hits
- Scalable across multiple instances

## Best Practices

1. **Choose appropriate cache keys** - Use unique identifiers that represent the cached data
2. **Set reasonable TTLs** - Balance freshness vs. performance
3. **Monitor lock timeouts** - Ensure they're longer than worst-case execution time
4. **Use custom KeyGenerators** - For complex key logic or consistency requirements
5. **Test concurrent scenarios** - Validate behavior under load
6. **Monitor Redis** - Track cache hit rates and memory usage

## Troubleshooting

### Issue: All requests still hitting the backend

**Solution:** Verify Redis is running and accessible:
```bash
redis-cli ping  # Should return "PONG"
```

### Issue: Lock timeout exceptions

**Solution:** Increase `lockTimeout` in annotation:
```java
@DistributedCacheable(value = "books", key = "#isbn", lockTimeout = 30000)
```

### Issue: Tests failing with container errors

**Solution:** Ensure Docker is running:
```bash
docker ps  # Should show running containers
```

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass (`mvn test`)
5. Submit a pull request

## License

This project is licensed under the MIT License.

## Acknowledgments

- Spring Boot team for the excellent framework
- Redisson team for robust distributed primitives
- Testcontainers for making integration testing easier

---

**Built with ❤️ using Spring Boot, Redis, and Redisson**
