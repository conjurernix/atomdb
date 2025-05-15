# AtomDB

**AtomDB** is a content-addressable, immutable, pluggable database designed with Clojure’s functional data principles in mind.

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

## Getting Started

1. Create a store (memory or file-based)
2. Optionally wrap it with a cache
3. Use `persist` to store values and get a content hash
4. Use `get-chunk` and `load` to restore the value

## Testing

Run tests with:

```
(clojure.test/run-tests 'atom-db.core)
```

Covers roundtrip serialization, edge cases, store correctness, and cache behaviors.

## Roadmap

- Structural diff and merge
- Version tracking (root log)
- Sync and CRDT support
- Reactive API wrapper
- Streaming/transducer interfaces

## License

MIT

## Author

Built by [Your Name]. Contributions welcome!
