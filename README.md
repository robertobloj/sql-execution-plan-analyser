# SQL Execution Plan Analyser

Project is written due to check, how we can scan `spring-boot` project to find 
all JPA queries, next to translate these queries into native sql queries.
Having that, we want to prepare `postgres` database, fulfill it with data and 
finally, we want to find invalid queries, where we have `FULL SCAN` or 
`high cost` for query. We want to write simple test to protect application 
against negligence of developers.

## Prerequisites

You must have: 

1. [Docker] installed on your computer
2. [JDK] installed on your computer

## Setup

You can run command:

```
gradlew clean build
```

## Details

The idea is as follows:

1. You have [Spring Boot Application]
2. You use [Spring Repositories]
3. We want to scan all repositories
4. We want to find all methods with `@Query` annotation
5. We retrieve JPA queries from `@Query` annotations
6. We translate JPA queries into native sql queries
7. We create [PostgreSQL] container and fill it with simple data
8. Finally, we want to check all native queries whether these queries are good or bad (we must check execution plan for each of them)
9. Test (single unit test) will fail if execution plan contains `FULL SCAN` or `TOTAL COST` is higher than 10000.

We've created simple entities, such as: [Person], [Address] and [ActivityLog].
Every [Person] has many `Addresses` and / or many `Activity Logs`.

We've created appropriate repositories for entities, and ensure we have some 
indexes for tables.  

## Tests

Following tests are created:

1. [PersonIntegrationTest] - check that all entities are correctly written, and we are able to successfully save entities into [PostgreSQL] database 
2. [SqlCaptureInspectorTest] - test for custom `org.hibernate.resource.jdbc.spi.StatementInspector` implementation. We check whether we are able to collect all sql queries or not.
3. [SqlExecutionPlanTest] - test whether good query has a good plan and cost and vice versa, bad query has a bad plan. 
4. [JpaToSqlConversionTest] - test for checking, whether translation from JPA into sql works properly
5. [JpaScannerSqlExecutionPlanTest] - all in one test. This test scan repositories, find jpa queries and translates it into native sql queries. This is our input for test. Next step is to create example entities, run `ANALYZE` command and finally we check execution plans and costs for each query.

## Postgres SQLs

### Connect to db

```
psql -h localhost -p 5432 -d testdb -U test
```

### Check whether you have all indexes or not

```sql
select t.relname as table_name, i.relname as index_name, a.attname as column_name
from pg_class t, pg_class i, pg_index ix, pg_attribute a
where
    t.oid = ix.indrelid
    and i.oid = ix.indexrelid
    and a.attrelid = t.oid
    and a.attnum = ANY(ix.indkey)
    and t.relkind = 'r'
    and t.relname in ('activity_log', 'address', 'person');
order by t.relname, i.relname;
```

Output should be similar to this:

| Table name   | Index name              | Column name |
|:-------------|:------------------------|:------------|
| activity_log | activity_log_pkey       | id          |
| address      | address_pkey            | id          |
| person       | person_pkey             | id          |
| activity_log | idx_activitylog_action  | action      |
| address      | idx_address_city        | city        |



[Docker]: https://www.docker.com/
[JDK]: https://www.oracle.com/java/technologies/downloads/
[Spring Boot Application]: https://spring.io/projects/spring-boot
[Spring Repositories]: https://docs.spring.io/spring-data/data-commons/docs/1.6.1.RELEASE/reference/html/repositories.html
[PostgreSQL]: https://www.postgresql.org/
[Person]: src/main/java/pl/db/plan/scanner/entities/Person.java
[Address]: src/main/java/pl/db/plan/scanner/entities/Address.java
[ActivityLog]: src/main/java/pl/db/plan/scanner/entities/ActivityLog.java
[PersonIntegrationTest]: src/test/java/pl/db/plan/scanner/integration/PersonIntegrationTest.java
[SqlCaptureInspectorTest]: src/test/java/pl/db/plan/scanner/inspector/SqlCaptureInspectorTest.java
[SqlExecutionPlanTest]: src/test/java/pl/db/plan/scanner/inspector/SqlExecutionPlanTest.java
[JpaToSqlConversionTest]: src/test/java/pl/db/plan/scanner/inspector/JpaToSqlConversionTest.java
[JpaScannerSqlExecutionPlanTest]: src/test/java/pl/db/plan/scanner/inspector/JpaScannerSqlExecutionPlanTest.java