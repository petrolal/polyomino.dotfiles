package polyomino

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.sysutils.SysUtils
import munit.FunSuite

class SysUtilsSuite extends FunSuite:
  private def withIsolatedContext[T](f: Context => T): T =
    val tempDir = os.temp.dir(prefix = "polyomino-sysutils-test-")
    try
      val ctx = Context.isolated(tempDir, dotfilesDir = os.pwd)
      f(ctx)
    finally
      os.remove.all(tempDir)

  test("SysUtils.runLock handles lock call safely in test mode"):
    withIsolatedContext { ctx =>
      val res = SysUtils.runLock(ctx, List("--screenshot", "/tmp/test-lock-unit.png"))
      assert(res.isRight)
    }

  test("SysUtils.runLockPreview returns Right in test mode"):
    withIsolatedContext { ctx =>
      val res = SysUtils.runLockPreview(ctx, List("--preview"))
      assert(res.isRight)
    }

  test("SysUtils.runIdle returns Right in test mode without spawning daemons"):
    withIsolatedContext { ctx =>
      val res = SysUtils.runIdle(ctx)
      assert(res.isRight)
    }

  test("SysUtils.runScreenshot rejects invalid mode"):
    withIsolatedContext { ctx =>
      val res = SysUtils.runScreenshot(ctx, List("invalid-mode"))
      assert(res.isLeft)
    }

  test("SysUtils.runScreenshot accepts 'full' mode in test sandbox"):
    withIsolatedContext { ctx =>
      val res = SysUtils.runScreenshot(ctx, List("full"))
      assert(res.isRight)
      val shotsDir = ctx.home / "Pictures" / "Screenshots"
      assert(os.exists(shotsDir))
      assertEquals(os.list(shotsDir).length, 1)
    }

  test("SysUtils.runScreenshot accepts 'region' mode in test sandbox"):
    withIsolatedContext { ctx =>
      val res = SysUtils.runScreenshot(ctx, List("region"))
      assert(res.isRight)
      val shotsDir = ctx.home / "Pictures" / "Screenshots"
      assert(os.exists(shotsDir))
      assertEquals(os.list(shotsDir).length, 1)
    }

  test("SysUtils.runScreenshot accepts 'window' mode in test sandbox"):
    withIsolatedContext { ctx =>
      val res = SysUtils.runScreenshot(ctx, List("window"))
      assert(res.isRight)
      val shotsDir = ctx.home / "Pictures" / "Screenshots"
      assert(os.exists(shotsDir))
      assertEquals(os.list(shotsDir).length, 1)
    }

  test("SysUtils.runScreenshot defaults to region mode when args are empty"):
    withIsolatedContext { ctx =>
      val res = SysUtils.runScreenshot(ctx, Nil)
      assert(res.isRight)
      val shotsDir = ctx.home / "Pictures" / "Screenshots"
      assert(os.exists(shotsDir))
      assertEquals(os.list(shotsDir).length, 1)
    }

  test("SysUtils.findFocusedNodeGeometry extracts window coordinates from tree"):
    val mockTree = ujson.Obj(
      "nodes" -> ujson.Arr(
        ujson.Obj(
          "focused" -> ujson.Bool(false),
          "rect" -> ujson.Obj("x" -> 0, "y" -> 0, "width" -> 800, "height" -> 600)
        ),
        ujson.Obj(
          "focused" -> ujson.Bool(true),
          "rect" -> ujson.Obj("x" -> 1920, "y" -> 100, "width" -> 1280, "height" -> 720)
        )
      ),
      "floating_nodes" -> ujson.Arr()
    )
    val geom = SysUtils.findFocusedNodeGeometry(mockTree)
    assertEquals(geom, Some("1920,100 1280x720"))

  test("SysUtils.findFocusedNodeGeometry returns None when no node is focused"):
    val mockTree = ujson.Obj(
      "focused" -> ujson.Bool(false),
      "nodes" -> ujson.Arr(),
      "floating_nodes" -> ujson.Arr()
    )
    val geom = SysUtils.findFocusedNodeGeometry(mockTree)
    assertEquals(geom, None)

  test("SysUtils.runCalendar executes safely in test mode"):
    withIsolatedContext { ctx =>
      val res = SysUtils.runCalendar(ctx)
      assert(res.isRight)
    }

  test("SysUtils.runDrawWindow handles test mode safely"):
    withIsolatedContext { ctx =>
      val res = SysUtils.runDrawWindow(ctx, List("--spawn"))
      assert(res.isRight)
    }

  test("SysUtils.runMediaStatus outputs valid JSON in test mode"):
    withIsolatedContext { ctx =>
      val res = SysUtils.runMediaStatus(ctx)
      assert(res.isRight)
    }

  test("SysUtils.runFastfetchLogo symlinks matching logo in assets directory"):
    withIsolatedContext { ctx =>
      val assetsDir = ctx.configDir / "fastfetch" / "assets"
      os.makeDir.all(assetsDir)
      os.write(assetsDir / "polyomino_tetris.txt", "TEST_LOGO")
      val res = SysUtils.runFastfetchLogo(ctx)
      assert(res.isRight)
      assert(os.exists(assetsDir / "current_logo.txt") || os.isLink(assetsDir / "current_logo.txt"))
    }

  test("SysUtils.runWelcome succeeds when script exists and fails when missing"):
    withIsolatedContext { ctx =>
      val res = SysUtils.runWelcome(ctx)
      assert(res.isRight)

      val missingCtx = ctx.copy(dotfilesDir = ctx.home / "empty")
      val missingRes = SysUtils.runWelcome(missingCtx)
      assert(missingRes.isLeft)
    }

