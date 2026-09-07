package polyomino.dotfiles.power

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}
import polyomino.dotfiles.theme.{Palette, ThemeEngine}

import scala.util.control.NonFatal

/** `power-menu` — a Tetris-themed power / exit modal.
  *
  * A random tetromino falls for exactly 30 seconds down a flat, upright
  * rectangular matrix (portrait — narrow and tall, like a real Tetris board)
  * onto a jagged pile of already-locked pieces heaped along the floor. The
  * piece and every cell of the pile carry the classic Tetris colours (cyan I,
  * yellow O, purple T, green S, red Z, blue J, orange L), drawn as solid
  * two-wide blocks with a darker left column so the grid stays legible.
  *
  * The matrix is split into three landing lanes:
  *   - left   : reboot            (`systemctl reboot`)
  *   - centre : shutdown [default](`systemctl poweroff`)
  *   - right  : lock + suspend    (`swaylock -f` then `systemctl suspend`)
  *
  * Controls: Left/Right or `h`/`l` switch lanes; Enter / Space / `j` hard-drop
  * (run the selected action immediately); `Esc` or `q` cancels and runs nothing. Down
  * arrow is deliberately unbound so a stray mouse-wheel scroll can't fire it. If the
  * user never touches it, the piece lands in the centre lane and shutdown fires.
  *
  * There is no separate countdown: the piece's height above the pile *is* the
  * 30 s timer. The frame, landing ghost and help line are steps on one neutral
  * text→base ramp; gold (the palette accent) is spent only on the lane the
  * piece is currently over — its two glowing dividers, its floor tick and its
  * label below the well.
  *
  * Rendering is raw ANSI; the terminal is put into non-canonical mode by shelling
  * out to `stty` (via os-lib). No new dependencies, so it stays inside the
  * GraalVM `--no-fallback` / no-reflection constraints of the rest of the binary.
  *
  * The Sway rule makes the window a borderless floating pane sized to the whole
  * output — its own dark translucent backdrop on the screen it opens on. Other
  * outputs are left alone. The binary re-grabs keyboard focus and sets opacity
  * once the surface has settled (doing either at map time raced with kitty's
  * focus setup and left the modal unresponsive until clicked).
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
      val groundY  = g.groundYFor(st.stackMaxRows, st.shape.length)
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
    val groundY = g.groundYFor(st.stackMaxRows, st.shape.length)
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

  private def setupTerminal(): Unit =
    savedStty =
      try Some(os.proc("stty", "-g").call(stdin = os.Inherit, stderr = os.Pipe).out.trim())
      catch case NonFatal(_) => None
    try os.proc("stty", "-echo", "-icanon", "min", "0", "time", "0").call(stdin = os.Inherit, stderr = os.Pipe)
    catch case NonFatal(_) => ()
    restored = false
    Runtime.getRuntime.addShutdownHook(new Thread(() => restoreTerminal()))
    grabFocus()
    Console.out.print("[?1049h[?25l[2J")
    Console.out.flush()

  /** Pull the Sway keyboard focus onto this modal, then set its opacity.
    *
    * `focus_follows_mouse no` plus the pointer being on another output means the
    * modal can map without keyboard focus; and doing the opacity change at map
    * time (via `for_window`) churned kitty's surface enough that it dropped its
    * initial `wl_keyboard.enter` and stayed dead until clicked. So both happen
    * here instead, once the surface has settled — the window is already mapped by
    * the time this runs. No-op if `swaymsg` is absent. */
  private def grabFocus(): Unit =
    var i = 0
    while i < 4 do
      try os.proc("swaymsg", "[app_id=polyomino-power-menu]", "focus").call(check = false, stderr = os.Pipe)
      catch case NonFatal(_) => ()
      i += 1
      if i < 4 then try Thread.sleep(70) catch case NonFatal(_) => ()
    try os.proc("swaymsg", "[app_id=polyomino-power-menu]", "opacity", "0.92").call(check = false, stderr = os.Pipe)
    catch case NonFatal(_) => ()

  private def restoreTerminal(): Unit = synchronized {
    if !restored then
      restored = true
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

  /** One locked cell of the pile: column and row in *cell* units, the row
    * counted up from the well floor. `colorIdx` is which tetromino this cell
    * came from, so the pile reads as a mosaic of past pieces. */
  private final case class Debris(cellX: Int, cellY: Int, colorIdx: Int)

  private final case class State(
      laneIdx: Int,
      pieceX: Double,
      pieceY: Double,
      pieceIdx: Int,
      stack: Vector[Debris],
      stackMaxRows: Int
  ):
    def shape: Vector[String] = Tetrominoes(pieceIdx)

  private object State:
    def initialFor(g: Geom): State =
      val rnd = new scala.util.Random()
      val idx = rnd.nextInt(Tetrominoes.length)

      // a jagged Tetris pile heaped along the floor — every column a few cells,
      // some stacked higher, each locked cell a random past-piece colour
      val cap = math.max(2, math.min(g.rowsInner - 4, 9))
      val heights = Vector.tabulate(g.wellCols) { _ =>
        val r = rnd.nextInt(100)
        val h =
          if r < 10 then 0
          else if r < 68 then 1 + rnd.nextInt(math.max(1, cap / 2))
          else cap / 2 + rnd.nextInt(math.max(1, cap - cap / 2 + 1))
        math.min(h, cap)
      }
      val stack =
        for
          cx <- (0 until g.wellCols).toVector
          cy <- 0 until heights(cx)
        yield Debris(cx, cy, rnd.nextInt(Tetrominoes.length))

      State(1, g.laneCenterX(1).toDouble, g.topY.toDouble, idx, stack, heights.max)

  /** The well takes this share of the parent's *width* — kept narrow so the
    * matrix stands upright (portrait), like a real Tetris board. Its height is
    * whatever's left after the lane ticks + labels + help line. */
  private val WellFillW = 0.28

  private final case class Geom(cols: Int, rows: Int):
    // one Tetris cell is CellW×CellH chars; the interior is a whole number of
    // cells that splits evenly into three landing lanes
    val laneCells = math.max(3, ((cols * WellFillW).toInt / CellW) / 3)
    val wellCols  = laneCells * 3
    val innerW    = wellCols * CellW
    val wellLeft  = math.max(1, (cols - (innerW + 2)) / 2)
    val wellRight = wellLeft + innerW + 1

    val wellTop         = 2
    val wellBottom      = math.max(wellTop + 8, rows - 6)
    val interiorTopY    = wellTop + 1
    val interiorBottomY = wellBottom - 1
    val rowsInner       = interiorBottomY - interiorTopY + 1
    val wellH           = wellBottom - wellTop + 1

    def cellX(cx: Int): Int      = wellLeft + 1 + cx * CellW
    def laneCenterX(i: Int): Int = cellX(i * laneCells + laneCells / 2)
    val topY = interiorTopY

    /** Char row for the top of a piece `pieceRows` cells tall coming to rest on
      * a pile `stackRows` cells high. */
    def groundYFor(stackRows: Int, pieceRows: Int): Int =
      math.max(interiorTopY, interiorBottomY - (stackRows + pieceRows) * CellH + 1)

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
              case 'q' | 'Q'         => out += Input.Cancel
              case _                 => ()
            buf = buf.tail
            escSince = 0L
      (out.toList, buf, escSince)

  // ── theme ─────────────────────────────────────────────────────────────────

  /** The frame + ghost + help are steps on one neutral text→base ramp; the
    * seven pieces carry the classic Tetris colours (each with a darker left
    * edge for a hint of a grid); `accent` (palette gold) marks the live lane. */
  private final case class Theme(
      bg: String,
      wall: String, floor: String, divider: String, dividerHot: String,
      ghost: String, dim: String, help: String, accent: String,
      pieceFg: Vector[String], pieceEdge: Vector[String],
      stackFg: Vector[String], stackEdge: Vector[String]
  )

  private object Theme:
    private type Rgb = (Int, Int, Int)

    /** Classic tetromino colours, in `Tetrominoes` order (I O T S Z J L). Kept
      * literal so the modal reads unmistakably as Tetris — the backdrop, frame
      * and live-lane accent still come from the system palette. */
    private val Classic: Vector[Rgb] = Vector(
      (0, 209, 214),   // I  cyan
      (242, 201, 76),  // O  yellow
      (168, 85, 247),  // T  purple
      (39, 201, 108),  // S  green
      (239, 68, 68),   // Z  red
      (59, 130, 246),  // J  blue
      (245, 158, 66)   // L  orange
    )

    def from(p: Palette): Theme =
      val acc  = rgb(p.accent, (235, 180, 52))
      val base = rgb(p.base, (15, 17, 23))
      val txt  = rgb(p.text, (248, 250, 252))
      def tone(t: Double): String = fgSeq(mix(txt, base, t))
      Theme(
        bg         = bgSeq(base),
        wall       = tone(0.55),
        floor      = tone(0.5),
        divider    = tone(0.82),
        dividerHot = fgSeq(mix(acc, base, 0.3)),
        ghost      = tone(0.7),
        dim        = tone(0.72),
        help       = tone(0.6),
        accent     = fgSeq(acc),
        pieceFg    = Classic.map(c => fgSeq(mix(c, txt, 0.1))),
        pieceEdge  = Classic.map(c => fgSeq(mix(c, base, 0.5))),
        stackFg    = Classic.map(c => fgSeq(mix(c, base, 0.3))),
        stackEdge  = Classic.map(c => fgSeq(mix(c, base, 0.62)))
      )

    private def rgb(hex: String, fallback: Rgb): Rgb =
      try
        val h = hex.trim.stripPrefix("#")
        (Integer.parseInt(h.substring(0, 2), 16),
         Integer.parseInt(h.substring(2, 4), 16),
         Integer.parseInt(h.substring(4, 6), 16))
      catch case NonFatal(_) => fallback

    private def clamp(i: Int): Int = math.max(0, math.min(255, i))
    private def mix(a: Rgb, b: Rgb, t: Double): Rgb =
      (clamp((a._1 + (b._1 - a._1) * t).toInt),
       clamp((a._2 + (b._2 - a._2) * t).toInt),
       clamp((a._3 + (b._3 - a._3) * t).toInt))
    private def fgSeq(c: Rgb): String = s"[38;2;${c._1};${c._2};${c._3}m"
    private def bgSeq(c: Rgb): String = s"[48;2;${c._1};${c._2};${c._3}m"

  // ── tetrominoes ───────────────────────────────────────────────────────────

  /** One Tetris cell is `CellW`×`CellH` chars: a solid two-wide block, one row
    * tall, its left column drawn a shade darker so the grid stays legible. */
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

  private object Renderer:
    def frame(st: State, g: Geom, th: Theme, remaining: Double): String =
      val buf     = new Buf(g.cols, g.rows)
      val shapeW  = st.shape.map(_.length).max
      val shapeH  = st.shape.length
      val pxW     = shapeW * CellW
      val groundY = g.groundYFor(st.stackMaxRows, shapeH)

      // upright rectangular matrix. There is no separate countdown: the falling
      // piece's height above the pile is the 30 s timer.
      drawWell(buf, g, th, st.laneIdx)

      // faint lane dividers, only in the open air above the pile; the pair
      // bounding the live lane glow
      val pileTopY = g.interiorBottomY - st.stackMaxRows * CellH
      for k <- 1 to 2 do
        val dx  = g.cellX(k * g.laneCells)
        val hot = k == st.laneIdx || k - 1 == st.laneIdx
        var y   = g.wellTop + 1
        while y <= pileTopY do
          buf.put(dx, y, "┊", if hot then th.dividerHot else th.divider)
          y += 1

      // the accumulated pile — a mosaic of past pieces heaped along the floor
      for d <- st.stack do
        drawBlock(buf, g.cellX(d.cellX), g.interiorBottomY - d.cellY * CellH,
                  th.stackFg(d.colorIdx), th.stackEdge(d.colorIdx))

      // where the piece will land in the current lane
      drawShape(buf, st.shape, g.laneCenterX(st.laneIdx) - pxW / 2, groundY,
                th.ghost, th.ghost, "░")

      // the live piece — x is the choice, y is the clock
      drawShape(buf, st.shape, math.round(st.pieceX).toInt - pxW / 2,
                math.round(st.pieceY).toInt,
                th.pieceFg(st.pieceIdx), th.pieceEdge(st.pieceIdx), "█")

      drawLanes(buf, g, th, st.laneIdx)

      val help = "←/→  move        ↵  drop        esc / q  cancel"
      buf.put(math.max(0, (g.cols - help.length) / 2), g.rows - 1, help, th.help)

      buf.render(th.bg)

    /** The matrix: square-cornered side rails + floor, open top. */
    private def drawWell(buf: Buf, g: Geom, th: Theme, laneIdx: Int): Unit =
      val span = "─" * math.max(0, g.wellRight - g.wellLeft - 1)
      buf.put(g.wellLeft, g.wellTop, "┌" + span + "┐", th.wall)
      buf.put(g.wellLeft, g.wellBottom, "└" + span + "┘", th.wall)
      var y = g.wellTop + 1
      while y < g.wellBottom do
        buf.put(g.wellLeft, y, "│", th.wall)
        buf.put(g.wellRight, y, "│", th.wall)
        y += 1

    /** Lane ticks right under the floor + the three action labels spread across
      * the full width. The live lane is the only thing drawn in the accent. */
    private def drawLanes(buf: Buf, g: Geom, th: Theme, laneIdx: Int): Unit =
      for i <- 0 to 2 do
        val sel  = i == laneIdx
        val w    = math.max(2, g.laneCells * CellW - 2)
        buf.put(g.laneCenterX(i) - w / 2, g.wellBottom + 1,
                (if sel then "▀" else "─") * w, if sel then th.accent else th.dim)
      val third = math.max(1, g.cols / 3)
      for i <- 0 to 2 do
        val lane  = Lane.values(i)
        val sel   = i == laneIdx
        val label = s"${lane.glyph} ${lane.label}"
        val cx    = third * i + third / 2
        buf.put(math.max(0, cx - label.length / 2), g.wellBottom + 3, label,
                if sel then th.accent else th.dim)

    /** One filled cell: a `CellW`-wide block whose leftmost column is `edge`
      * (a shade darker) and the rest `fg`. */
    private def drawBlock(buf: Buf, x: Int, y: Int, fg: String, edge: String): Unit =
      buf.put(x, y, "█", edge)
      if CellW > 1 then buf.put(x + 1, y, "█" * (CellW - 1), fg)

    private def drawShape(buf: Buf, shape: Vector[String], ox: Int, oy: Int,
                          fg: String, edge: String, glyph: String): Unit =
      var cy = 0
      while cy < shape.length do
        val row = shape(cy)
        var cx = 0
        while cx < row.length do
          if row.charAt(cx) != ' ' then
            val x = ox + cx * CellW
            val y = oy + cy * CellH
            buf.put(x, y, glyph, edge)
            if CellW > 1 then buf.put(x + 1, y, glyph * (CellW - 1), fg)
          cx += 1
        cy += 1

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
      |  esc / q             cancel, run nothing
      |
      |If left untouched the piece lands centre and shutdown runs.
      |
      |Flags:
      |  --dry-run   render one frame to stdout and exit (no input, no action)
      |  --help      this text
      |""".stripMargin
