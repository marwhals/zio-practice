package part_3_concurrency

import zio._
import utils.*

object Schedules extends ZIOAppDefault {

  val aZIO = Random.nextBoolean.flatMap { flag =>
    if (flag) ZIO.succeed("fetched value!").debugThread
    else ZIO.succeed("failure......").debugThread *> ZIO.fail("error")
  }

  val aRetriedZIO = aZIO.retry(Schedule.recurs(10)) // retries 10 times, returns the first success, and the last failure if all 10 attempts fail

  // schedules are data structures that describe how effects should be timed
  val oneTimeSchedule = Schedule.once
  val recurrentSchedule = Schedule.recurs(10)
  val fixedIntervalScehdule = Schedule.spaced(1.second) // retries every 1s until a success is returned
  // exponential backoff (a critical resource is down etc)
  val exBackoffSchedule = Schedule.exponential(1.second, 2.0)
  val fiboScehdule = Schedule.fibonacci(1.second)

  // combinator
  val recurrentAndSpaced = Schedule.recurs(3) && Schedule.spaced(1.second) // every attempt is 1s apart, 3 attempts total
  // sequencing
  val recurrentThenSpaced = Schedule.recurs(3) ++ Schedule.spaced(1.second) // 3 retries, then every 1s

  // Schedules have
  // R = environment,
  // I = input (errors in the case of .retry, values in the case of .repeat),
  // O = output (values for the next schedule so that you can do something with them)
  val totalElapsed = Schedule.spaced(1.second) >>> Schedule.elapsed.map(time => println(s"total time elapsed: $time"))

  def run = aZIO.retry(totalElapsed)

}
