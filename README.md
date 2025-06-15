# AtomDB

**AtomDB** is an in-process, content-addressable, immutable, pluggable database designed with Clojure’s functional data principles in mind.

## Features

- Immutable value storage (like Clojure atoms)
- Content-addressable by SHA-256 hash
- Merkle-tree–like structure with structural sharing
- Pluggable chunk stores (in-memory, filesystem)
- Flexible cache options (LRU, TTL, no-op)
- Supports core Clojure data types, plus UUID, Instant, BigDecimal, etc.

## Supported Data Types

- Maps, vectors, sets, lists
- Keywords, symbols, strings
- Booleans, numbers, ratios, bigdecimals
- UUIDs, Instants, nil, and empty collections

## Using

### Retrieving Data

Let's assume the following initialization of the `db`
```clojure
  
(def mock-users
  {1 {:id 1 :name "Alice" :email "alice@example.com" :age 28}
   2 {:id 2 :name "Bob" :email "bob@example.com" :age 30}
   3 {:id 3 :name "Charlie" :email "charlie@example.com" :age 44}})

(def db (atom/db {:init {:users mock-users}}))
```

We can now read get the map of users like so
```clojure
(get @db :users) 
```
Keep in mind, dereferencing `db` will not load the whole database from disk. In fact this will return a lazy map where no values are realized until they are requested, which will be subsequently be loaded from disk and _potentially_ cached.

Similarly, we can access a specific user like so:
```clojure
(def user-id 1)
(get-in @db [:users user-id])
```
or using any other clojure core function that works on clojure core datastructures!

If cached, re-retrieving the same `user-id` will not have to do disk IO.

### Storing Data
Since `db` behaves like an `atom`, we can store data by using the familiar `atom` functions 
```clojure
(def new-user {:id 4 :name "Diana" :email "diana@example.com" :age 33})

(swap! db assoc-in [:users (:id new-user)] new-user)
```

In the above example, the new user will be stored efficiently. 
NOTE: this does not rewrite the whole db to disk.

We can update an existing user in the same manner
```clojure
(swap! db update-in [:users 1 :age] inc)
```
Again, here only the necessary data is loaded from disk, and the diff is stored efficiently.
In fact, we could write this as follows without any IO impact:
```clojure
(swap! db
       (fn [{:keys [users] :as db}]
         (assoc db :users (update-in users [1 :age] inc)))
```