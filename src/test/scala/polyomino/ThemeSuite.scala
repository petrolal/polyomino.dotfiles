package polyomino

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.theme.{Palette, ThemeEngine}
import munit.FunSuite
import os._

class ThemeSuite extends FunSuite {
  test("Palette.find retrieves custom palettes") {
    val ctx = Context.discover().toOption.get
    val palette = Palette.find("matriz", ctx)
    assertEquals(palette.name, "matriz")

    val fallback = Palette.find("unknown-theme", ctx)
    assert(fallback.name.nonEmpty)
  }

  test("Palette.listAll discovers custom conf palettes") {
    val ctx = Context.discover().toOption.get
    val themes = Palette.listAll(ctx)
    assert(themes.contains("matriz"))
    assert(themes.contains("encruza"))
    assert(themes.contains("caravela"))
    assert(themes.contains("aruanda"))
  }

  test("Palette.find case-insensitive lookup") {
    val ctx = Context.discover().toOption.get
    val matriz1 = Palette.find("matriz", ctx)
    val matriz2 = Palette.find("MATRIZ", ctx)
    val matriz3 = Palette.find("Matriz", ctx)
    assertEquals(matriz1.name, matriz2.name)
    assertEquals(matriz2.name, matriz3.name)
  }

  test("Palette.FallbackPalette has valid colors") {
    val p = Palette.FallbackPalette
    assertEquals(p.name, "matriz")
    assert(p.base.startsWith("#"))
    assert(p.accent.startsWith("#"))
    assert(p.text.startsWith("#"))
  }

  private def withIsolatedContext[T](f: Context => T): T =
    val tempDir = os.temp.dir(prefix = "polyomino-theme-test-")
    try
      val ctx = Context.isolated(tempDir, dotfilesDir = os.pwd)
      f(ctx)
    finally
      os.remove.all(tempDir)

  test("ThemeEngine.run renders custom theme config files") {
    withIsolatedContext { ctx =>
      val res = ThemeEngine.run(ctx, List("matriz"))
      assert(res.isRight)
      assert(os.exists(ctx.configDir / "kitty" / "theme.conf"))
      assert(os.exists(ctx.configDir / "waybar" / "theme.css"))
      assert(os.exists(ctx.configDir / "wofi" / "theme.css"))
      assert(os.exists(ctx.configDir / "wofi" / "style.css"))
      assert(os.exists(ctx.configDir / "rofi" / "theme.rasi"))
      assert(os.exists(ctx.configDir / "rofi" / "whichkey.rasi"))
      assert(os.exists(ctx.configDir / "swaync" / "style.css"))
    }
  }

  test("ThemeEngine.run applies all supported themes") {
    withIsolatedContext { ctx =>
      for flavor <- List("matriz", "encruza", "caravela", "aruanda") do
        val res = ThemeEngine.run(ctx, List(flavor))
        assert(res.isRight, s"$flavor theme apply failed: $res")
        val state = os.read(ctx.configDir / "polyomino" / "theme" / "state")
        assert(state.contains(s"FLAVOR=$flavor"))
    }
  }

  test("ThemeEngine.run with flat mode") {
    withIsolatedContext { ctx =>
      val res = ThemeEngine.run(ctx, List("matriz", "--flat"))
      assert(res.isRight)
    }
  }

  test("all 4 themes preserve the Enterprise CAD Waybar layout") {
    withIsolatedContext { ctx =>
      val waybarStyle = ctx.configDir / "waybar" / "style.css"
      for flavor <- List("matriz", "encruza", "caravela", "aruanda") do
        val res = ThemeEngine.run(ctx, List(flavor, "--flat"))
        assert(res.isRight, s"$flavor theme apply failed: $res")
        val css = os.read(waybarStyle)
        assert(css.contains("window#waybar") && css.contains("border-bottom: 1px solid @overlay0;"), s"$flavor: missing waybar overlay bottom border")
        assert(css.contains("border-radius: 0px") || css.contains("border-radius: 0;"), s"$flavor: missing CAD 0px border radius")
        assert(css.contains("#left") && css.contains("#center") && css.contains("#right"),
          s"$flavor: missing the 3 segment selectors")
    }
  }

  test("static Sway/Waybar configs carry the CAD workstation layout invariants") {
    val ctx = Context.discover().toOption.get
    val swayConfig = os.read(ctx.dotfilesDir / "config" / "sway" / "config")
    assert(swayConfig.contains("gaps inner 8") || swayConfig.contains("gaps inner 6"), "sway: expected `gaps inner 8`")
    assert(swayConfig.contains("gaps outer 4"), "sway: expected `gaps outer 4`")
    val fxConfig = if os.exists(ctx.dotfilesDir / "config" / "sway" / "fx.conf") then os.read(ctx.dotfilesDir / "config" / "sway" / "fx.conf") else ""
    assert(swayConfig.contains("corner_radius 4") || fxConfig.contains("corner_radius 4") || swayConfig.contains("include fx.conf"), "sway: expected `corner_radius 4` or `include fx.conf`")
    val waybarConfig = os.read(ctx.dotfilesDir / "config" / "waybar" / "config.jsonc")
    assert(waybarConfig.contains("\"modules-left\""), "waybar: missing modules-left segment")
    assert(waybarConfig.contains("\"modules-center\""), "waybar: missing modules-center segment")
    assert(waybarConfig.contains("\"modules-right\""), "waybar: missing modules-right segment")
  }

  test("ThemeEngine.run with rotate mode") {
    withIsolatedContext { ctx =>
      val res = ThemeEngine.run(ctx, List("matriz", "--rotate"))
      assert(res.isRight)
    }
  }

  test("ThemeEngine.getActivePalette returns valid palette") {
    withIsolatedContext { ctx =>
      ThemeEngine.run(ctx, List("matriz"))
      val p = ThemeEngine.getActivePalette(ctx)
      assert(p.name.nonEmpty)
      assert(p.base.nonEmpty)
    }
  }

  test("ThemeEngine renders sway colors.conf correctly") {
    withIsolatedContext { ctx =>
      ThemeEngine.run(ctx, List("matriz"))
      val colorsFile = ctx.configDir / "sway" / "colors.conf"
      assert(os.exists(colorsFile))
      val content = os.read(colorsFile)
      assert(content.contains("client.focused"))
    }
  }

  test("ThemeEngine renders swaylock config") {
    withIsolatedContext { ctx =>
      ThemeEngine.run(ctx, List("matriz"))
      val lockConfig = ctx.configDir / "swaylock" / "config"
      assert(os.exists(lockConfig))
      val content = os.read(lockConfig)
      assert(content.contains("font"))
    }
  }
}
