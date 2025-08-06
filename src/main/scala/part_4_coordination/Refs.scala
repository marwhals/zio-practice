package part_4_coordination

import utils.*
import zio.*

/**
 * Why? Purely functional, thread-safe state management
 * Purely functional atomic reference
 * Interacting with a Ref is an effect
 * - Setting new value
 * - Getting existing value
 * - Getting + Setting atomically
 * - updating with a function
 * - updating + getting (old value or new value)
 * - modifying + surfacing an external value
 */

object Refs extends ZIOAppDefault {

  // refs are purely functional atomic references
  val atomicMOL: ZIO[Any, Nothing, Ref[Int]] = Ref.make(42)

  // obtain a value
  val mol = atomicMOL.flatMap { ref =>
    ref.get // returns a UIO[Int], thread-safe getter
  }

  // changing
  val setMol = atomicMOL.flatMap { ref =>
    ref.set(100) // UIO[Unit], thread-safe setter
  }

  // get + change in ONE atomic operation
  val gsMOL = atomicMOL.flatMap { ref =>
    ref.getAndSet(500)
  }

  // update - run a function on the value
  val updatedMol: UIO[Unit] = atomicMOL.flatMap { ref =>
    ref.update(_ * 100) // ref.set(f(ref.get))
  }

  // update and get in ONE operation
  val updatedMolWithValue = atomicMOL.flatMap { ref =>
    ref.updateAndGet(_ * 100)
    ref.getAndUpdate(_ * 100)
  }

  // modify
  val modifyMol: UIO[String] = atomicMOL.flatMap { ref =>
    ref.modify(value => (s"my current value is $value", value * 100))
  }

  // use case is thread safe reads and writes
  // example: distributing work
  def demoConcurrentWorkImpure(): UIO[Unit] = {
    var count = 0

    def task(workload: String): UIO[Unit] = {
      val wordCount = workload.split(" ").length
      for {
        _ <- ZIO.succeed(s"Counting words for: $workload: $wordCount").debugThread
        newCount <- ZIO.succeed(count + wordCount)
        _ <- ZIO.succeed(s"new total: $newCount").debugThread
        _ <- ZIO.succeed(count += wordCount) // updating the variable
      } yield () // since this is an effect
    }

    val effects = List("sentence one", "another sentence", "sentence three").map(task)
    ZIO.collectAllParDiscard(effects)
  }
  /* ^^^^^^^^^^^^^^^^
  - Not thread safe
  - Hard to debug in case of failure
  - Mixing pure and impure code
   */
  def demoConcurrentWorkPure(): UIO[Unit] = {
    var count = 0

    def task(workload: String, total: Ref[Int]): UIO[Unit] = {
      val wordCount = workload.split(" ").length
      for {
        _ <- ZIO.succeed(s"Counting words for: $workload: $wordCount").debugThread
        newCount <- total.updateAndGet(_ + wordCount)
        _ <- ZIO.succeed(s"new total: $newCount").debugThread
      } yield ()
    }

    for {
      counter <- Ref.make(0)
      _ <- ZIO.collectAllParDiscard(
        List("Sentence one", "Sentence two", "Sentence 3").map(load => task(load, counter))
      )
    } yield ()
  }

  /**
   * TODO - Exercises 16:00
   *
   */

  def run = ???

}
