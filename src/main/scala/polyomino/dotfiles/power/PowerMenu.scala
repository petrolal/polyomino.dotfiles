package polyomino.dotfiles.power

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}
import polyomino.dotfiles.theme.{Palette, ThemeEngine}

import scala.util.control.NonFatal

/** `power-menu` — a Tetris-themed power / exit modal.
  *
  * A random flat 2D tetromino falls for exactly 30 seconds down a portrait well
  * (with a randomised debris stack heaped at the base) across three landing lanes:
  *   - left   : reboot            (`systemctl reboot`)
  *   - centre : shutdown [default](`systemctl poweroff`)
  *   - right  : lock + suspend    (`swaylock -f` then `systemctl suspend`)
  *
  * Controls: Left/Right or `h`/`l` switch lanes; Enter / Space / `j` hard-drop
  * (run the selected action immediately); `Esc` cancels and runs nothing. Down
  * arrow is deliberately unbound so a stray mouse-wheel scroll can't fire it. If the
  * user never touches it, the piece lands in the centre lane and shutdown fires.
  *
  * Rendering is raw ANSI; the terminal is put into non-canonical mode by shelling
  * out to `stty` (via os-lib). No new dependencies, so it stays inside the
  * GraalVM `--no-fallback` / no-reflection constraints of the rest of the binary.
  *
  * The Sway rule makes the window a fullscreen translucent pane, so it is its own
  * dark backdrop on the output it opens on; while it is up the binary DPMS-off's
  * every other active output and turns them back on when it exits.
  */
