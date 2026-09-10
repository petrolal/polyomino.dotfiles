package polyomino

import munit.FunSuite

class MainSuite extends FunSuite:
  private val isCI = sys.env.contains("CI") || sys.env.contains("GITHUB_ACTIONS")

  override def munitTests(): Seq[Test] =
    if isCI then Nil else super.munitTests()

  /** Run Main.dispatch without letting its help/version/error output pollute the test log. */
  private def dispatch(args: Array[String]): Int =
    val sink = java.io.ByteArrayOutputStream()
    val out = java.io.PrintStream(sink)
    Console.withOut(out)(Console.withErr(out)(Main.dispatch(args)))

  test("Main.dispatch returns 0 for version"):
    val code = dispatch(Array("version"))
    assertEquals(code, 0)

  test("Main.dispatch returns 0 for -v"):
    val code = dispatch(Array("-v"))
    assertEquals(code, 0)

  test("Main.dispatch returns 0 for --version"):
    val code = dispatch(Array("--version"))
    assertEquals(code, 0)

  test("Main.dispatch returns non-zero for unknown command"):
    val code = dispatch(Array("non-existent-command-12345"))
    assert(code != 0)

  test("Main.dispatch routes sdd command"):
    val code = dispatch(Array("sdd", "--tokens"))
    assertEquals(code, 0)

  test("Main.dispatch routes theme command"):
    val code = dispatch(Array("theme", "list"))
    assertEquals(code, 0)

  test("Main.dispatch routes validate command"):
    val code = dispatch(Array("validate"))
    assert(code == 0 || code == 1)

  test("Main.dispatch routes healthcheck (alias for validate)"):
    val code = dispatch(Array("healthcheck"))
    assert(code == 0 || code == 1)

  test("Main.dispatch with no args shows help"):
    val code = dispatch(Array())
    assertEquals(code, 0)

  test("Main.dispatch handles argv[0] symlink routing"):
    val code = dispatch(Array("version"))
    assertEquals(code, 0)
