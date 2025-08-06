package part_3_concurrency

import zio.*
import utils.*

/**
 * Blocking
 * - Run blocking effects on a dedicated thread pool
 * - Blocking ZIOs cannot (usually) be interrupted
 * - Can use attemptBlockingInterrupt
 *  - based on Thread.interrupt, a heavy operation
 *  - can also fail if the code catches InterruptException
 * - Best option: attemptBlockingCancelable
 *  - Have an external flag/switch
 *  - Pass a "cancelling" effect which manipulates the switch
 *  - On interrupt, the fiber will call the cancelling effect
 * - Semantic blocking
 *  -  No threads are really blocked, only rescheduled
 */

import java.util.concurrent.atomic.AtomicBoolean

object BlockingEffects extends ZIOAppDefault {

  def blockingTask(n: Int): UIO[Unit] =
    ZIO.succeed(s"running blocking task $n").debugThread *>
      ZIO.succeed(Thread.sleep(10000)) *>
      blockingTask(n)

  val program = ZIO.foreachPar((1 to 100).toList)(blockingTask)
  // thread starvation

  // separate blocking thread pool
  val aBlockingZIO = ZIO.attemptBlocking {
    println(s"[${Thread.currentThread().getName}] running a long computation....")
    Thread.sleep(10000)
    42
  }

  // blocing code cannot (usually) be interrupted
  val tryIntterupting = for {
    blockingFib <- aBlockingZIO.fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting....").debugThread *> blockingFib.interrupt
    mol <- blockingFib.join
  } yield mol

  // can use attemptBlockingInterrupt
  // based on Thread.interrupt -> InterruptedException
  val aBlockingInterruptibleZIO = ZIO.attemptBlockingInterrupt {
    println(s"[${Thread.currentThread().getName}] running a long computation...")
    Thread.sleep(10000)
    42
  }

  // use to interrupt a long computation us a flag/switch
  def interruptibleBlockingEffect(canceledFlag: AtomicBoolean): Task[Unit] =
    ZIO.attemptBlockingCancelable { // effect
      (1 to 100000).foreach { element =>
        if (!canceledFlag.get()) {
          println(element)
          Thread.sleep(100)
        }
      }
    } (ZIO.succeed(canceledFlag.set(true))) // cancelling/interrupting effect

  // Semantic Blocking - i.e yield control of the tread to another fiber
  val sleepingThread = ZIO.succeed(Thread.sleep(1000)) // blocking, uninteruptible
  val sleeping = ZIO.sleep(1.second) // Semantically blocking
  //  yield
  val chainedZIO = (1 to 1000).map(i => ZIO.succeed(i)).reduce(_.debugThread *> _.debugThread)
  val yieldingDemo = (1 to 1000).map(i => ZIO.succeed(i)).reduce(_.debugThread *> ZIO.yieldNow *> _.debugThread)

  def run = chainedZIO

}
