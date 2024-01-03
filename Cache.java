import java.util.Collection;

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

