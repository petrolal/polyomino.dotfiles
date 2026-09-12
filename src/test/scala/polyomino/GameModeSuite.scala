package polyomino

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.gamemode.GameModeEngine
import munit.FunSuite

class GameModeSuite extends FunSuite:
  private def withIsolatedContext[T](f: Context => T): T =
    val tempDir = os.temp.dir(prefix = "polyomino-gamemode-test-")
    try
      val ctx = Context.isolated(tempDir, dotfilesDir = os.pwd)
      f(ctx)
    finally
      os.remove.all(tempDir)

  test("GameModeEngine starts inactive by default"):
    withIsolatedContext { ctx =>
      assert(!GameModeEngine.isActive(ctx))
      val res = GameModeEngine.run(ctx, List("status"))
      assert(res.isRight)
    }

  test("GameModeEngine enable and disable cycle"):
    withIsolatedContext { ctx =>
      val enableRes = GameModeEngine.enable(ctx)
      assert(enableRes.isRight)
      assert(GameModeEngine.isActive(ctx))

      val disableRes = GameModeEngine.disable(ctx)
      assert(disableRes.isRight)
      assert(!GameModeEngine.isActive(ctx))
    }

  test("GameModeEngine toggle alternates active state"):
    withIsolatedContext { ctx =>
      assert(!GameModeEngine.isActive(ctx))
      assert(GameModeEngine.toggle(ctx).isRight)
      assert(GameModeEngine.isActive(ctx))
      assert(GameModeEngine.toggle(ctx).isRight)
      assert(!GameModeEngine.isActive(ctx))
    }

  test("GameModeEngine --waybar flag outputs JSON format without errors"):
    withIsolatedContext { ctx =>
      val resInactive = GameModeEngine.run(ctx, List("--waybar"))
      assert(resInactive.isRight)

      GameModeEngine.enable(ctx)
      val resActive = GameModeEngine.run(ctx, List("--waybar"))
      assert(resActive.isRight)
    }

  test("GameModeEngine rejects unknown subcommand"):
    withIsolatedContext { ctx =>
      val res = GameModeEngine.run(ctx, List("invalid-subcommand"))
      assert(res.isLeft)
    }
