package polyomino.dotfiles.power

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}
import polyomino.dotfiles.theme.{Palette, ThemeEngine}

import scala.util.control.NonFatal

/** `power-menu` — Bento-card Tetris workstation power & session modal.
  *
  * Strictly adheres to the signature Polyomino bento-card aesthetic:
  *   - Outer floating dialog with 1px violet borders and frosted glass background
  *   - Integrated top header bar with `[ ⮽ ] [ POLYOMINO // POWER CONTROL ]` & `[ TIMEOUT: 30s ]`
  *   - Faint 1px square grid matrix with solid vertical guide rails
  *   - Three dedicated action chutes with dynamic glowing landing target wells:
  *       - Lane 1 (Left, Amber)     : REBOOT          (`systemctl reboot`)
  *       - Lane 2 (Center, Red)     : SHUTDOWN        (`systemctl poweroff`)
  *       - Lane 3 (Right, Violet)   : LOCK + SUSPEND  (`swaylock -f` ; `systemctl suspend`)
  *   - Monospaced tabular status footer:
  *       `[ NORMAL ]  h/l (←/→) Lane  •  k (↑) Rotate  •  j/Enter Drop & Confirm  •  q/Esc Cancel`
  *   - Lock flash (100ms), line dissolve (200ms), and GAME OVER confirmation window
  */
