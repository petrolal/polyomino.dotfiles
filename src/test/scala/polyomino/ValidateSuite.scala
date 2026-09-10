package polyomino

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.validate.Validator
import munit.FunSuite

class ValidateSuite extends FunSuite:
  test("Context.discover returns resolved paths"):
    val ctx = Context.discover()
    assert(ctx.isRight)
    val c = ctx.toOption.get
    assert(c.home.toString.nonEmpty)

  test("Context.discover returns valid home directory"):
    val ctx = Context.discover().toOption.get
    assert(os.exists(ctx.home))

  test("Context.discover returns valid configDir"):
    val ctx = Context.discover().toOption.get
    assert(ctx.configDir.toString.nonEmpty)
    assert(!ctx.configDir.toString.contains("//"))

  test("Context.discover configDir is non-empty"):
    val ctx = Context.discover().toOption.get
    assert(ctx.configDir.toString.nonEmpty)
    assert(ctx.configDir.toString.contains("config"))

  test("Validator.run detects missing configuration in uninitialized environment"):
    val tempDir = os.temp.dir(prefix = "polyomino-validate-test-")
    try
      val ctx = Context.isolated(tempDir, dotfilesDir = os.pwd)
      val res = scala.Console.withOut(new java.io.ByteArrayOutputStream()) {
        Validator.run(ctx, Nil)
      }
      assert(res.isLeft)
    finally
      os.remove.all(tempDir)

  test("Context.isolated initializes sandbox paths correctly"):
    val tempDir = os.temp.dir(prefix = "polyomino-isolated-test-")
    try
      val ctx = Context.isolated(tempDir)
      assert(ctx.isTest)
      assertEquals(ctx.swaySocket, None)
      assert(os.exists(ctx.home))
      assert(os.exists(ctx.configDir))
      assert(os.exists(ctx.shareDir))
    finally
      os.remove.all(tempDir)

  test("Validator.VersionStr is non-empty"):
    assert(Validator.VersionStr.nonEmpty)
    assert(Validator.VersionStr.contains("0.1.0"))

  test("Validator.Subcommands contains expected commands"):
    val expected = Seq("theme", "install", "autotiling", "runtime-refresh", "os-colorscheme", "lock", "idle", "screenshot", "backup", "restore", "menu", "whichkey")
    expected.foreach { cmd =>
      assert(Validator.Subcommands.contains(cmd), s"Missing expected command in Validator: $cmd")
    }

  test("menu subcommand is wired into both symlink/audit lists and help"):
    assert(Validator.Subcommands.contains("menu"))
    assert(polyomino.dotfiles.install.DeployInstaller.Subcommands.contains("menu"))
    assert(Main.UmbrellaHelp.contains("menu"))

  test("Validator detects missing binaries and directories in clean environment"):
    val tempDir = os.temp.dir(prefix = "polyomino-validate-dry-")
    try
      val ctx = Context.isolated(tempDir, dotfilesDir = os.pwd)
      val res = scala.Console.withOut(new java.io.ByteArrayOutputStream()) {
        Validator.run(ctx, List("--dry-run"))
      }
      assert(res.isLeft)
    finally
      os.remove.all(tempDir)
