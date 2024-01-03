import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// We have a key-value database storing user IPv4 addresses.
// This database is located quite far from the users, resulting in an additional latency of around a hundred milliseconds.
// We aim to minimize this latency and have started considering caching.
//
// We need to write a cache for the key-value database, taking into account the following:
// - the code should be as efficient and clear as possible without any bugs.
// - user code should remain unaware of the caching process.

interface KVDatabase {
    String get(String key); // get single value by key (users use it very often)
    Collection<String> keys(); // get all keys (users use it very seldom)
    Collection<String> mGet(Collection<String> keys); // get values by keys (users use it very seldom)
}

public class Cache implements KVDatabase {

    private final KVDatabase db;
    private final Map<String, String> data = new HashMap<>(); // ConcurrentHashMap and we don't need to use lock
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public Cache(KVDatabase database, Duration ttl) {
        this.db = database;
        executor.scheduleAtFixedRate(this::evict, ttl.toMillis(), ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public String get(String key) {
        lock.readLock().lock();
        try {
            String value = data.get(key);
            if (value != null) {
                return value;
            }
        } finally {
            lock.readLock().unlock();
        }

        // retrieve from database
        String value = db.get(key);
        lock.writeLock().lock();
        try {
            data.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
        return value;
    }

    @Override
    public Collection<String> keys() {
        return db.keys();
    }

    @Override
    public Collection<String> mGet(Collection<String> keys) {
        return db.mGet(keys);
    }

    private void evict() {
        lock.writeLock().lock();
        try {
            data.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

}
