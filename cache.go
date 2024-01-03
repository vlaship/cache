package cache

import (
	"sync"
	"sync/atomic"
	"time"
	"unsafe"
)

// We have a key-value database storing user IPv4 addresses.
// This database is located quite far from the users, resulting in an additional latency of around a hundred milliseconds.
// We aim to minimize this latency and have started considering caching.
//
// We need to write a cache for the key-value database, taking into account the following:
// - the code should be as efficient and clear as possible without any bugs.
// - user code should remain unaware of the caching process.

type KVDatabase interface {
	Get(key string) (string, error)        // get single value by key (users use it very often)
	Keys() ([]string, error)               // get all keys (users use it very seldom)
	MGet(keys []string) ([]*string, error) // get values by keys (users use it very seldom)
}

type Cache struct {
	KVDatabase
	data map[string]string
	mu   sync.RWMutex
	ttl  time.Duration
}

func NewCache(db KVDatabase) *Cache {
	cache := Cache{
		KVDatabase: db,
		data:       make(map[string]string),
		ttl:        time.Second * 60,
	}

	cache.evict()

	return &cache
}

func (c *Cache) evict() {
	t := time.NewTicker(time.Second * 5)
	go func() {
		for range t.C {
			newData := make(map[string]string)
			d := unsafe.Pointer(&c.data)
			atomic.SwapPointer(&d, unsafe.Pointer(&newData))
		}
	}()
}

func (c *Cache) Get(key string) (string, error) {
	c.mu.RLock()
	val, ok := c.data[key]
	c.mu.RUnlock()

	if ok {
		return val, nil
	}

	val, err := c.KVDatabase.Get(key)
	if err != nil {
		return "", err
	}

	c.mu.Lock()
	c.data[key] = val
	c.mu.Unlock()

	return val, nil
}