object PowerMenu:

  enum Lane(val label: String, val glyph: String, val index: Int):
    case Reboot   extends Lane("REBOOT", "↻", 0)
    case Shutdown extends Lane("SHUTDOWN", "⏻", 1)
    case Suspend  extends Lane("LOCK + SUSPEND", "☾", 2)

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
        Console.out.println("\u001b[0m")
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
    val startTime = System.nanoTime()
    var pending = List.empty[Int]
    var escSince = 0L
    var outcome: Outcome = Outcome.Pending

    while outcome == Outcome.Pending do
      val now = System.nanoTime()
      val elapsed = (now - startTime) / 1e9
      val remaining = math.max(0.0, CountdownSecs - elapsed)

      pending = pending ++ readAvailable()
      val (events, rest, es2) = InputParser.parse(pending, escSince, now)
      pending = rest
      escSince = es2

      st.phase match
        case Phase.Playing =>
          for ev <- events do
            ev match
              case Input.Left =>
                val nextX = st.cellX - 1
                if st.fits(st.shape, nextX, math.floor(st.pieceY).toInt, g) then
                  beep()
                  st = st.copy(cellX = nextX)
              case Input.Right =>
                val nextX = st.cellX + 1
                if st.fits(st.shape, nextX, math.floor(st.pieceY).toInt, g) then
                  beep()
                  st = st.copy(cellX = nextX)
              case Input.Rotate =>
                val rotated = State.rotCW(st.shape)
                val kicks = List(0, -1, 1, -2, 2)
                val curY = math.floor(st.pieceY).toInt
                kicks.find(k => st.fits(rotated, st.cellX + k, curY, g)) match
                  case Some(k) =>
                    beep()
                    st = st.copy(shape = rotated, cellX = st.cellX + k)
                  case None => ()
              case Input.SoftDrop =>
                val ground = st.groundY(st.shape, st.cellX, g)
                beep()
                st = st.copy(pieceY = math.min(ground.toDouble, st.pieceY + 1.0))
              case Input.HardDrop =>
                beep()
                val ground = st.groundY(st.shape, st.cellX, g)
                val lane = Lane.values(g.laneForCol(st.cellX, st.shape.head.length))
                st = st.copy(
                  pieceY = ground.toDouble,
                  phase = Phase.LockFlash(lane, now + (100 * 1e6).toLong)
                )
              case Input.JumpLane(idx) =>
                val targetX = idx * g.laneCells + math.max(0, (g.laneCells - st.shape.head.length) / 2)
                val clampedX = math.max(0, math.min(g.wellCols - st.shape.head.length, targetX))
                if st.fits(st.shape, clampedX, math.floor(st.pieceY).toInt, g) then
                  beep()
                  st = st.copy(cellX = clampedX)
              case Input.Cancel =>
                outcome = Outcome.Cancelled

          if st.phase == Phase.Playing then
            val ground = st.groundY(st.shape, st.cellX, g)
            val progress = 1.0 - remaining / CountdownSecs
            val nextY = g.topY + (ground - g.topY) * progress
            st = st.copy(pieceY = nextY)

            // Auto-abort if timeout reached without action
            if remaining <= 0.0 then
              outcome = Outcome.Cancelled
            else if st.pieceY >= ground.toDouble then
              beep()
              val lane = Lane.values(g.laneForCol(st.cellX, st.shape.head.length))
              st = st.copy(
                pieceY = ground.toDouble,
                phase = Phase.LockFlash(lane, now + (100 * 1e6).toLong)
              )

        case Phase.LockFlash(lane, endNanos) =>
          for ev <- events do
            if ev == Input.Cancel then outcome = Outcome.Cancelled
          if now >= endNanos then
            st = st.copy(phase = Phase.Dissolve(lane, now, now + (200 * 1e6).toLong))

        case Phase.Dissolve(lane, _, endNanos) =>
          for ev <- events do
            if ev == Input.Cancel then outcome = Outcome.Cancelled
          if now >= endNanos then
            st = st.copy(phase = Phase.GameOver(lane, now + (2000 * 1e6).toLong))

        case Phase.GameOver(lane, deadline) =>
          for ev <- events do
            ev match
              case Input.Cancel =>
                outcome = Outcome.Cancelled
              case Input.HardDrop =>
                beep()
                outcome = Outcome.Fire(lane)
              case _ => ()

          if outcome == Outcome.Pending && now >= deadline then
            outcome = Outcome.Fire(lane)

      Console.out.print(Renderer.frame(st, g, theme, remaining))
      Console.out.flush()
      Thread.sleep(25)

    outcome match
      case Outcome.Fire(lane) =>
        restoreTerminal()
        println(s"\u001b[1;33m[polyomino power-menu]\u001b[0m ${lane.label} — executing…")
        execute(lane)
      case _ =>
        restoreTerminal()
        println(s"\u001b[1;36m[polyomino power-menu]\u001b[0m cancelled — no action taken.")

  private def execute(lane: Lane): Unit =
    lane match
      case Lane.Suspend =>
        val rubik = os.home / ".local" / "bin" / "polyomino-rubik-lock"
        if os.exists(rubik) then
          try
            os.proc(rubik.toString).spawn(stdout = os.Inherit, stderr = os.Inherit)
            Thread.sleep(400)
          catch case NonFatal(_) => ()
        for cmd <- actionFor(lane) do
          if cmd.headOption.contains("swaylock") && os.exists(rubik) then ()
          else if cmd.headOption.contains("swaylock") && !commandExists("swaylock") then ()
          else
            try os.proc(cmd).call(check = false, stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
            catch case NonFatal(_) => ()
      case _ =>
        for cmd <- actionFor(lane) do
          try os.proc(cmd).call(check = false, stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
          catch case NonFatal(_) => ()

  private def beep(): Unit =
    try
      Console.out.print("\u0007")
      Console.out.flush()
    catch case NonFatal(_) => ()

  // ── terminal plumbing ─────────────────────────────────────────────────────

  @volatile private var savedStty: Option[String] = None
  @volatile private var restored = true
  @volatile private var shutdownHook: Option[Thread] = None

  private def setupTerminal(): Unit =
    savedStty =
      try Some(os.proc("stty", "-g").call(stdin = os.Inherit, stderr = os.Pipe).out.trim())
      catch case NonFatal(_) => None
    try os.proc("stty", "-echo", "-icanon", "min", "0", "time", "0").call(stdin = os.Inherit, stderr = os.Pipe)
    catch case NonFatal(_) => ()
    restored = false
    val hook = new Thread(() => restoreTerminal())
    shutdownHook = Some(hook)
    Runtime.getRuntime.addShutdownHook(hook)
    grabFocus()
    Console.out.print("\u001b[?1049h\u001b[?25l\u001b[2J")
    Console.out.flush()

  private def grabFocus(): Unit =
    var i = 0
    while i < 4 do
      try os.proc("swaymsg", "[app_id=polyomino-power]", "focus").call(check = false, stderr = os.Pipe)
      catch case NonFatal(_) => ()
      try os.proc("swaymsg", "[app_id=polyomino-power-menu]", "focus").call(check = false, stderr = os.Pipe)
      catch case NonFatal(_) => ()
      i += 1
      if i < 4 then try Thread.sleep(60) catch case NonFatal(_) => ()
    try os.proc("swaymsg", "[app_id=polyomino-power]", "opacity", "0.96").call(check = false, stderr = os.Pipe)
    catch case NonFatal(_) => ()
    try os.proc("swaymsg", "[app_id=polyomino-power-menu]", "opacity", "0.96").call(check = false, stderr = os.Pipe)
    catch case NonFatal(_) => ()

  private def restoreTerminal(): Unit = synchronized {
    if !restored then
      restored = true
      shutdownHook.foreach { h =>
        try Runtime.getRuntime.removeShutdownHook(h) catch case NonFatal(_) => ()
        shutdownHook = None
      }
      Console.out.print("\u001b[?25h\u001b[?1049l\u001b[0m")
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
      (math.max(48, parts(1).toInt), math.max(20, parts(0).toInt))
    catch case NonFatal(_) => (80, 24)

  private def commandExists(cmd: String): Boolean =
    try os.proc("which", cmd).call(check = false).exitCode == 0
    catch case NonFatal(_) => false

  // ── model ─────────────────────────────────────────────────────────────────

  private enum Input:
    case Left, Right, Rotate, SoftDrop, HardDrop, Cancel
    case JumpLane(idx: Int)

  private enum Outcome:
    case Pending
    case Cancelled
    case Fire(lane: Lane)

  private enum Phase:
    case Playing
    case LockFlash(lane: Lane, endNanos: Long)
    case Dissolve(lane: Lane, startNanos: Long, endNanos: Long)
    case GameOver(lane: Lane, deadlineNanos: Long)

  private final case class Debris(cellX: Int, cellY: Int, colorIdx: Int)

  private final case class State(
      cellX: Int,
      pieceY: Double,
      shape: Vector[String],
      pieceIdx: Int,
      stack: Vector[Debris],
      stackMaxRows: Int,
      phase: Phase = Phase.Playing,
      stackCoords: Set[(Int, Int)] = Set.empty
  ):
    def laneIdx(g: Geom): Int = g.laneForCol(cellX, shape.head.length)

    def fits(sh: Vector[String], px: Int, py: Int, g: Geom): Boolean =
      if px < 0 || px + sh.head.length > g.wellCols then false
      else if py < g.interiorTopY || py + sh.length > g.interiorBottomY + 1 then false
      else
        var ok = true
        var r = 0
        while r < sh.length && ok do
          var c = 0
          while c < sh(r).length && ok do
            if sh(r).charAt(c) != ' ' then
              val cy = py + r
              val cx = px + c
              val targetDebrisY = g.interiorBottomY - cy
              if stackCoords.contains((cx, targetDebrisY)) then ok = false
            c += 1
          r += 1
        ok

    def groundY(sh: Vector[String], px: Int, g: Geom): Int =
      var py = g.interiorTopY
      while py + 1 + sh.length <= g.interiorBottomY + 1 && fits(sh, px, py + 1, g) do
        py += 1
      py

  private object State:
    def rotCW(sh: Vector[String]): Vector[String] =
      val h = sh.length
      val w = sh.head.length
      Vector.tabulate(w)(r => Vector.tabulate(h)(c => sh(h - 1 - c).charAt(r)).mkString)

    def initialFor(g: Geom): State =
      val rnd = new scala.util.Random()
      val idx = rnd.nextInt(Tetrominoes.length)
      val initialShape = Tetrominoes(idx)

      // Start centered in Lane 1 (Shutdown center lane)
      val initX = 1 * g.laneCells + math.max(0, (g.laneCells - initialShape.head.length) / 2)

      val rows = g.rowsInner
      val cols = g.wellCols
      val grid = Array.fill(rows, cols)(-1)

      def cellFits(sh: Vector[String], px: Int, py: Int): Boolean =
        sh.indices.forall { cy =>
          sh(cy).indices.forall { cx =>
            sh(cy).charAt(cx) == ' ' || {
              val r = py + cy
              val c = px + cx
              r >= 0 && r < rows && c >= 0 && c < cols && grid(r)(c) == -1
            }
          }
        }
      def colHeight(c: Int): Int =
        var r = 0
        while r < rows && grid(r)(c) == -1 do r += 1
        rows - r

      val target = math.max(2, rows * 30 / 100)
      var attempts = 0
      var crest = 0
      while crest < target && attempts < 300 do
        attempts += 1
        val k = rnd.nextInt(Tetrominoes.length)
        var sh = Tetrominoes(k)
        var rot = rnd.nextInt(4)
        while rot > 0 do { sh = rotCW(sh); rot -= 1 }
        if sh.head.length <= cols then
          val px = rnd.nextInt(cols - sh.head.length + 1)
          if cellFits(sh, px, 0) then
            var py = 0
            while cellFits(sh, px, py + 1) do py += 1
            for cy <- sh.indices; cx <- sh(cy).indices if sh(cy).charAt(cx) != ' ' do
              grid(py + cy)(px + cx) = k
            crest = (0 until cols).map(colHeight).max

      val stack =
        for
          r <- (0 until rows).toVector
          c <- 0 until cols
          if grid(r)(c) >= 0
        yield Debris(c, rows - 1 - r, grid(r)(c))
      val maxRows = if crest > 0 then crest else 0

      State(
        cellX = initX,
        pieceY = g.interiorTopY.toDouble,
        shape = initialShape,
        pieceIdx = idx,
        stack = stack,
        stackMaxRows = maxRows,
        stackCoords = stack.map(d => (d.cellX, d.cellY)).toSet
      )

  private final case class Geom(cols: Int, rows: Int):
    val cardW      = math.min(cols, 74)
    val cardH      = math.min(rows, 22)
    val cardLeft   = math.max(0, (cols - cardW) / 2)
    val cardRight  = cardLeft + cardW - 1
    val cardTop    = math.max(0, (rows - cardH) / 2)
    val cardBottom = cardTop + cardH - 1

    val laneCells = 4
    val wellCols  = laneCells * 3 // 12 cells across 3 dedicated action chutes
    val innerW    = wellCols * CellW // 24 chars
    val wellLeft  = cardLeft + math.max(1, (cardW - (innerW + 2)) / 2)
    val wellRight = wellLeft + innerW + 1

    val wellTop         = cardTop + 3
    val wellBottom      = cardBottom - 5
    val interiorTopY    = wellTop + 1
    val interiorBottomY = wellBottom - 1
    val rowsInner       = interiorBottomY - interiorTopY + 1
    val wellH           = wellBottom - wellTop + 1

    def cellX(cx: Int): Int = wellLeft + 1 + cx * CellW
    def laneCenterX(i: Int): Int = cellX(i * laneCells + laneCells / 2)

    val topY: Int = interiorTopY

    def laneForCol(cx: Int, pieceW: Int): Int =
      val center = cx + pieceW / 2
      if center < laneCells then 0
      else if center < laneCells * 2 then 1
      else 2

  // ── input parser ──────────────────────────────────────────────────────────

  private object InputParser:
    def parse(buf0: List[Int], escSince0: Long, now: Long): (List[Input], List[Int], Long) =
      var buf = buf0
      var escSince = escSince0
      val out = scala.collection.mutable.ListBuffer.empty[Input]
      var continue = true
      while continue && buf.nonEmpty do
        buf.head match
          case 3 => // Ctrl-C
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
                  case Nil => continue = false
                  case f :: rest2 =>
                    f.toChar match
                      case 'A' => out += Input.Rotate   // Up arrow
                      case 'B' => out += Input.SoftDrop // Down arrow
                      case 'C' => out += Input.Right    // Right arrow
                      case 'D' => out += Input.Left     // Left arrow
                      case 'Z' => out += Input.Left     // Shift+Tab
                      case _   => ()
                    buf = rest2
                    escSince = 0L
              case _ =>
                out += Input.Cancel
                buf = buf.tail
                escSince = 0L
          case b =>
            b.toChar match
              case 'h' | 'H' | 'a' | 'A'     => out += Input.Left
              case 'l' | 'L' | 'd' | 'D'     => out += Input.Right
              case 'k' | 'K' | 'w' | 'W'     => out += Input.Rotate
              case 's' | 'S'                 => out += Input.SoftDrop
              case 'j' | 'J'                 => out += Input.HardDrop
              case '\r' | '\n' | ' '         => out += Input.HardDrop
              case '1'                       => out += Input.JumpLane(0)
              case 'r' | 'R'                 => out += Input.JumpLane(0)
              case '2'                       => out += Input.JumpLane(1)
              case 'p' | 'P'                 => out += Input.JumpLane(1)
              case '3'                       => out += Input.JumpLane(2)
              case 'q' | 'Q'                 => out += Input.Cancel
              case _                         => ()
            buf = buf.tail
            escSince = 0L
      (out.toList, buf, escSince)

  // ── theme ─────────────────────────────────────────────────────────────────

  private final case class Theme(
      bg: String,
      cardBg: String,
      cardBorder: String,
      wall: String,
      grid: String,
      floor: String,
      divider: String,
      dividerHot: String,
      ghost: String,
      dim: String,
      help: String,
      accentAmber: String,
      accentRed: String,
      accentViolet: String,
      accentCyan: String,
      pieceFg: Vector[String],
      pieceEdge: Vector[String],
      stackFg: Vector[String],
      stackEdge: Vector[String]
  ):
    def laneColor(lane: Lane): String = lane match
      case Lane.Reboot   => accentAmber
      case Lane.Shutdown => accentRed
      case Lane.Suspend  => accentViolet

  private object Theme:
    private type Rgb = (Int, Int, Int)

    private val Classic: Vector[Rgb] = Vector(
      (6, 182, 212),   // I  cyan (#06b6d4)
      (245, 158, 11),  // O  amber (#f59e0b)
      (139, 92, 246),  // T  violet (#8b5cf6)
      (34, 197, 94),   // S  green (#22c55e)
      (239, 68, 68),   // Z  red (#ef4444)
      (59, 130, 246),  // J  blue (#3b82f6)
      (249, 115, 22)   // L  orange (#f97316)
    )

    def from(p: Palette): Theme =
      val base      = rgb(p.base, (15, 17, 26)) // #0f111a
      val txt       = rgb(p.text, (248, 250, 252))
      val overlay   = rgb(p.overlay0, (46, 64, 87))

      val amberRgb  = (245, 158, 11)   // Warm Amber (#f59e0b)
      val redRgb    = rgb(p.red, (239, 68, 68)) // Carmine Red (#ef4444)
      val violetRgb = rgb(p.mauve, (139, 92, 246)) // Deep Violet (#8b5cf6)
      val cyanRgb   = rgb(p.teal, (56, 189, 248)) // Cyan (#38bdf8)
      val gridRgb   = (40, 45, 65)     // rgba(255, 255, 255, 0.04)
      val divRgb    = (55, 50, 85)     // rgba(139, 92, 246, 0.2)

      def tone(t: Double): String = fgSeq(mix(txt, base, t))

      Theme(
        bg           = bgSeq(base),
        cardBg       = bgSeq(base),
        cardBorder   = fgSeq(violetRgb),
        wall         = tone(0.60),
        grid         = fgSeq(gridRgb),
        floor        = tone(0.50),
        divider      = fgSeq(divRgb),
        dividerHot   = fgSeq(mix(violetRgb, txt, 0.20)),
        ghost        = fgSeq((75, 80, 105)),
        dim          = tone(0.70),
        help         = tone(0.55),
        accentAmber  = fgSeq(amberRgb),
        accentRed    = fgSeq(redRgb),
        accentViolet = fgSeq(violetRgb),
        accentCyan   = fgSeq(cyanRgb),
        pieceFg      = Classic.map(c => fgSeq(mix(c, txt, 0.1))),
        pieceEdge    = Classic.map(c => fgSeq(mix(c, base, 0.5))),
        stackFg      = Classic.map(c => fgSeq(mix(c, base, 0.3))),
        stackEdge    = Classic.map(c => fgSeq(mix(c, base, 0.62)))
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
    private def fgSeq(c: Rgb): String = s"\u001b[38;2;${c._1};${c._2};${c._3}m"
    private def bgSeq(c: Rgb): String = s"\u001b[48;2;${c._1};${c._2};${c._3}m"

  // ── tetrominoes ───────────────────────────────────────────────────────────

  private val CellW = 2
  private val CellH = 1

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
      val buf = new Buf(g.cols, g.rows)
      val curLane = st.laneIdx(g)

      // 1. Outer Bento Card Framing (1px border, 0px radius aesthetic)
      drawBentoCard(buf, g, th)

      // 2. Integrated Header Panel (Glyph [ ⮽ ] + Title + Tabular [ TIMEOUT: 30s ])
      val glyphTag = "[ ⮽ ]"
      val titleTag = "[ POLYOMINO // POWER CONTROL ]"
      val timeoutSec = math.ceil(remaining).toInt
      val timeoutTag = f"[ TIMEOUT: ${timeoutSec}%02ds ]"

      buf.put(g.cardLeft + 2, g.cardTop + 1, glyphTag, th.accentViolet)
      buf.put(g.cardLeft + 2 + glyphTag.length + 1, g.cardTop + 1, titleTag, th.accentCyan)
      buf.put(g.cardRight - timeoutTag.length - 1, g.cardTop + 1, timeoutTag, th.accentAmber)

      // 3. Matrix Well Box inside Bento Card
      drawWell(buf, g, th)

      // 4. Clean 1px Vector Square Grid (rgba(255, 255, 255, 0.04))
      var cy = g.interiorTopY
      while cy <= g.interiorBottomY do
        for k <- 0 until g.wellCols do
          buf.put(g.cellX(k), cy, "┼─", th.grid)
        cy += 1

      // 5. Solid 1px Vertical Guide Rails separating the 3 Action Chutes
      val pileTopY = g.interiorBottomY - st.stackMaxRows * CellH
      for k <- 1 to 2 do
        val dx  = g.cellX(k * g.laneCells)
        val hot = k == curLane || k == curLane + 1
        var y   = g.wellTop + 1
        while y <= pileTopY do
          buf.put(dx, y, "│ ", if hot then th.dividerHot else th.divider)
          y += 1

      // 6. Debris Pile
      for d <- st.stack do
        drawBlock(buf, g.cellX(d.cellX), g.interiorBottomY - d.cellY * CellH,
                  th.stackFg(d.colorIdx), th.stackEdge(d.colorIdx))

      // 7. Faint Dashed/Wireframe Ghost Piece at Bottom of Active Lane
      val ghostY = st.groundY(st.shape, st.cellX, g)
      drawShape(buf, st.shape, g.cellX(st.cellX), ghostY, th.ghost, th.ghost, "░")

      // 8. Active Piece (supports White Lock Flash & Line Dissolve animations)
      st.phase match
        case Phase.LockFlash(lane, _) =>
          drawShape(buf, st.shape, g.cellX(st.cellX), math.round(st.pieceY).toInt,
                    "\u001b[38;2;255;255;255m", "\u001b[38;2;255;255;255m", "█")
        case Phase.Dissolve(lane, s, e) =>
          val frac = math.min(1.0, math.max(0.0, (System.nanoTime() - s).toDouble / math.max(1L, e - s)))
          val glyph = if frac < 0.25 then "▓" else if frac < 0.5 then "▒" else if frac < 0.75 then "░" else "·"
          drawShape(buf, st.shape, g.cellX(st.cellX), math.round(st.pieceY).toInt,
                    th.laneColor(lane), th.laneColor(lane), glyph)
        case _ =>
          drawShape(buf, st.shape, g.cellX(st.cellX), math.round(st.pieceY).toInt,
                    th.pieceFg(st.pieceIdx), th.pieceEdge(st.pieceIdx), "█")

      // 9. Dedicated Dynamic Target Landing Pads & Well Badges at Base
      drawLanes(buf, g, th, curLane)

      // 10. Game Over Modal Overlay if Triggered
      st.phase match
        case Phase.GameOver(lane, deadline) =>
          val leftSec = math.max(0.0, (deadline - System.nanoTime()) / 1e9)
          drawGameOverModal(buf, g, th, lane, leftSec)
        case _ => ()

      // 11. Pinned Bottom Footer Strip
      val normalBadge = "[ NORMAL ]"
      val helpText = "h/l (←/→) Lane • k (↑) Rotate • j/Enter Drop • q/Esc Cancel"
      buf.put(g.cardLeft + 2, g.cardBottom - 1, normalBadge, th.accentCyan)
      buf.put(g.cardLeft + 2 + normalBadge.length + 2, g.cardBottom - 1, helpText, th.help)

      buf.render(th.bg)

    private def drawBentoCard(buf: Buf, g: Geom, th: Theme): Unit =
      val span = "─" * math.max(0, g.cardRight - g.cardLeft - 1)
      var y = g.cardTop + 1
      while y < g.cardBottom do
        buf.put(g.cardLeft, y, "│", th.cardBorder)
        buf.put(g.cardRight, y, "│", th.cardBorder)
        y += 1
      buf.put(g.cardLeft, g.cardTop, "┌" + span + "┐", th.cardBorder)
      buf.put(g.cardLeft, g.cardTop + 2, "├" + span + "┤", th.cardBorder)
      buf.put(g.cardLeft, g.cardBottom - 2, "├" + span + "┤", th.cardBorder)
      buf.put(g.cardLeft, g.cardBottom, "└" + span + "┘", th.cardBorder)

    private def drawWell(buf: Buf, g: Geom, th: Theme): Unit =
      val span = "─" * math.max(0, g.wellRight - g.wellLeft - 1)
      buf.put(g.wellLeft, g.wellTop, "┌" + span + "┐", th.wall)
      buf.put(g.wellLeft, g.wellBottom, "└" + span + "┘", th.wall)
      var y = g.wellTop + 1
      while y < g.wellBottom do
        buf.put(g.wellLeft, y, "│", th.wall)
        buf.put(g.wellRight, y, "│", th.wall)
        y += 1

    private def drawLanes(buf: Buf, g: Geom, th: Theme, laneIdx: Int): Unit =
      // Dynamic Landing Target Wells directly under each chute
      for i <- 0 to 2 do
        val sel      = i == laneIdx
        val w        = g.laneCells * CellW - 2
        val lane     = Lane.values(i)
        val padColor = if sel then th.laneColor(lane) else th.dim
        buf.put(g.laneCenterX(i) - w / 2, g.wellBottom + 1,
                (if sel then "▀" else "─") * w, padColor)

      // Spaced Landing Target Well Badges with Glow Highlight
      val third = math.max(1, (g.cardW - 4) / 3)
      for i <- 0 to 2 do
        val lane  = Lane.values(i)
        val sel   = i == laneIdx
        val label = s"[ ${lane.glyph} ${lane.label} ]"
        val color = if sel then th.laneColor(lane) else th.dim
        val cx    = g.cardLeft + 2 + third * i + third / 2
        val lx    = math.max(g.cardLeft + 2, math.min(g.cardRight - label.length - 1, cx - label.length / 2))
        buf.put(lx, g.wellBottom + 2, label, color)

    private def drawGameOverModal(buf: Buf, g: Geom, th: Theme, lane: Lane, leftSec: Double): Unit =
      val modalW = 38
      val modalH = 8
      val mx = math.max(g.cardLeft + 2, (g.cols - modalW) / 2)
      val my = g.wellTop + math.max(1, (g.wellH - modalH) / 2)
      val acc = th.laneColor(lane)

      val span = "═" * (modalW - 2)
      buf.put(mx, my, s"╔$span╗", acc)
      for r <- 1 until modalH - 1 do
        buf.put(mx, my + r, "║" + " " * (modalW - 2) + "║", acc)
      buf.put(mx, my + modalH - 1, s"╚$span╝", acc)

      val t1 = "▶  G A M E   O V E R  ◀"
      val t2 = s"CHOSEN ACTION: ${lane.glyph} ${lane.label}"
      val t3 = f"EXECUTING IN ${leftSec}%.1fs..."
      val t4 = "[Q/ESC] ABORT  •  [J/ENTER] NOW"
      buf.put(mx + (modalW - t1.length) / 2, my + 1, t1, acc)
      buf.put(mx + (modalW - t2.length) / 2, my + 3, t2, th.wall)
      buf.put(mx + (modalW - t3.length) / 2, my + 4, t3, acc)
      buf.put(mx + (modalW - t4.length) / 2, my + 6, t4, th.dim)

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
        sb.append(s"\u001b[${y + 1};1H").append(bg)
        var last = "?"
        var x = 0
        while x < cols do
          val c = fg(y)(x)
          if c != last then
            sb.append(if c.isEmpty then "\u001b[39m" else c)
            last = c
          sb.append(ch(y)(x))
          x += 1
        sb.append("\u001b[0m")
        y += 1
      sb.toString

  private val HelpText: String =
    """polyomino power-menu — Bento-card Tetris workstation power/exit modal.
      |
      |A tetromino descends down a 12-column matrix with 3 dedicated action chutes:
      |  Lane 1 (left)    reboot             (systemctl reboot)
      |  Lane 2 (center)  shutdown [default] (systemctl poweroff)
      |  Lane 3 (right)   lock + suspend     (swaylock -f ; systemctl suspend)
      |
      |Controls:
      |  left/right or h/l/a/d   move piece across chutes
      |  up or k / w             rotate tetromino clockwise
      |  down or s               soft drop
      |  1 / 2 / 3               direct chute snap (reboot, shutdown, lock+suspend)
      |  enter / space / j       hard drop into chosen chute & confirm
      |  esc / q                 cancel, run nothing
      |
      |When the piece lands, a Game Over countdown begins for the chosen chute
      |with an abort window before executing.
      |
      |Flags:
      |  --dry-run   render one frame to stdout and exit (no input, no action)
      |  --help      this text
      |""".stripMargin
