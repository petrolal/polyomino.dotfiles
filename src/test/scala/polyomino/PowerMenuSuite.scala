package polyomino

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.power.PowerMenu
import munit.FunSuite

class PowerMenuSuite extends FunSuite:

  test("actionFor maps each lane to its systemctl / rubik-lock command(s)"):
    assertEquals(PowerMenu.actionFor(PowerMenu.Lane.Reboot), Seq(Seq("systemctl", "reboot")))
    assertEquals(PowerMenu.actionFor(PowerMenu.Lane.Shutdown), Seq(Seq("systemctl", "poweroff")))
    val suspend = PowerMenu.actionFor(PowerMenu.Lane.Suspend)
    assertEquals(suspend.head, Seq("swaylock", "-f"))
    assertEquals(suspend.last, Seq("systemctl", "suspend"))
    assertEquals(PowerMenu.actionFor(PowerMenu.Lane.Lock), Seq(Seq("polyomino-rubik-lock")))

  test("lane layout contains 4 dedicated action chutes"):
    assertEquals(PowerMenu.Lane.values(0), PowerMenu.Lane.Reboot)
    assertEquals(PowerMenu.Lane.values(1), PowerMenu.Lane.Shutdown)
    assertEquals(PowerMenu.Lane.values(2), PowerMenu.Lane.Suspend)
    assertEquals(PowerMenu.Lane.values(3), PowerMenu.Lane.Lock)
    assertEquals(PowerMenu.Lane.values.length, 4)

  test("--dry-run renders one frame and never executes an action"):
    val ctx = Context.discover().toOption.get
    val sink = new java.io.ByteArrayOutputStream()
    val res =
      Console.withOut(new java.io.PrintStream(sink))(PowerMenu.run(ctx, List("--dry-run")))
    assert(res.isRight, s"expected Right, got $res")
    val out = sink.toString
    assert(
      out.contains("REBOOT") && out.contains("SHUTDOWN") && out.contains("SUSPEND") && out.contains("LOCK"),
      "frame should label all four lanes"
    )
    assert(out.contains("move") && out.contains("drop") && out.contains("cancel"),
      "frame should show the controls hint")

  test("--help returns Right without touching the terminal"):
    val ctx = Context.discover().toOption.get
    val sink = new java.io.ByteArrayOutputStream()
    val res =
      Console.withOut(new java.io.PrintStream(sink))(PowerMenu.run(ctx, List("--help")))
    assert(res.isRight)
    assert(sink.toString.contains("power-menu"))
