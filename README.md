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

[Docker]: https://www.docker.com/
[JDK]: https://www.oracle.com/java/technologies/downloads/
[Spring Boot Application]: https://spring.io/projects/spring-boot
[Spring Repositories]: https://docs.spring.io/spring-data/data-commons/docs/1.6.1.RELEASE/reference/html/repositories.html
[PostgreSQL]: https://www.postgresql.org/
[Person]: src/main/java/pl/db/plan/scanner/entities/Person.java
[Address]: src/main/java/pl/db/plan/scanner/entities/Address.java
[ActivityLog]: src/main/java/pl/db/plan/scanner/entities/ActivityLog.java