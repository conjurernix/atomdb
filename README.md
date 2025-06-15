# AtomDB

**AtomDB** is an in-process, content-addressable, immutable, pluggable database designed with Clojure's functional data
principles in mind. It provides a familiar Clojure atom-like interface while offering persistent storage, efficient
structural sharing, and flexible caching options.

## Features

- Immutable value storage (like Clojure atoms)
- Content-addressable by SHA-256 hash
- Merkle-tree–like structure with structural sharing
- Pluggable chunk stores (in-memory, filesystem)
- Flexible cache options (LRU, TTL, no-op)
- Supports core Clojure data types, plus UUID, Instant, BigDecimal, etc.

## Installation

Add the following dependency to your project.clj or deps.edn:

```clojure
;; deps.edn
{:deps {atomdb/atomdb {:mvn/version "0.1.0"}}}

;; project.clj
[atomdb/atomdb "0.1.0"]
```

## Supported Data Types

- Maps, vectors, sets, lists
- Keywords, symbols, strings
- Booleans, numbers, ratios, bigdecimals
- UUIDs, Instants, nil, and empty collections

## Quick Start

```clojure
(require '[atomdb.core :as atom]
         '[atomdb.store.memory :as memory]
         '[atomdb.store.file :as file]
         '[atomdb.cache.lru :as lru]
         '[atomdb.serde.edn :as serde.edn])

;; Create an in-memory database
(def db (atom/db))

;; Initialize with data
(reset! db {:users {1 {:name "Alice" :email "alice@example.com"}}})

;; Add more data
(swap! db assoc-in [:users 2] {:name "Bob" :email "bob@example.com"})

;; Read data
(get-in @db [:users 1 :name]) ;; => "Alice"

;; Create a file-backed database
(def persistent-db (atom/db {:store (file/file-store "/path/to/data" (serde.edn/edn-serde))
                             :cache (lru/lru-cache 1000)}))
```

## Usage Guide

### Creating a Database

```clojure
(require '[atomdb.core :as atom]
         '[atomdb.store.memory :as memory]
         '[atomdb.store.file :as file]
         '[atomdb.cache.lru :as lru]
         '[atomdb.cache.ttl :as ttl]
         '[atomdb.cache.noop :as noop]
         '[atomdb.serde.edn :as serde.edn])

;; Create with default options (in-memory store, LRU cache)
(def db (atom/db))

;; Create with initial data
(def mock-users
  {1 {:id 1 :name "Alice" :email "alice@example.com" :age 28}
   2 {:id 2 :name "Bob" :email "bob@example.com" :age 30}
   3 {:id 3 :name "Charlie" :email "charlie@example.com" :age 44}})

(def db (atom/db {:init {:users mock-users}}))

;; Create with file store
(def db (atom/db {:store (file/file-store "data/mydb" (serde.edn/edn-serde))
                  :init  {:users mock-users}}))

;; Create with TTL cache
(def db (atom/db {:cache (ttl/ttl-cache 60000)  ;; 1 minute
                  :init  {:users mock-users}}))

;; Create with custom store and cache
(def db (atom/db {:store (file/file-store "data/mydb" (serde.edn/edn-serde))
                  :cache (lru/lru-cache 10000)
                  :init  {:users mock-users}}))
```

### Retrieving Data

```clojure
;; Get the entire database (lazy-loaded)
@db

;; Get a specific value
(get @db :users)

;; Get a nested value
(get-in @db [:users 1 :name])

```

Keep in mind, dereferencing `db` will not load the whole database from disk. It returns a lazy map where values are
loaded from disk only when requested and potentially cached based on your cache configuration.

### Storing Data

Since `db` behaves like a Clojure `atom`, you can use familiar atom functions:

```clojure
;; Add a new user
(def new-user {:id 4 :name "Diana" :email "diana@example.com" :age 33})
(swap! db assoc-in [:users (:id new-user)] new-user)

;; Update an existing user
(swap! db update-in [:users 1 :age] inc)

```

AtomDB stores data efficiently:

- Only changed parts of the data structure are persisted
- Structural sharing is used to minimize storage requirements
- Content-addressing ensures data integrity

### Complex Operations

You can perform complex operations in a single transaction:

```clojure
(swap! db
       (fn [{:keys [users counters] :as data}]
         (-> data
             (assoc-in [:users 4] new-user)
             (update-in [:counters :total-users] inc)
             (update-in [:counters :last-updated] (constantly (java.util.Date.))))))
```

## Configuration Options

When creating a database with `atom/db`, you can provide the following options:

| Option   | Description                                               | Default                        |
|----------|-----------------------------------------------------------|--------------------------------|
| `:store` | Store implementation (must implement ChunkStore protocol) | `memory/->MemoryChunkStore`    |
| `:cache` | Cache implementation (must implement Cache protocol)      | `lru/lru-cache` with size 1000 |
| `:init`  | Initial data to populate the database                     | `nil`                          |

## API Reference

### Core Functions

- `atom/db [options]` - Creates a new AtomDB instance with the given options

### Store Implementations

- `memory/->MemoryChunkStore [store-atom]` - In-memory store implementation
- `file/file-store [dir serde]` - File-based store implementation

### Cache Implementations

- `lru/lru-cache [capacity]` - Least Recently Used cache implementation
- `ttl/ttl-cache [ttl-ms]` - Time-To-Live cache implementation
- `noop/no-op-cache []` - No-operation cache implementation

### Serialization Implementations

- `serde.edn/edn-serde []` - EDN serialization implementation
- `serde.fressian/fressian-serde []` - Fressian serialization implementation

## Performance Considerations

- Use appropriate cache settings for your workload
- For large databases, consider using the file store with an LRU cache
- Batch related operations in a single `swap!` call when possible

## Testing

AtomDB uses [Kaocha](https://github.com/lambdaisland/kaocha) for testing. Kaocha is a comprehensive test runner for Clojure that provides a rich set of features.

### Running Tests

To run all tests:

```bash
clojure -M:dev:test:kaocha
```

### Watch Mode

To run tests in watch mode (automatically re-run tests when files change):

```bash
clojure -M:dev:test:kaocha-watch
```

### Kaocha Configuration

The Kaocha configuration is defined in `tests.edn`. You can modify this file to customize how tests are run, including:

- Test selectors
- Reporters
- Plugins
- Randomization
- Profiling

For more information about Kaocha configuration options, see the [Kaocha documentation](https://cljdoc.org/d/lambdaisland/kaocha/CURRENT).

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

Copyright © 2023

Distributed under the Eclipse Public License, the same as Clojure.
