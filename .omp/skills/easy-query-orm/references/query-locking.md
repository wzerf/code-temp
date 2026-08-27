# Row-Level Query Locking

Use this reference when a read must lock the selected rows until the enclosing
transaction ends. The 3.2.14 API is `forUpdate()` on both the weakly typed
`com.easy.query.core.basic.api.select.ClientQueryable` and the proxy
`com.easy.query.api.proxy.entity.select.EntityQueryable`.

## Basic pattern

Call `forUpdate()` on the root query inside an active transaction, then finish
the query normally:

```java
import com.easy.query.core.basic.jdbc.tx.Transaction;

try (Transaction tx = easyEntityQuery.beginTransaction()) {
    Account account = easyEntityQuery.queryable(Account.class)
            .where(a -> a.id().eq(accountId))
            .forUpdate()
            .singleOrNull();

    if (account == null) {
        tx.rollback();
        return;
    }
    // Validate and perform any writes against the locked row here.
    tx.commit();
}
```

On the core client, the same method is available directly on
`ClientQueryable<T>`:

```java
import com.easy.query.core.basic.api.select.ClientQueryable;
import com.easy.query.core.basic.jdbc.tx.Transaction;

try (Transaction tx = easyQueryClient.beginTransaction()) {
    ClientQueryable<Account> locked = easyQueryClient.queryable(Account.class)
            .where(a -> a.eq("id", accountId))
            .forUpdate();
    locked.singleOrNull();
    tx.commit();
}
```

`forUpdate()` is fluent and appends `FOR UPDATE` once to the outer SQL. It can
be combined with `where`, `orderBy`, `limit`, projection, and terminals such as
`firstOrNull()` or `toList()`.

## Required boundaries

- An active transaction recognized by easy-query is required. Calling
  `forUpdate()` outside a transaction throws `IllegalStateException`; SQL
  rendering also checks the transaction again.
- The current 3.2.14 implementation accepts only a single-table root query.
  A join that adds another table to the root expression is rejected by
  `forUpdate()`.
- Repeating `forUpdate()` on the same chain is rejected. Apply it once to the
  root query.
- A subquery used by `exists` or `in` remains a subquery; the lock suffix is
  added only when the root expression is rendered. This does not lock rows from
  the nested table.
- A row lock is held according to the database transaction isolation/locking
  rules. Commit or rollback promptly; do not keep remote calls inside the lock
  transaction.

## Dialect support

The 3.2.14 source has `toSQL()` smoke coverage that renders a lock suffix for
MySQL, PostgreSQL, H2, SQLite, KingbaseES, GaussDB, Dameng, Oracle, and DB2.
That verifies ORM SQL shape, not execution against every database. In
particular, SQLite does not support `SELECT ... FOR UPDATE`, even though the
current dialect smoke test renders it; do not treat that test as runtime
support. ClickHouse, DuckDB, and Microsoft SQL Server fail earlier with
`UnsupportedOperationException`. Verify the target database and version before
exposing a portable repository method; SQL Server needs a dialect-specific
table hint rather than this API.

## Do not confuse with optimistic locking

`forUpdate()` is a pessimistic database row lock for a read transaction. It is
independent of `@Version`/`withVersion(...)`, which detect stale writes through
an optimistic version predicate. Use one or both deliberately based on the
concurrency protocol.

## Source verification (3.2.14)

- `ClientQueryable.forUpdate()`:
  `sql-core/src/main/java/com/easy/query/core/basic/api/select/ClientQueryable.java`
- transaction/single-table/repeated-call checks:
  `sql-core/src/main/java/com/easy/query/core/basic/api/select/abstraction/AbstractClientQueryable.java`
- outer SQL suffix and transaction re-check:
  `sql-core/src/main/java/com/easy/query/core/expression/sql/expression/impl/QuerySQLExpressionImpl.java`
- proxy declaration:
  `sql-platform/sql-api-proxy/src/main/java/com/easy/query/api/proxy/entity/select/EntityQueryable.java`
- proxy forwarding implementation:
  `sql-platform/sql-api-proxy/src/main/java/com/easy/query/api/proxy/entity/select/abstraction/AbstractEntityQueryable.java`
- dialect SQL-rendering smoke matrix:
  `sql-test/src/main/java/com/easy/query/test/ForUpdateDialectToSQLSmokeTest.java`
