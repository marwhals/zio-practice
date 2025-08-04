package part_2_effects

import zio.*

import java.util.concurrent.TimeUnit

/**
 * Notes
 * - Layers
 *  - Standard dependency injection is clunky:
 *    - Doesn't scale
 *    - Becomes hard to debug/understand
 *    - Needs serious discipline to do well
 *    - Instantiates services at the point of call
 *    - Can easily leak resources
 *  - Alternative: ZIO dependencies
 *    - make them as needed when they are needed
 *    - pass them once at the "end of the world"
 *  - Benefits
 *    - business logic uncluttered by instantiation of dependencies
 *    - Resources used once at the end of the application
 *    - Logic is easily testable by passing a different resource implementation (e.g. a mocked one)
 *
 * - Layers API
 * --- Like regular ZIOs
 * ----- ZLayer.succeed(ConnectionPool.create(10))
 * --- Create a layer out of function arguments (magic macros)
 * ----- ZLayer.fromFunction(UserDatabase.create _) //NOTE: underscore required for explicit eta-expansion in Scala 2
 * --- Composing Layers
 * ----- Vertical: Consumes dependencies of the first, produces values of the last
 * ----- Horizontal: Consumes dependencies of both, produces values of both
 * --> see "val databaseLayerFull......
 * --> see "val subscriptionRequirementsLayer....................
 *
 * Layers API --- Macro based
 * - Magic auto-wiring
 * --- Macro-based inspection of type signatures, graph connections
 * --- Rich compiler errors for missing layers, duplicate layers
 * --- Pretty-printed graph presentation (text or Mermaid links)
 *
 */


object ZIODependencies extends ZIOAppDefault {

  // app to subscribe users to newsletter
  case class User(name: String, email: String)

  class UserSubscription(emailService: EmailService, userDatabase: UserDatabase) {
    def subscribeUser(user: User): Task[Unit] =
      for {
        _ <- emailService.email(user)
        _ <- userDatabase.insert(user)
      } yield ()
  }

  class EmailService {
    def email(user: User): Task[Unit] =
      ZIO.succeed(s"You've just been subscribed to Rock the JVM, Welcome, ${user.name}").unit
  }

  class UserDatabase(connectionPool: ConnectionPool) {
    def insert(user: User): Task[Unit] = for {
      conn <- connectionPool.get
      _ <- conn.runQuery(s"insert into subscribers(name, email) values (${user.name}, ${user.email})")
    } yield ()
  }

  class ConnectionPool(nConnections: Int) {
    def get: Task[Connection] =
      ZIO.succeed(println("Acquired connection")) *> ZIO.succeed(Connection())
  }

  case class Connection() {
    def runQuery(query: String): Task[Unit] =
      ZIO.succeed(println(s"Executing query: $query"))
  }

  object UserSubscription {
    def create(emailService: EmailService, userDatabase: UserDatabase) = new UserSubscription(emailService, userDatabase)

    val live: ZLayer[EmailService with UserDatabase, Nothing, UserSubscription] =
      ZLayer.fromFunction(create _)
  }

  object EmailService {
    def create(): EmailService = new EmailService

    val live: ZLayer[Any, Nothing, EmailService] =
      ZLayer.succeed(create())
  }

  object UserDatabase {
    def create(connectionPool: ConnectionPool): UserDatabase =
      new UserDatabase(connectionPool)

    val live: ZLayer[ConnectionPool, Nothing, UserDatabase] =
      ZLayer.fromFunction(create)
  }

  object ConnectionPool {
    def create(nConnections: Int): ConnectionPool =
      new ConnectionPool(nConnections)

    def live(nConnections: Int): ZLayer[Any, Nothing, ConnectionPool] =
      ZLayer.succeed(create(nConnections))
  }

  val subscriptionService = ZIO.succeed( // This pattern is known as dependency injection......many ways to do this
    UserSubscription.create(
      EmailService.create(),
      UserDatabase.create(
        ConnectionPool.create(10)
      )
    )
  )

  /**
   * Using companion objects for factory methods leads to clean DI but has a number of objects
   * - does not scale for many services -- nightmare to debug
   * - DI can be a 100x worse - need to know where everything comes form and can still rely on the developer organising all these services
   *  - pass dependencies partially
   *  - not having all dependencies in the same place
   *  - passing dependencies multiple times can lead to errors in your code. Can leak resources that you are not aware of.
   */

