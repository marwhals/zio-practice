package part_2_effects

import scala.concurrent.Future

/**
 * - Pure functional program = a big expression computing a value
 *  - referential transparency ---- can replace an expression with its value without changing the behaviour
 * - Expressions performing side effects are not replaceable
 *  i.e break referential transparency
 *
 *  Effect properties
 *    - It described what kind of computation it will perform
 *    - The type signature describes the value it will calculate
 *    - It separates effect description from effect execution (when externally visible effects are produced)
 *
 */

object Effects {

  // functional programming - nothing more than a giant expression containing a value ^_^
  def combine(a: Int, b: Int): Int = a + b

  // local reasoning - type signature describes the kind of computation that will be performed
  // referential transparency
  val fiv = combine(2,3) // is equivalent to
  val five_v2 = 2 + 3
  val five_v3 = 5

  // not all expressions are RT
  val resultOfPrinting: Unit = println("Learning ZIO")
  val resultOfPrinting_v2: Unit = () // not the same behaviour even though the values are the same. Breaks referential transparency

  // example2: changing a variable
  var anInt = 0
  val changingInt: Unit = (anInt = 42) // side effect
  val changingInt_v2: Unit = () // not the same program
  // need side effects but functional programming is nice. Need to bridge this.

  /**
    Effect properties:
    - Local Reasoning: the type signature describes what type of computation it will perform
    - Local Reasoning: the type signature describes the type of VALUE that it will produce
    - if side effects are required, construction must be separate from the execution
   */

  /*
    Example: Option = possibly absent value
    - type signature describes the kind of computation = a possibly absent value
    - type signature says that the computation returns an A, if the computation does produce something
    - no side effects are needed
    ----- Option is an effect
   */
  val anOption: Option[Int] = Option(42)
  /*
    Example 2: Future
    - describes an asynchronous computation
    - produces a value of type A, if it finishes and it's successful
    - side effects are required, construction is not separate from execution

    ------- Future is not an effect
   */

  import scala.concurrent.ExecutionContext.Implicits.global

  val aFuture: Future[Int] = Future(42)

  /*
    Example 3: MyIO
    - describes a computation which might perform side effects
    - produces a value of type A if the computation is successful
    - side effects are required, construction IS SEPARATE from execution

    MyIO is an effect
   */
  case class MyIO[A](unsafeRun: () => A) { // A \lambda
    def map[B](f: A => B): MyIO[B] =
      MyIO(() => f(unsafeRun()))

    def flatMap[B](f: A => MyIO[B]): MyIO[B] =
      MyIO(() => f(unsafeRun()).unsafeRun())
  }

  val anIOWithSideEffects: MyIO[Int] = MyIO(() => {
    println("producing effect")
    42
  })

  /**
   * TODO - Exercises - create some IO which
   *  1. measure the current time of the system
   *     2. measure the duration of a computation
   *    - use exercise 1
   *    - use map/flatMap combinations of MyIO
   *      3. read something from the console
   *      4. print something to the console (e.g. "what's your name"), then read, then print a welcome message
   */


  def main(args: Array[String]): Unit = {
  }

}
