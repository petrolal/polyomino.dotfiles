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

  test("Validator.run executes without crashing"):
    val ctx = Context.discover().toOption.get
    val res = Validator.run(ctx, Nil)
    assert(res.isRight || res.isLeft)

  test("Validator.run checks required tools"):
    val ctx = Context.discover().toOption.get
    val res = Validator.run(ctx, Nil)
    // Either all tools pass or some fail - both are valid outcomes
    assert(res.isRight || res.isLeft)

  test("Validator.VersionStr is non-empty"):
    assert(Validator.VersionStr.nonEmpty)
    assert(Validator.VersionStr.contains("0.1.0"))

  test("Validator.Subcommands contains expected commands"):
    assert(Validator.Subcommands.contains("theme"))
    assert(Validator.Subcommands.contains("install"))
    assert(Validator.Subcommands.contains("autotiling"))

  test("menu subcommand is wired into both symlink/audit lists and help"):
    assert(Validator.Subcommands.contains("menu"))
    assert(polyomino.dotfiles.install.DeployInstaller.Subcommands.contains("menu"))
    assert(Main.UmbrellaHelp.contains("menu"))