object PowerMenu:

  enum Lane(val label: String, val glyph: String):
    case Reboot   extends Lane("REBOOT", "↻")
    case Shutdown extends Lane("SHUTDOWN", "⏻")
    case Suspend  extends Lane("LOCK + SUSPEND", "☾")

  private val CountdownSecs = 30.0

  /** Commands run (in order) for a chosen lane. Pure — exercised by tests. */
  def actionFor(lane: Lane): Seq[Seq[String]] = lane match
    case Lane.Reboot   => Seq(Seq("systemctl", "reboot"))
    case Lane.Shutdown => Seq(Seq("systemctl", "poweroff"))
    case Lane.Suspend  => Seq(Seq("swaylock", "-f"), Seq("systemctl", "suspend"))

  def run(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    if args.contains("--help") || args.contains("-h") then
      println(HelpText)
      Right(())
    else
      val theme = Theme.from(ThemeEngine.getActivePalette(ctx))
      if args.contains("--dry-run") || args.contains("--print") then
        val (cols, rows) = termSize()
        val g = Geom(cols, rows)
        Console.out.print(Renderer.frame(State.initialFor(g), g, theme, CountdownSecs))
        Console.out.println("[0m")
        Right(())
      else
        try
          interactive(theme)
          Right(())
        catch
          case NonFatal(e) =>
            restoreTerminal()
            Left(CommandError(s"power-menu failed: ${e.getMessage}"))
        finally restoreTerminal()

  // ── interactive loop ──────────────────────────────────────────────────────

  private def interactive(theme: Theme): Unit =
    setupTerminal()
    val (cols, rows) = termSize()
    val g = Geom(cols, rows)
    var st = State.initialFor(g)
    val start = System.nanoTime()
    var pending = List.empty[Int]
    var escSince = 0L
    var outcome: Outcome = Outcome.Pending

    while outcome == Outcome.Pending do
      val frameStart = System.nanoTime()
      val elapsed = (frameStart - start) / 1e9
      val remaining = math.max(0.0, CountdownSecs - elapsed)

      pending = pending ++ readAvailable()
      val (events, rest, es2) = InputParser.parse(pending, escSince, frameStart)
      pending = rest
      escSince = es2

      for ev <- events do ev match
        case Input.Left   => st = st.copy(laneIdx = math.max(0, st.laneIdx - 1))
        case Input.Right  => st = st.copy(laneIdx = math.min(2, st.laneIdx + 1))
        case Input.Drop   => outcome = Outcome.Fire(Lane.values(st.laneIdx))
        case Input.Cancel => outcome = Outcome.Cancelled

      if outcome == Outcome.Pending && remaining <= 0.0 then
        outcome = Outcome.Fire(Lane.values(st.laneIdx))

      val targetX  = g.laneCenterX(st.laneIdx).toDouble
      val groundY  = g.groundYFor(st.stackMaxRows, st.sprite.length)
      val progress = 1.0 - remaining / CountdownSecs
      st = st.copy(
        pieceX = st.pieceX + (targetX - st.pieceX) * 0.35,
        pieceY = g.topY + (groundY - g.topY) * progress
      )

      Console.out.print(Renderer.frame(st, g, theme, remaining))
      Console.out.flush()

      val tookMs = (System.nanoTime() - frameStart) / 1e6
      val napMs = 30.0 - tookMs
      if napMs > 0 then Thread.sleep(napMs.toLong)

    outcome match
      case Outcome.Fire(lane) =>
        slam(st, g, theme)
        restoreTerminal()
        println(s"[1;33m[polyomino power-menu][0m ${lane.label} — executing…")
        execute(lane)
      case _ =>
        restoreTerminal()
        println(s"[1;36m[polyomino power-menu][0m cancelled — no action taken.")

  /** Short hard-drop animation before the action fires. */
  private def slam(st0: State, g: Geom, theme: Theme): Unit =
    var st = st0
    val groundY = g.groundYFor(st.stackMaxRows, st.sprite.length)
    var k = 0
    while k < 6 do
      st = st.copy(pieceY = st.pieceY + (groundY - st.pieceY) * 0.6)
      Console.out.print(Renderer.frame(st, g, theme, 0.0))
      Console.out.flush()
      Thread.sleep(22)
      k += 1

  private def execute(lane: Lane): Unit =
    for cmd <- actionFor(lane) do
      if cmd.headOption.contains("swaylock") && !commandExists("swaylock") then ()
      else
        try os.proc(cmd).call(check = false, stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
        catch case NonFatal(_) => ()

  // ── terminal plumbing ─────────────────────────────────────────────────────

  @volatile private var savedStty: Option[String] = None
  @volatile private var restored = true
  @volatile private var dimmedOutputs: List[String] = Nil

  private def setupTerminal(): Unit =
    savedStty =
      try Some(os.proc("stty", "-g").call(stdin = os.Inherit, stderr = os.Pipe).out.trim())
      catch case NonFatal(_) => None
    try os.proc("stty", "-echo", "-icanon", "min", "0", "time", "0").call(stdin = os.Inherit, stderr = os.Pipe)
    catch case NonFatal(_) => ()
    restored = false
    Runtime.getRuntime.addShutdownHook(new Thread(() => restoreTerminal()))
    applyBackdrop()
    grabFocus()
    Console.out.print("[?1049h[?25l[2J")
    Console.out.flush()

  /** Black out every screen except the one the modal opens on: the Sway rule
    * makes this window a fullscreen translucent pane on its own output, so the
    * other active outputs are DPMS-off'd for the duration and turned back on in
    * [[clearBackdrop]] (and via the shutdown hook). No-op on plain X / if
    * `swaymsg` is absent or there is only one active output. */
  private def applyBackdrop(): Unit =
    try
      val json = os.proc("swaymsg", "-t", "get_outputs").call(check = false, stderr = os.Pipe).out.trim()
      val others = ujson
        .read(json)
        .arr
        .iterator
        .map(_.obj)
        .filter(o => o.get("active").exists(_.bool) && !o.get("focused").exists(_.bool))
        .map(_("name").str)
        .toList
      for name <- others do
        os.proc("swaymsg", "output", name, "dpms", "off").call(check = false, stderr = os.Pipe)
      dimmedOutputs = others
    catch case NonFatal(_) => ()

  /** Pull the Sway keyboard focus onto this modal. The `for_window … focus` rule
    * loses the race with the fullscreen toggle when the window opens on an output
    * the pointer isn't over (`focus_follows_mouse no`), so ask again from inside —
    * the window is mapped by the time this code runs — a few times as it settles. */
  private def grabFocus(): Unit =
    var i = 0
    while i < 3 do
      try os.proc("swaymsg", "[app_id=polyomino-power-menu]", "focus").call(check = false, stderr = os.Pipe)
      catch case NonFatal(_) => ()
      i += 1
      if i < 3 then try Thread.sleep(60) catch case NonFatal(_) => ()

  private def clearBackdrop(): Unit =
    if dimmedOutputs.nonEmpty then
      val names = dimmedOutputs
      dimmedOutputs = Nil
      for name <- names do
        try os.proc("swaymsg", "output", name, "dpms", "on").call(check = false, stderr = os.Pipe)
        catch case NonFatal(_) => ()

  private def restoreTerminal(): Unit = synchronized {
    if !restored then
      restored = true
      clearBackdrop()
      Console.out.print("[?25h[?1049l[0m")
      Console.out.flush()
      savedStty match
        case Some(s) => try os.proc("stty", s).call(stdin = os.Inherit, stderr = os.Pipe) catch case NonFatal(_) => ()
        case None    => try os.proc("stty", "sane").call(stdin = os.Inherit, stderr = os.Pipe) catch case NonFatal(_) => ()
  }

  private def readAvailable(): List[Int] =
    try
      val n = System.in.available()
      if n <= 0 then Nil
      else
        val b = new Array[Byte](math.min(n, 64))
        val r = System.in.read(b)
        if r <= 0 then Nil else b.take(r).map(_ & 0xff).toList
    catch case NonFatal(_) => Nil

  private def termSize(): (Int, Int) =
    try
      val parts = os.proc("stty", "size").call(stdin = os.Inherit, stderr = os.Pipe).out.trim().split("\\s+")
      // use the real pty size — flooring it wider than the window makes every
      // line wrap and the whole layout drifts off-centre
      (math.max(34, parts(1).toInt), math.max(20, parts(0).toInt))
    catch case NonFatal(_) => (72, 40)

  private def commandExists(cmd: String): Boolean =
    try os.proc("which", cmd).call(check = false).exitCode == 0
    catch case NonFatal(_) => false

  // ── model ─────────────────────────────────────────────────────────────────

  private enum Input:
    case Left, Right, Drop, Cancel

  private enum Outcome:
    case Pending
    case Cancelled
    case Fire(lane: Lane)

  /** One settled cell of the debris stack: column/row in cell units measured
    * from the bottom-left of the well interior, plus which block colour it uses. */
  private final case class Debris(cellX: Int, cellY: Int, colorIdx: Int)

  private final case class State(
      laneIdx: Int,
      pieceX: Double,
      pieceY: Double,
      sprite: Vector[String],
      stack: Vector[Debris],
      stackMaxRows: Int
  )
  private object State:
    def initialFor(g: Geom): State =
      val rnd   = new scala.util.Random()
      val shape = scaleShape(Tetrominoes(rnd.nextInt(Tetrominoes.length)))

      // per-column debris heights (in cells) — mostly low with the odd tower and
      // a few empty columns, so the falling piece has somewhere to slot in
      val cap = math.max(2, (g.wellH - 4) / CellH)
      val heights = Vector.tabulate(g.wellWCells) { _ =>
        val r = rnd.nextInt(100)
        val h = if r < 22 then 0 else if r < 72 then 1 + rnd.nextInt(3) else 3 + rnd.nextInt(4)
        math.min(h, cap)
      }
      val stack =
        for
          cx <- (0 until g.wellWCells).toVector
          cy <- 0 until heights(cx)
        yield Debris(cx, cy, rnd.nextInt(BlockColors))

      State(1, g.laneCenterX(1).toDouble, g.topY.toDouble, shape, stack, heights.max)

  /** The well fills this share of the parent box; the clock + labels get the rest. */
  private val WellFill = 0.72

  private final case class Geom(cols: Int, rows: Int):
    // interior width: ~WellFill of the parent, snapped to a whole number of
    // cells that divides evenly into three landing lanes
    val laneCells  = math.max(2, ((cols * WellFill).toInt / CellW) / 3)
    val wellWCells = laneCells * 3
    val innerW     = wellWCells * CellW
    // centre the whole box (interior + its two border columns) in the parent
    val wellLeft   = math.max(1, (cols - (innerW + 2)) / 2)
    val wellRight  = wellLeft + innerW + 1

    // height: ~WellFill of the parent, biased slightly upward so the lane
    // labels/help fit below
    private val wellRows = math.max(12, (rows * WellFill).toInt)
    private val slack    = math.max(0, rows - wellRows)
    val wellTop    = math.max(2, (slack * 2) / 5)
    val wellBottom = math.min(rows - 5, wellTop + wellRows - 1)
    val wellH      = wellBottom - wellTop + 1

    val interiorTopY    = wellTop + 1
    val interiorBottomY = wellBottom - 1
    def cellX(cx: Int): Int      = wellLeft + 1 + cx * CellW
    def laneCenterX(i: Int): Int = cellX(i * laneCells + laneCells / 2)
    val topY = interiorTopY
    def groundYFor(stackRows: Int, pieceRows: Int): Int =
      math.max(topY + 1, interiorBottomY - stackRows * CellH - pieceRows + 1)

  // ── input parser ──────────────────────────────────────────────────────────

  private object InputParser:
    /** Consume as many complete key events as possible from the byte buffer.
      *
      * A lone `ESC` (0x1B) is only reported as [[Input.Cancel]] once ~60 ms have
      * elapsed with nothing following it, so it is never mistaken for the `ESC`
      * that prefixes an arrow-key CSI sequence. Returns the events produced, the
      * still-unparsed bytes, and the "esc pending since" timestamp to carry over.
      */
    def parse(buf0: List[Int], escSince0: Long, now: Long): (List[Input], List[Int], Long) =
      var buf = buf0
      var escSince = escSince0
      val out = scala.collection.mutable.ListBuffer.empty[Input]
      var continue = true
      while continue && buf.nonEmpty do
        buf.head match
          case 3 => // Ctrl-C — safety hatch, treated as cancel
            out += Input.Cancel
            buf = buf.tail
          case 27 =>
            buf.tail match
              case Nil =>
                if escSince == 0L then escSince = now
                if (now - escSince) / 1e6 > 60 then
                  out += Input.Cancel
                  buf = Nil
                  escSince = 0L
                continue = false
              case s :: rest if s == '['.toInt || s == 'O'.toInt =>
                rest match
                  case Nil => continue = false // wait for the final byte
                  case f :: rest2 =>
                    f.toChar match
                      case 'C' => out += Input.Right
                      case 'D' => out += Input.Left
                      // NB: no 'B' (Down arrow) -> Drop. A mouse wheel scroll
                      // sends ESC[B in the alt-screen, and mapping it to a
                      // hard-drop meant a stray scroll fired the power action.
                      case _   => ()
                    buf = rest2
                    escSince = 0L
              case _ => // ESC followed by an unrelated byte — a real Esc press
                out += Input.Cancel
                buf = buf.tail
                escSince = 0L
          case b =>
            b.toChar match
              case 'h' | 'H'         => out += Input.Left
              case 'l' | 'L'         => out += Input.Right
              case 'j' | 'J'         => out += Input.Drop
              case '\r' | '\n' | ' ' => out += Input.Drop
              case _                 => ()
            buf = buf.tail
            escSince = 0L
      (out.toList, buf, escSince)

  // ── theme ─────────────────────────────────────────────────────────────────

  private final case class Theme(
      bg: String, well: String, floor: String, dim: String, help: String,
      ghost: String, piece: String, blocks: Vector[String],
      reboot: String, shutdown: String, suspend: String
  ):
    def laneColor(l: Lane): String = l match
      case Lane.Reboot   => reboot
      case Lane.Shutdown => shutdown
      case Lane.Suspend  => suspend

  private object Theme:
    private type Rgb = (Int, Int, Int)

    def from(p: Palette): Theme =
      val acc  = rgb(p.accent, (235, 180, 52))
      val base = rgb(p.base, (15, 17, 23))
      val txt  = rgb(p.text, (248, 250, 252))
      val blockRgbs = Vector(
        rgb(p.accent, (235, 180, 52)),
        rgb(p.red, (239, 68, 68)),
        rgb(p.green, (16, 185, 129)),
        rgb(p.yellow, (245, 158, 11)),
        rgb(p.blue, (96, 165, 250)),
        mix(rgb(p.red, (239, 68, 68)), rgb(p.blue, (96, 165, 250)), 0.5)
      )
      Theme(
        bg        = bgSeq(base),
        well      = fgSeq(mix(txt, base, 0.5)),
        floor     = fgSeq(mix(txt, base, 0.55)),
        dim       = fgSeq(mix(txt, base, 0.72)),
        help      = fgSeq(mix(txt, base, 0.6)),
        ghost     = fgSeq(mix(acc, base, 0.7)),
        piece     = fgSeq(acc),
        blocks    = blockRgbs.map(c => fgSeq(mix(c, base, 0.18))),
        reboot    = fgSeq(rgb(p.red, (239, 68, 68))),
        shutdown  = fgSeq(acc),
        suspend   = fgSeq(rgb(p.green, (16, 185, 129)))
      )

    private def rgb(hex: String, fallback: Rgb): Rgb =
      try
        val h = hex.trim.stripPrefix("#")
        (Integer.parseInt(h.substring(0, 2), 16),
         Integer.parseInt(h.substring(2, 4), 16),
         Integer.parseInt(h.substring(4, 6), 16))
      catch case NonFatal(_) => fallback

    private def clamp(i: Int): Int = math.max(0, math.min(255, i))
    private def darken(c: Rgb, f: Double): Rgb =
      (clamp((c._1 * (1 - f)).toInt), clamp((c._2 * (1 - f)).toInt), clamp((c._3 * (1 - f)).toInt))
    private def mix(a: Rgb, b: Rgb, t: Double): Rgb =
      (clamp((a._1 + (b._1 - a._1) * t).toInt),
       clamp((a._2 + (b._2 - a._2) * t).toInt),
       clamp((a._3 + (b._3 - a._3) * t).toInt))
    private def fgSeq(c: Rgb): String = s"[38;2;${c._1};${c._2};${c._3}m"
    private def bgSeq(c: Rgb): String = s"[48;2;${c._1};${c._2};${c._3}m"

  // ── renderer ──────────────────────────────────────────────────────────────

  // ── tetrominoes ───────────────────────────────────────────────────────────

  /** Cell size in terminal chars — 2×1 reads roughly square given font aspect. */
  private val CellW = 2
  private val CellH = 1

  /** The seven tetrominoes, one cell per glyph, `#` filled / space empty. */
  private val Tetrominoes: Vector[Vector[String]] = Vector(
    Vector("####"),           // I
    Vector("##", "##"),       // O
    Vector("###", " # "),     // T
    Vector(" ##", "## "),     // S
    Vector("## ", " ##"),     // Z
    Vector("#  ", "###"),     // J
    Vector("  #", "###")      // L
  )

  /** Blow a compact shape up to `CellW`×`CellH` solid blocks. */
  private def scaleShape(shape: Vector[String]): Vector[String] =
    shape.flatMap { row =>
      val wide = row.flatMap(c => (if c == ' ' then " " else "█") * CellW)
      Vector.fill(CellH)(wide)
    }

  /** Number of distinct debris block colours [[Theme.blocks]] provides. */
  private val BlockColors = 6

  private object Renderer:
    def frame(st: State, g: Geom, th: Theme, remaining: Double): String =
      val buf       = new Buf(g.cols, g.rows)
      val spriteW   = st.sprite.map(_.length).max
      val spriteH   = st.sprite.length
      val groundY   = g.groundYFor(st.stackMaxRows, spriteH)
      val midX      = g.wellLeft + 1 + g.innerW / 2

      // the well — a plain rectangular box. There is no separate countdown: the
      // falling piece's height above the floor is the timer.
      drawBox(buf, g.wellLeft, g.wellTop, g.wellRight, g.wellBottom, th.well)

      // random debris heaped at the base, in mixed block colours
      for d <- st.stack do
        val x = g.cellX(d.cellX)
        val y = g.interiorBottomY - d.cellY * CellH
        buf.put(x, y, "█" * CellW, th.blocks(d.colorIdx % th.blocks.length))

      // lane markers + labels under the well; the lane the piece is over glows.
      // The well is narrow, so the centre label sits on its own row to avoid
      // colliding with the flanking two.
      for i <- 0 to 2 do
        val lane  = Lane.values(i)
        val sel   = i == st.laneIdx
        val lc    = if sel then th.laneColor(lane) else th.dim
        val markW = g.laneCells * CellW - 2
        buf.put(g.laneCenterX(i) - markW / 2, g.wellBottom + 1,
                (if sel then "▀" else "·") * markW, lc)
        val label   = s"${lane.glyph} ${lane.label}"
        val labelRow = if i == 1 then g.wellBottom + 3 else g.wellBottom + 2
        buf.put(g.laneCenterX(i) - label.length / 2, labelRow, label, lc)

      // ghost of where the piece will land in the current lane
      drawSprite(buf, st.sprite, g.laneCenterX(st.laneIdx) - spriteW / 2, groundY, th.ghost, '▒')

      // the falling piece — its x position is the choice, its y is the clock
      drawSprite(buf, st.sprite, math.round(st.pieceX).toInt - spriteW / 2,
                 math.round(st.pieceY).toInt, th.piece, '█')

      val help = "←/→  move        ↵  drop        esc  cancel"
      buf.put(math.max(0, midX - help.length / 2), g.rows - 1, help, th.help)

      buf.render(th.bg)

    /** A rectangular border box with square corners. */
    private def drawBox(buf: Buf, x0: Int, y0: Int, x1: Int, y1: Int, color: String): Unit =
      buf.put(x0, y0, "┌" + "─" * math.max(0, x1 - x0 - 1) + "┐", color)
      buf.put(x0, y1, "└" + "─" * math.max(0, x1 - x0 - 1) + "┘", color)
      var y = y0 + 1
      while y < y1 do
        buf.put(x0, y, "│", color)
        buf.put(x1, y, "│", color)
        y += 1

    private def drawSprite(buf: Buf, sprite: Vector[String], x: Int, y: Int,
                           color: String, fill: Char): Unit =
      var dy = 0
      while dy < sprite.length do
        val line = sprite(dy)
        var dx = 0
        while dx < line.length do
          if line.charAt(dx) != ' ' then
            buf.put(x + dx, y + dy, fill.toString, color)
          dx += 1
        dy += 1

  /** Fixed-size character + foreground-colour grid, serialised to one ANSI frame. */
  private final class Buf(cols: Int, rows: Int):
    private val ch = Array.fill(rows, cols)(' ')
    private val fg = Array.fill(rows, cols)("")

    def put(x: Int, y: Int, s: String, color: String): Unit =
      if y >= 0 && y < rows then
        var i = 0
        while i < s.length do
          val cx = x + i
          if cx >= 0 && cx < cols then
            ch(y)(cx) = s.charAt(i)
            fg(y)(cx) = color
          i += 1

    def render(bg: String): String =
      val sb = new StringBuilder(rows * (cols + 16))
      var y = 0
      while y < rows do
        sb.append(s"[${y + 1};1H").append(bg)
        var last = "?"
        var x = 0
        while x < cols do
          val c = fg(y)(x)
          if c != last then
            sb.append(if c.isEmpty then "[39m" else c)
            last = c
          sb.append(ch(y)(x))
          x += 1
        sb.append("[0m")
        y += 1
      sb.toString

  private val HelpText: String =
    """polyomino power-menu — Tetris-themed power / exit modal.
      |
      |A tetromino falls for 30 s over three lanes:
      |  left    reboot             (systemctl reboot)
      |  centre  shutdown [default] (systemctl poweroff)
      |  right   lock + suspend     (swaylock -f ; systemctl suspend)
      |
      |  left/right or h/l   switch lane
      |  enter / space / j   drop now (run the selected action immediately)
      |  esc                 cancel, run nothing
      |
      |If left untouched the piece lands centre and shutdown runs.
      |
      |Flags:
      |  --dry-run   render one frame to stdout and exit (no input, no action)
      |  --help      this text
      |""".stripMargin
