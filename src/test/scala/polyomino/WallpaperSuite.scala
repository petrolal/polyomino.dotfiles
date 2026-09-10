package polyomino

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.theme.ThemeEngine
import polyomino.dotfiles.wallpaper.WallpaperEngine
import munit.FunSuite

class WallpaperSuite extends FunSuite {
  private def withIsolatedContext[T](f: Context => T): T =
    val tempDir = os.temp.dir(prefix = "polyomino-wallpaper-test-")
    try
      val ctx = Context.isolated(tempDir, dotfilesDir = os.pwd)
      f(ctx)
    finally
      os.remove.all(tempDir)

  test("wallpapersForFlavor only returns files that belong to the flavor") {
    withIsolatedContext { c =>
      val matriz = WallpaperEngine.wallpapersForFlavor(c, "matriz")
      assert(matriz.nonEmpty, "expected at least themes/wallpapers/matriz.svg")
      assert(matriz.forall { p =>
        val s = p.baseName.toLowerCase
        s == "matriz" || s.startsWith("matriz-") || s.startsWith("matriz_")
      }, s"leaked a non-matriz wallpaper: ${matriz.map(_.last)}")
      // caravela.svg must never show up under the matriz flavor
      assert(!matriz.exists(_.last.toLowerCase.startsWith("caravela")))
    }
  }

  test("activeFlavor reflects the applied theme state") {
    withIsolatedContext { c =>
      ThemeEngine.run(c, List("caravela", "--flat"))
      assertEquals(WallpaperEngine.activeFlavor(c), "caravela")
      ThemeEngine.run(c, List("matriz", "--flat"))
      assertEquals(WallpaperEngine.activeFlavor(c), "matriz")
    }
  }

  test("run list succeeds") {
    withIsolatedContext { c =>
      ThemeEngine.run(c, List("matriz", "--flat"))
      assert(WallpaperEngine.run(c, List("list")).isRight)
    }
  }

  test("run next persists WALLPAPER= to the theme state file") {
    withIsolatedContext { c =>
      ThemeEngine.run(c, List("matriz", "--flat"))
      val res = WallpaperEngine.run(c, List("next"))
      assert(res.isRight, s"wallpaper next failed: $res")
      val stateFile = c.configDir / "polyomino" / "theme" / "state"
      val wallpaperLine = os.read.lines(stateFile).find(_.startsWith("WALLPAPER="))
      assert(wallpaperLine.exists(_.stripPrefix("WALLPAPER=").trim.nonEmpty),
        s"state file has no WALLPAPER= entry: $wallpaperLine")
      // and the sway colors.conf now carries an output bg directive
      val colors = os.read(c.configDir / "sway" / "colors.conf")
      assert(colors.linesIterator.exists(l => l.startsWith("output ") && l.contains(" bg ")),
        "colors.conf missing the output bg wallpaper directive")
    }
  }

  test("run with an unknown wallpaper name returns Left") {
    withIsolatedContext { c =>
      ThemeEngine.run(c, List("matriz", "--flat"))
      assert(WallpaperEngine.run(c, List("definitely-not-a-wallpaper-name")).isLeft)
    }
  }
}
