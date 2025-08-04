# Notes on Fibers

---

- Parallelism = multiple computations running at the same time
- Concurrency = multiple computations overlap

- Parallel programs may not necessarily be concurrent
  - e.g. the tasks are independent

- Concurrent programs may not necessarily be parallel
  - e.g multi-tasking on the same CPU

- We focus on concurrency
  - Poses the most problems
  - Is almost always a requirement for useful programs

---

- Fibers = description of an effect being executed on some other thread
- Creating a fiber is an effectful operation
  - the fiber will be wrapped in a ZIO
- Managing a fiber is an effectful operation
  - the result of the operation is wrapped in another ZIO

---

- How Fibers work
  - ZIO has a thread pool that manages the execution of effects
    - thread
      - active = can run code
    - fiber
      - passive = just a data structure

- ZIO has a thread pool that manages the execution of effects
  - a few threads (100)
- Lots of fibers
  - 100000000s per GB heap
- ZIO schedules fibers for execution

*notice --- thread CPU, fibers - heap memory*

---

# Motivation for Fibers

- Why we need fibers
  - no more need for threads and locks
  - delegate thread management for ZIO runtime
  - avoid asynchronous code with callbacks (callback hell)
  - maintain pure functional programming
  - keep low-level primitives (e.g. blocking, waiting, joining, interrupting, cancelling)
- Fiber scheduling concepts and impl details
  - blocking effects in a fiber lead to descending
  - semantic blocking
  - cooperative scheduling
  - the same fiber can run on multiple JVM threads
  - work-stealing thread pool
- 