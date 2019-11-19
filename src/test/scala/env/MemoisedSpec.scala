package env

import zio._
import zio.test._
import zio.test.Assertion._
import zio.duration._

import zio.cookbook.test.ZIOBaseSpec

import cats.Eval
// import zio.test.mock.MockConsole.putStrLn

//noinspection TypeAnnotation
object ToTest {
  var callCount: Int = 0;
  val resetCallCount: UIO[Unit] = ZIO.effectTotal {
    callCount = 0; ()
  }
  val getCallCount: UIO[Int] = ZIO.effectTotal(callCount)

  val plainVal: Int = {
    callCount = callCount + 1
    println(s"v1: $callCount")
    callCount
  }

  val scalaVal: Task[Int] = ZIO.effect(plainVal)

  val rand = random.nextString(2)

  val catsNow   = Eval.now(plainVal)
  val catsLater = Eval.later(plainVal)

  val scalaValMemoized = scalaVal.memoize
  val scalaValCached   = scalaVal.cached(10.seconds).flatten

  val scalaValMemoized2: ZIO[Any, Nothing, IO[Throwable, Int]] = scalaVal.memoize
  val testScalaVal = testM("scala val caching") {
    for {
      _    <- resetCallCount
      val1 <- scalaVal
      val2 <- scalaVal
    } yield assert(val1, equalTo(val2))
  }

  val testScalaValMemoized1 = testM("scala val memoized caching [1]") {
    for {
      _    <- resetCallCount
      svm  <- scalaVal.memoize
      val1 <- svm
      val2 <- svm
    } yield assert(val1, equalTo(val2))
  }

  val testScalaValMemoized2 = testM("scala val memoized caching [2]") {
    for {
      _    <- resetCallCount
      val1 <- scalaValMemoized
      val2 <- scalaValMemoized
    } yield assert(val1, equalTo(val2))
  }

  val testScalaValCached1 = testM("scala val caching [1]") {
    for {
      _     <- resetCallCount
      cache <- scalaVal.cached(10.seconds)
      val1  <- cache
      val2  <- cache
    } yield assert(val1, equalTo(val2))
  }
  val testScalaValCached2 = testM("scala val caching [2]") {
    for {
      _    <- resetCallCount
      val1 <- scalaValCached
      val2 <- scalaValCached
    } yield assert(val1, equalTo(val2))
  }

  // val test0 = testM("test0") {
  //   for {
  //     str <- rand.memoize
  //     str0  str
  //   } yield assert(str, equalTo(str0))
  // }
  val t = Task {
    println(s"Compute")
    5
  }
  val test0 = testM("0") {
    // val out = t.flatMap(tt => tt + tt)
    // assert(t, equalTo(out))
    for {
      memoized <- t.memoize
      tt1      <- memoized
      tt2      <- memoized
    } yield assert(memoized, equalTo(tt1 + tt2))
  }

  def func(): Int = {
    println(s"Compute")
    5
  }

  // val eff = Task.effect()
  // val v0 = FunctionIO.fromFunction(func)

  // Arrows
  val add1: FunctionIO[Nothing, Int, Int] = FunctionIO.fromFunction(_ + 1)
  val eff1: FunctionIO[Nothing, Int, Int] = FunctionIO.effectTotal(_ => -1)
  val mul2: FunctionIO[Nothing, Int, Int] = FunctionIO.fromFunction(_ * 2)

  val out = -4
  val eff = ZIO.succeed(out)

  import java.util.UUID

  lazy val eff0 = ZIO.effect { println("Hi"); UUID.randomUUID() }
}

import ToTest._

object LazySpec
    extends ZIOBaseSpec(
      suite("ZIO val/def/memoize Specs")(
        testM("arrow compose lifed methods") {
          assertM((add1 >>> mul2 >>> add1).run(1), equalTo(5))
        },
        testM("Cache ext effect") {
          for {
            init <- eff
            v1   <- (add1 >>> add1 >>> add1).run(init)
          } yield assert(v1, equalTo(out + 3))
        },
        // testM("Lazy test") {
        //   for {
        //     v0 <- eff0
        //     v1 <- eff0
        //   } yield assert(v0, equalTo(v1))
        // },
        testM("Cats Lazy eval with ZIO caches a task") {
          for {
            init <- ZIO.effect(Eval.later(plainVal))
            val1 <- Task(init)
            val2 <- Task(init)
          } yield assert(val1, equalTo(val2))
        },
        testM("Cats Eager eval with ZIO doesn't cache a task") {
          for {
            init <- ZIO.effect(Eval.now(plainVal))
            val1 <- ZIO.effect(init.value)
            val2 <- Task(init.value)
          } yield assert(val1, equalTo(val2))
        },
        testM("Cats Later must cache values") {
          for {
            val1 <- Task(catsLater)
            val2 <- Task(catsLater)
          } yield assert(val1.value, equalTo(val2.value))
        },
        testM("Cats now must NOT cache values") {
          for {
            val1 <- Task(catsNow)
            val2 <- Task(catsNow)
          } yield assert(val1.value, isLessThan(val2.value))
        }
      )
    )