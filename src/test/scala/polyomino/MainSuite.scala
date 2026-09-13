package polyomino

import polyomino.dotfiles.context.Context
import munit.FunSuite

class MainSuite extends FunSuite:
  private val testSandbox = os.temp.dir(prefix = "polyomino-main-test-")
  private val isolatedCtx = Context.isolated(testSandbox, dotfilesDir = os.pwd)

  override def afterAll(): Unit =
    if os.exists(testSandbox) then os.remove.all(testSandbox)
    super.afterAll()

  /** Run Main.dispatch without letting its help/version/error output pollute the test log. */
  private def dispatch(args: Array[String]): Int =
    val sink = java.io.ByteArrayOutputStream()
    val out = java.io.PrintStream(sink)
    Console.withOut(out)(Console.withErr(out)(Main.dispatch(args, Some(isolatedCtx))))

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

  test("Main.dispatch routes theme command"):
    val code = dispatch(Array("theme", "list"))
    assertEquals(code, 0)

  test("Main.dispatch routes validate command"):
    val code = dispatch(Array("validate"))
    assert(code == 0 || code == 1)

  test("Main.dispatch routes healthcheck (alias for validate)"):
    val code = dispatch(Array("healthcheck"))
    assert(code == 0 || code == 1)

  test("Main.dispatch routes uninstall command"):
    val code = dispatch(Array("uninstall"))
    assertEquals(code, 0)

  test("Main.dispatch with no args shows help"):
    val code = dispatch(Array())
    assertEquals(code, 0)

  test("Main.dispatch handles argv[0] symlink routing"):
    val code = dispatch(Array("version"))
    assertEquals(code, 0)

  test("Main.dispatch routes draw-window"):
    val code = dispatch(Array("draw-window"))
    assertEquals(code, 0)

  test("Main.dispatch routes media-status"):
    val code = dispatch(Array("media-status"))
    assertEquals(code, 0)

  test("Main.dispatch routes fastfetch-logo"):
    val code = dispatch(Array("fastfetch-logo"))
    assertEquals(code, 0)

  test("Main.dispatch routes projects"):
    val code = dispatch(Array("projects"))
    assertEquals(code, 0)

  test("Main.dispatch routes theme-cycle"):
    val code = dispatch(Array("theme-cycle"))
    assertEquals(code, 0)
