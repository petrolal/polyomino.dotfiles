package polyomino.dotfiles.sysutils

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}
import polyomino.dotfiles.theme.ThemeEngine

object SysUtils:
  def runLock(ctx: Context, args: List[String] = Nil): Either[PolyominoError, Unit] =
    val isPreview = args.exists(a => a == "--preview" || a == "-p" || a == "--test" || a == "-t" || a == "--fullscreen" || a == "-f" || a == "--screenshot" || a == "-s")
    val rubikScript = ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-rubik-lock"
    val scriptToRun = if os.exists(rubikScript) then rubikScript
      else ctx.home / ".local" / "bin" / "polyomino-rubik-lock"

    if ctx.isTest then
      if os.exists(scriptToRun) then Right(())
      else Left(CommandError(s"Lock script not found at $scriptToRun"))
    else if isPreview then
      runLockPreview(ctx, args)
    else if os.exists(scriptToRun) then
      println(s"\u001b[1;34m[polyomino lock]\u001b[0m Locking screen via ${scriptToRun.last}...")
      try
        val fullCmd: Seq[os.Shellable] = Seq(scriptToRun.toString: os.Shellable) ++ args.map(a => (a: os.Shellable))
        os.proc(fullCmd*).spawn(stdout = os.Inherit, stderr = os.Inherit)
        Right(())
      catch
        case e: Exception => Left(CommandError(s"Lock failed: ${e.getMessage}"))
    else
      Left(CommandError(s"Lock script not found at $scriptToRun"))

  def runLockPreview(ctx: Context, args: List[String] = Nil): Either[PolyominoError, Unit] =
    val rubikScript = ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-rubik-lock"
    val scriptToRun = if os.exists(rubikScript) then rubikScript
      else ctx.home / ".local" / "bin" / "polyomino-rubik-lock"

    if os.exists(scriptToRun) then
      if ctx.isTest then Right(())
      else
        println(s"\u001b[1;36m[polyomino preview-lock]\u001b[0m Launching lock screen preview (${scriptToRun.last})...")
        try
          val previewArgs = if args.isEmpty || !args.exists(a => a.startsWith("-")) then List("--preview") else args
          val fullCmd: Seq[os.Shellable] = Seq(scriptToRun.toString: os.Shellable) ++ previewArgs.map(a => (a: os.Shellable))
          os.proc(fullCmd*).call(stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
          Right(())
        catch
          case e: Exception => Left(CommandError(s"Preview lock failed: ${e.getMessage}"))
    else
      Left(CommandError(s"Lock script not found at $scriptToRun"))

  def runIdle(ctx: Context): Either[PolyominoError, Unit] =
    if ctx.isTest then Right(())
    else
      println("\u001b[1;34m[polyomino idle]\u001b[0m Launching swayidle daemon...")
      val rubikScript = ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-rubik-lock"
      val localRubik = ctx.home / ".local" / "bin" / "polyomino-rubik-lock"
      val lockConfigFile = ctx.configDir / "swaylock" / "config"
      val lockCmd = if os.exists(rubikScript) then
        rubikScript.toString
      else if os.exists(localRubik) then
        localRubik.toString
      else if os.exists(lockConfigFile) then
        s"swaylock -f --config $lockConfigFile"
      else
        "swaylock -f -c 1e1e2e"

      try
        os.proc(
          "swayidle", "-w",
          "timeout", "300", lockCmd,
          "timeout", "600", "swaymsg 'output * dpms off'",
          "resume", "swaymsg 'output * dpms on'",
          "timeout", "900", "systemctl suspend",
          "before-sleep", lockCmd
        ).spawn(stdout = os.Inherit, stderr = os.Inherit)
        Right(())
      catch
        case e: Exception => Left(CommandError(s"Idle daemon failed: ${e.getMessage}"))

  def runScreenshot(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    val mode = args.headOption.getOrElse("region")

    if mode != "full" && mode != "region" && mode != "window" then
      System.err.println("Usage: polyomino-screenshot {full|region|window}")
      return Left(CommandError("Usage: polyomino-screenshot {full|region|window}", 1))

    val screenshotsDir = ctx.home / "Pictures" / "Screenshots"
    os.makeDir.all(screenshotsDir)
    val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"))
    val file = screenshotsDir / s"$timestamp.png"

    if ctx.isTest then
      os.write.over(file, Array[Byte](0x89.toByte, 'P'.toByte, 'N'.toByte, 'G'.toByte))
      return Right(())

    println(s"\u001b[1;34m[polyomino screenshot]\u001b[0m Capturing $mode screenshot to $file...")

    try
      val captureRes = mode match
        case "full" =>
          os.proc("grim", file.toString).call(check = false)
        case "region" =>
          val slurpRes = os.proc("slurp").call(
            check = false,
            stdin = os.Inherit,
            stdout = os.Pipe,
            stderr = os.Inherit
          )
          if slurpRes.exitCode == 0 && slurpRes.out.text().trim.nonEmpty then
            val geom = slurpRes.out.text().trim
            os.proc("grim", "-g", geom, file.toString).call(check = false)
          else
            notifyDesktop("Cancelled", "Screenshot region selection cancelled.")
            return Right(())
        case "window" =>
          getFocusedWindowGeometry() match
            case Some(geom) =>
              os.proc("grim", "-g", geom, file.toString).call(check = false)
            case None =>
              notifyDesktop("Failed", "No focused window found.")
              return Left(CommandError("No focused window found", 1))

      if captureRes.exitCode == 0 && os.exists(file) then
        // Copy screenshot image bytes to wl-copy clipboard if available
        try
          if isCommandAvailable("wl-copy") then
            os.proc("wl-copy", "--type", "image/png").call(stdin = os.read.bytes(file), check = false)
        catch
          case _: Exception => ()

        notifyDesktop("Screenshot Saved", s"Saved to ${file.toString} (copied to clipboard)")
        println(s"  \u001b[32m[OK]\u001b[0m Saved to $file (copied to clipboard)")
        Right(())
      else
        Left(CommandError("grim screenshot capture failed", 1))
    catch
      case e: Exception => Left(CommandError(s"Screenshot failed: ${e.getMessage}"))

  private def getFocusedWindowGeometry(): Option[String] =
    try
      val res = os.proc("swaymsg", "-t", "get_tree").call(check = false)
      if res.exitCode == 0 then
        val json = ujson.read(res.out.text())
        findFocusedNodeGeometry(json)
      else None
    catch
      case _: Exception => None

  def findFocusedNodeGeometry(node: ujson.Value): Option[String] =
    try
      if node.obj.get("focused").exists(_.bool) then
        val rect = node.obj("rect")
        val x = rect("x").num.toInt
        val y = rect("y").num.toInt
        val w = rect("width").num.toInt
        val h = rect("height").num.toInt
        Some(s"$x,$y ${w}x$h")
      else
        val tiledNodes = node.obj.get("nodes").map(_.arr).getOrElse(Vector.empty)
        val floatingNodes = node.obj.get("floating_nodes").map(_.arr).getOrElse(Vector.empty)
        (tiledNodes ++ floatingNodes).flatMap(findFocusedNodeGeometry).headOption
    catch
      case _: Exception => None

  private def notifyDesktop(title: String, body: String): Unit =
    try
      if isCommandAvailable("notify-send") then
        val escapedTitle = title.replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedBody = body.replace("\\", "\\\\").replace("\"", "\\\"")
        os.proc("notify-send", "-u", "normal", "-t", "4000", "-a", "polyomino", escapedTitle, escapedBody).call(check = false)
    catch
      case _: Exception => () // Silently fail if notification daemon unavailable

  def runCalendar(ctx: Context): Either[PolyominoError, Unit] =
    if ctx.isTest then Right(())
    else if isCommandAvailable("swaync-client") && isProcessRunning("swaync") then
      try
        os.proc("swaync-client", "-t", "-sw").call(check = false)
        Right(())
      catch
        case _: Exception => runFallbackCalendar(ctx)
    else
      runFallbackCalendar(ctx)

  private def isProcessRunning(name: String): Boolean =
    try os.proc("pgrep", "-x", name).call(check = false).exitCode == 0 catch case _: Exception => false

  private def runFallbackCalendar(ctx: Context): Either[PolyominoError, Unit] =
    try
      if isCommandAvailable("kitty") then
        os.proc(
          "kitty",
          "--class=polyomino-calendar",
          "--title=Calendar",
          "-o", "font_size=15",
          "-o", "remember_window_size=no",
          "-o", "initial_window_width=680",
          "-o", "initial_window_height=440",
          "sh", "-c", "cal -3; echo ''; read -n 1 -s -r -p '  [Press any key or Escape to close]' || true"
        ).spawn(stdout = os.Inherit, stderr = os.Inherit)
        Right(())
      else
        println("  \u001b[33m[NOTE]\u001b[0m Calendar tool not available.")
        Right(())
    catch
      case e: Exception => Left(CommandError(s"Calendar popup failed: ${e.getMessage}"))

  def runDrawWindow(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    val script = ctx.dotfilesDir / "config" / "sway" / "scripts" / "sway-draw-window.sh"
    val scriptToRun = if os.exists(script) then script else ctx.configDir / "sway" / "scripts" / "sway-draw-window.sh"
    if os.exists(scriptToRun) then
      if ctx.isTest then Right(())
      else
        try
          val fullCmd: Seq[os.Shellable] = (scriptToRun.toString +: args).map(s => (s: os.Shellable))
          os.proc(fullCmd*).call(stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit, check = false)
          Right(())
        catch
          case e: Exception => Left(CommandError(s"Draw window failed: ${e.getMessage}"))
    else
      Left(CommandError(s"Script not found at $scriptToRun"))

  def runScreensaver(ctx: Context, args: List[String] = Nil): Either[PolyominoError, Unit] =
    val script = ctx.dotfilesDir / "config" / "sway" / "scripts" / "screensaver.py"
    val scriptToRun = if os.exists(script) then script else ctx.configDir / "sway" / "scripts" / "screensaver.py"
    if os.exists(scriptToRun) then
      if ctx.isTest then Right(())
      else
        try
          val fullCmd: Seq[os.Shellable] = Seq("python3": os.Shellable, scriptToRun.toString: os.Shellable) ++ args.map(a => (a: os.Shellable))
          os.proc(fullCmd*).call(stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
          Right(())
        catch
          case e: Exception => Left(CommandError(s"Screensaver failed: ${e.getMessage}"))
    else
      Left(CommandError(s"Screensaver script not found at $scriptToRun"))

  def runMatrix(ctx: Context, args: List[String] = Nil): Either[PolyominoError, Unit] =
    val script = ctx.dotfilesDir / "config" / "sway" / "scripts" / "matrix.sh"
    val scriptToRun = if os.exists(script) then script else ctx.configDir / "sway" / "scripts" / "matrix.sh"
    if os.exists(scriptToRun) then
      if ctx.isTest then Right(())
      else
        try
          val fullCmd: Seq[os.Shellable] = Seq("bash": os.Shellable, scriptToRun.toString: os.Shellable) ++ args.map(a => (a: os.Shellable))
          os.proc(fullCmd*).call(stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
          Right(())
        catch
          case e: Exception => Left(CommandError(s"Matrix screen failed: ${e.getMessage}"))
    else
      Left(CommandError(s"Matrix script not found at $scriptToRun"))

  def runWelcome(ctx: Context, args: List[String] = Nil): Either[PolyominoError, Unit] =
    val script = ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-welcome.py"
    val scriptToRun = if os.exists(script) then script else ctx.configDir / "sway" / "scripts" / "polyomino-welcome.py"
    if os.exists(scriptToRun) then
      if ctx.isTest then Right(())
      else
        try
          val fullCmd: Seq[os.Shellable] = Seq("python3": os.Shellable, scriptToRun.toString: os.Shellable) ++ args.map(a => (a: os.Shellable))
          os.proc(fullCmd*).spawn(stdout = os.Inherit, stderr = os.Inherit)
          Right(())
        catch
          case e: Exception => Left(CommandError(s"Welcome Center failed: ${e.getMessage}"))
    else
      Left(CommandError(s"Welcome Center script not found at $scriptToRun"))

  private def isCommandAvailable(cmd: String): Boolean =
    try os.proc("which", cmd).call(check = false).exitCode == 0 catch case _: Exception => false
