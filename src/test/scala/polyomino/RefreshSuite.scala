package polyomino

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.refresh.RefreshEngine
import munit.FunSuite

class RefreshSuite extends FunSuite:
  private def withIsolatedContext[T](f: Context => T): T =
    val tempDir = os.temp.dir(prefix = "polyomino-refresh-test-")
    try
      val ctx = Context.isolated(tempDir, dotfilesDir = os.pwd)
      f(ctx)
    finally
      os.remove.all(tempDir)

  test("RefreshEngine.runRefresh executes safely in test mode"):
    withIsolatedContext { ctx =>
      val res = RefreshEngine.runRefresh(ctx)
      assert(res.isRight)
    }

  test("RefreshEngine.runOsColorscheme returns Right in test mode"):
    withIsolatedContext { ctx =>
      val res = RefreshEngine.runOsColorscheme(ctx)
      assert(res.isRight)
    }
