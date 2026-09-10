package polyomino

import polyomino.dotfiles.autotiling.AutotilingDaemon
import polyomino.dotfiles.context.Context
import munit.FunSuite

class AutotilingSuite extends FunSuite:
  test("calculateSplit selects horizontal split when window is wider than tall"):
    val tree = ujson.Obj(
      "nodes" -> ujson.Arr(
        ujson.Obj(
          "focused" -> ujson.Bool(true),
          "type" -> "con",
          "rect" -> ujson.Obj("x" -> 0, "y" -> 0, "width" -> 1200, "height" -> 600)
        )
      ),
      "floating_nodes" -> ujson.Arr(),
      "layout" -> "splith"
    )
    assertEquals(AutotilingDaemon.calculateSplit(tree), Some("split h"))

  test("calculateSplit selects vertical split when window is taller than wide"):
    val tree = ujson.Obj(
      "nodes" -> ujson.Arr(
        ujson.Obj(
          "focused" -> ujson.Bool(true),
          "type" -> "con",
          "rect" -> ujson.Obj("x" -> 0, "y" -> 0, "width" -> 400, "height" -> 900)
        )
      ),
      "floating_nodes" -> ujson.Arr(),
      "layout" -> "splitv"
    )
    assertEquals(AutotilingDaemon.calculateSplit(tree), Some("split v"))

  test("shouldAutotile returns false for floating windows"):
    val floatingWindow = ujson.Obj(
      "focused" -> ujson.Bool(true),
      "type" -> "floating_con",
      "rect" -> ujson.Obj("x" -> 10, "y" -> 10, "width" -> 400, "height" -> 300)
    )
    assertEquals(AutotilingDaemon.shouldAutotile(floatingWindow, None), false)

  test("shouldAutotile returns false when parent layout is tabbed or stacked"):
    val normalWindow = ujson.Obj(
      "focused" -> ujson.Bool(true),
      "type" -> "con",
      "rect" -> ujson.Obj("x" -> 0, "y" -> 0, "width" -> 800, "height" -> 600)
    )
    val tabbedParent = ujson.Obj("layout" -> "tabbed")
    val stackedParent = ujson.Obj("layout" -> "stacked")
    val splithParent = ujson.Obj("layout" -> "splith")

    assertEquals(AutotilingDaemon.shouldAutotile(normalWindow, Some(tabbedParent)), false)
    assertEquals(AutotilingDaemon.shouldAutotile(normalWindow, Some(stackedParent)), false)
    assertEquals(AutotilingDaemon.shouldAutotile(normalWindow, Some(splithParent)), true)

  test("findFocusedWindow locates deeply nested focused container"):
    val nestedTree = ujson.Obj(
      "nodes" -> ujson.Arr(
        ujson.Obj(
          "focused" -> ujson.Bool(false),
          "nodes" -> ujson.Arr(
            ujson.Obj(
              "focused" -> ujson.Bool(true),
              "id" -> 42,
              "type" -> "con",
              "rect" -> ujson.Obj("x" -> 0, "y" -> 0, "width" -> 1000, "height" -> 500)
            )
          )
        )
      )
    )
    val found = AutotilingDaemon.findFocusedWindow(nestedTree, None)
    assert(found.isDefined)
    assertEquals(found.get._1("id").num.toInt, 42)

  test("AutotilingDaemon.run returns Left when SWAYSOCK is unset"):
    val tempDir = os.temp.dir(prefix = "polyomino-autotile-test-")
    try
      val ctx = Context.isolated(tempDir, dotfilesDir = os.pwd)
      val res = AutotilingDaemon.run(ctx, Nil)
      assert(res.isLeft)
    finally
      os.remove.all(tempDir)