  def subscribe(user: User): ZIO[Any, Throwable, Unit] = for {
    sub <- subscriptionService // service is instantiated at the point of call
    _ <- sub.subscribeUser(user)
  } yield ()

  // risk leaking resources if you subscribe multiple users in the same program
  val program = for {
    _ <- subscribe(User("User123", "123@lalala.com"))
    _ <- subscribe(User("Potato", "potato@kebab.com"))
  } yield ()

  // alternative
  def subscribe_v2(user: User): ZIO[UserSubscription, Throwable, Unit] = for {
    sub <- ZIO.service[UserSubscription] // ZIO[UserSubscription, Nothing, UserSubscription]
    _ <- sub.subscribeUser(user)
  } yield ()

  val program_v2 = for {
    _ <- subscribe_v2(User("User123", "123@lalala.com"))
    _ <- subscribe_v2(User("Potato", "potato@kebab.com"))
  } yield ()

  /**
   * Second approach
   * - We don't need to care about dependencies until the end of the world
   * - All ZIOs requiring this dependency will use the same instance
   * - Can use different instances of the same type for different needs (e.g testing)
   * - layers can be created and composed much like regular ZIOs + rich API
   *
   */

  /**
   * ZLayers
   *
   */

  val connectionPoolLayer: ZLayer[Any, Nothing, ConnectionPool] = ZLayer.succeed(ConnectionPool.create(10))
  // a layer that requires a dependency (higher layer) can be built with ZLayer.fromFunction
  // (and automatically fetch the function arguments and place them into the ZLayer's dependency / environment type argument)
  val databaseLayer: ZLayer[ConnectionPool, Nothing, UserDatabase] =
    ZLayer.fromFunction(UserDatabase.create _)
  val emailServiceLayer: ZLayer[Any, Nothing, EmailService] =
    ZLayer.succeed(EmailService.create())
  val userSubscriptionServiceLayer: ZLayer[UserDatabase with EmailService, Nothing, UserSubscription] =
    ZLayer.fromFunction(UserSubscription.create _)

  // composing layers
  // vertical composition >>>
  val databaseLayerFull: ZLayer[Any, Nothing, UserDatabase] = connectionPoolLayer >>> databaseLayer
  // horizontal composition: combines the dependencies of both layer AND the values of both layers
  val subscriptionRequirementsLayer: ZLayer[Any, Nothing, UserDatabase with EmailService] = databaseLayerFull ++ emailServiceLayer
  // mix and match
  val userSubscriptionLayer: ZLayer[Any, Nothing, UserSubscription] =
    subscriptionRequirementsLayer >>> userSubscriptionServiceLayer

  // best practice: write "factory" methods exposing layers in the companion objects of the services
  val runnableProgram = program_v2.provide(userSubscriptionLayer)

  // magic
  val runnableProgram_v2 = program_v2.provide(
    UserSubscription.live,
    EmailService.live,
    UserDatabase.live,
    ConnectionPool.live(10),
    // ZIO will tell you if you're missing a layer
    // ZIO will tell you if you have multiple layers of the same type
    // ZIO can also generate the dependency graph
    ZLayer.Debug.tree,
    ZLayer.Debug.mermaid,
  )

  // magic version 2
  val userSubscriptionLayer_v2: ZLayer[Any, Nothing, UserSubscription] = ZLayer.make[UserSubscription](
    UserSubscription.live,
    EmailService.live,
    UserDatabase.live,
    ConnectionPool.live(10),
  )

  // passthrough
  val dbWithPoolLayer: ZLayer[ConnectionPool, Nothing, ConnectionPool with UserDatabase] = UserDatabase.live.passthrough
  // service = take a dep and expose it as a value to further layers
  val dbService = ZLayer.service[UserDatabase]
  // launch - creates a ZIO that uses the services and never finishes
  val subscriptionLaunch: ZIO[EmailService with UserDatabase, Nothing, Nothing] = UserSubscription.live.launch
  // memoization

  /*
    Already provides services: Clock, Random, System, Console
   */
  val getTime = Clock.currentTime(TimeUnit.SECONDS)
  val randomValue = Random.nextInt
  val sysVariable = System.env("HADOOP_HOME")
  val printlnEffect = Console.printLine("This is ZIO")

  // TODO test out the mermaid generation
  def run = runnableProgram_v2

}