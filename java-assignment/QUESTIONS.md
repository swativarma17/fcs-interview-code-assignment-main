  # Questions

Here we have 3 questions related to the code base for you to answer. It is not about right or wrong, but more about what's the reasoning behind your decisions.

1. In this code base, we have some different implementation strategies when it comes to database access layer and manipulation. If you would maintain this code base, would you refactor any of those? Why?

**Answer:**
```
- Centralize transactional boundaries (@Transactional at service/application layer) and unify error semantics (404/409, optimistic locking).
- Align Product/Store to the same pattern for consistency, testability, and easier future changes (e.g., swapping DB, adding caching/outbox).
```
----
2. When it comes to API spec and endpoints handlers, we have an Open API yaml file for the `Warehouse` API from which we generate code, but for the other endpoints - `Product` and `Store` - we just coded directly everything. What would be your thoughts about what are the pros and cons of each approach and what would be your choice?

**Answer:**
- advantages of using open API :
-Automatic schema validation + better documentation discoverability.
- Faster scaffolding; easier external integration governance but 
Spec changes add process overhead; may slow rapid iteration.
choice: 
- Use OpenAPI-first for all externally consumed APIs (Warehouse, Product, Store).
- If an endpoint is truly internal/prototyping, hand-write—but still maintain a minimal spec to avoid drift

```
----
3. Given the need to balance thorough testing with time and resource constraints, how would you prioritize and implement tests for this project? Which types of tests would you focus on, and how would you ensure test coverage remains effective over time?

**Answer:**
```
- Focus pyramid: Unit tests (business rules, mappers, validators) ,Repo integration (Testcontainers) > API/controller + contract tests > a few E2E critical flows.
- Validate Warehouse endpoints against OpenAPI schema; add similar specs for Product/Store to enable contract tests
- Maintain reusable test data builders/factories to keep tests readable and stable over time.

```