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
          "timeout", "600", lockCmd,
          "timeout", "900", "swaymsg 'output * dpms off'",
          "resume", "swaymsg 'output * dpms on'",
          "timeout", "1200", "systemctl suspend",
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

  def runDrawWindow(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    if ctx.isTest then return Right(())

    var mode = "auto"
    var targetCmd: List[String] = Nil

    var remaining = args
    var stopParsing = false
    while remaining.nonEmpty && !stopParsing do
      remaining.head match
        case "-s" | "--spawn" =>
          mode = "spawn"
          targetCmd = remaining.tail
          stopParsing = true
        case "-c" | "--current" =>
          mode = "current"
          remaining = remaining.tail
        case "-h" | "--help" =>
          println("Usage: polyomino draw-window [-s|--spawn [cmd...]] [-c|--current] [-h|--help]")
          println("Draw interactive window geometry in Sway using slurp.")
          return Right(())
        case other =>
          mode = "spawn"
          targetCmd = remaining
          stopParsing = true

    if !isCommandAvailable("slurp") then
      return Left(CommandError("slurp is required but not installed.", 1))
    if !isCommandAvailable("swaymsg") then
      return Left(CommandError("swaymsg is required but not installed.", 1))

    val slurpRes = os.proc(
      "slurp", "-d",
      "-F", "JetBrainsMono Nerd Font",
      "-b", "#191C2488",
      "-c", "#EBB434ff",
      "-s", "#00D2D344",
      "-B", "#0F1117ff",
      "-w", "2",
      "-f", "%x %y %w %h"
    ).call(check = false, stdin = os.Inherit, stderr = os.Inherit)

    val geometry = slurpRes.out.text().trim
    if geometry.isEmpty then return Right(())

    val parts = geometry.split("\\s+").map(_.trim).filter(_.nonEmpty)
    if parts.length < 4 then return Right(())

    var x = parts(0).toIntOption.getOrElse(0)
    var y = parts(1).toIntOption.getOrElse(0)
    var w = parts(2).toIntOption.getOrElse(900)
    var h = parts(3).toIntOption.getOrElse(550)

    // Guard against accidental click-without-drag (< 30px)
    if w < 30 || h < 30 then
      w = 900
      h = 550
      x = x - w / 2
      y = y - h / 2
      if x < 0 then x = 50
      if y < 0 then y = 50

    val focusedType = getFocusedNodeType()
    val isContainerFocused = focusedType.contains("con") || focusedType.contains("floating_con")

    if mode == "current" || (mode == "auto" && isContainerFocused) then
      try
        os.proc("swaymsg", s"floating enable; border pixel 3; resize set ${w}px ${h}px; move absolute position ${x}px ${y}px").call(check = false)
        Right(())
      catch
        case e: Exception => Left(CommandError(s"Move window failed: ${e.getMessage}"))
    else
      val uniqueId = s"sway_drawn_${System.currentTimeMillis()}_${scala.util.Random.nextInt(10000)}"
      try
        os.proc("swaymsg", s"""for_window [app_id="^$uniqueId$$"] floating enable, border pixel 3; for_window [class="^$uniqueId$$"] floating enable, border pixel 3; for_window [title="^$uniqueId$$"] floating enable, border pixel 3""").call(check = false)
      catch
        case _: Exception => ()

      val termBin = if isCommandAvailable("kitty") then "kitty"
        else if isCommandAvailable("foot") then "foot"
        else if isCommandAvailable("alacritty") then "alacritty"
        else "kitty"

      val cmdStrings: Seq[String] = termBin match
        case "kitty" =>
          if targetCmd.isEmpty then Seq("kitty", "--class", uniqueId, "--title", uniqueId)
          else Seq("kitty", "--class", uniqueId, "--title", uniqueId, "-e") ++ targetCmd
        case "foot" =>
          if targetCmd.isEmpty then Seq("foot", "--app-id", uniqueId, "--title", uniqueId)
          else Seq("foot", "--app-id", uniqueId, "--title", uniqueId) ++ targetCmd
        case "alacritty" =>
          if targetCmd.isEmpty then Seq("alacritty", "--class", s"$uniqueId,$uniqueId", "--title", uniqueId)
          else Seq("alacritty", "--class", s"$uniqueId,$uniqueId", "--title", uniqueId, "-e") ++ targetCmd
        case other =>
          Seq(other)

      val spawnCmd: Seq[os.Shellable] = cmdStrings.map(s => (s: os.Shellable))

      try
        os.proc(spawnCmd*).spawn(stdout = os.Inherit, stderr = os.Inherit)
        val watcherThread = new Thread(() => {
          var found = false
          var i = 0
          while i < 40 && !found do
            try
              val tree = os.proc("swaymsg", "-t", "get_tree").call(check = false).out.text()
              if tree.contains(uniqueId) then
                found = true
                os.proc("swaymsg", s"""[app_id="^$uniqueId$$"] floating enable; [app_id="^$uniqueId$$"] border pixel 3; [app_id="^$uniqueId$$"] resize set ${w}px ${h}px; [app_id="^$uniqueId$$"] move absolute position ${x}px ${y}px; [app_id="^$uniqueId$$"] focus; [class="^$uniqueId$$"] floating enable; [class="^$uniqueId$$"] border pixel 3; [class="^$uniqueId$$"] resize set ${w}px ${h}px; [class="^$uniqueId$$"] move absolute position ${x}px ${y}px; [class="^$uniqueId$$"] focus""").call(check = false)
            catch
              case _: Exception => ()
            if !found then
              try Thread.sleep(25) catch case _: InterruptedException => ()
            i += 1
        })
        watcherThread.setDaemon(true)
        watcherThread.start()
        Right(())
      catch
        case e: Exception => Left(CommandError(s"Spawn window failed: ${e.getMessage}"))

  private def getFocusedNodeType(): Option[String] =
    try
      val res = os.proc("swaymsg", "-t", "get_tree").call(check = false)
      if res.exitCode == 0 then
        val json = ujson.read(res.out.text())
        findFocusedNodeType(json)
      else None
    catch
      case _: Exception => None

  private def findFocusedNodeType(node: ujson.Value): Option[String] =
    try
      if node.obj.get("focused").exists(_.bool) then
        node.obj.get("type").map(_.str)
      else
        val tiledNodes = node.obj.get("nodes").map(_.arr).getOrElse(Vector.empty)
        val floatingNodes = node.obj.get("floating_nodes").map(_.arr).getOrElse(Vector.empty)
        (tiledNodes ++ floatingNodes).flatMap(findFocusedNodeType).headOption
    catch
      case _: Exception => None

  def runMediaStatus(ctx: Context): Either[PolyominoError, Unit] =
    if ctx.isTest then
      println(ujson.write(ujson.Obj("text" -> "", "alt" -> "idle", "tooltip" -> "No media playing", "class" -> "idle")))
      return Right(())

    val raw = try
      os.proc("playerctl", "-p", "spotify_player,spotify,%any", "metadata", "--format", "{{status}}\t{{artist}}\t{{title}}\t{{album}}\t{{playerName}}")
        .call(check = false).out.text().trim
    catch
      case _: Exception => ""

    if raw.isEmpty then
      println(ujson.write(ujson.Obj("text" -> "", "alt" -> "idle", "tooltip" -> "No media playing", "class" -> "idle")))
      return Right(())

    val parts = raw.split("\t", -1).map(_.trim)
    val status = parts.lift(0).getOrElse("")
    val artist = parts.lift(1).getOrElse("")
    val title = parts.lift(2).getOrElse("")
    val album = parts.lift(3).getOrElse("")
    val player = parts.lift(4).getOrElse("Media")

    // Handle Spotify ads
    if title == "Advertisement" || (artist == "Spotify" && title.isEmpty) then
      println(ujson.write(ujson.Obj("text" -> " Ad", "alt" -> "ad", "tooltip" -> "Advertisement", "class" -> "ad")))
      return Right(())

    val displayArtist = if artist.nonEmpty then artist else "Unknown Artist"
    val displayTitle = if title.nonEmpty then title else "Unknown Track"
    val displayAlbum = if album.nonEmpty then album else "N/A"

    val (displayText, className) = status match
      case "Playing" => (s"󰐊 $displayArtist - $displayTitle", "playing")
      case "Paused" => (s"󰏤 $displayArtist - $displayTitle", "paused")
      case _ => ("", "idle")

    val tooltip = s"$player ($status)\nTitle:  $displayTitle\nArtist: $displayArtist\nAlbum:  $displayAlbum"

    println(ujson.write(ujson.Obj(
      "text" -> displayText,
      "alt" -> status,
      "tooltip" -> tooltip,
      "class" -> className
    )))
    Right(())

  def runFastfetchLogo(ctx: Context): Either[PolyominoError, Unit] =
    val osId = try
      if os.exists(os.root / "etc" / "os-release") then
        os.read.lines(os.root / "etc" / "os-release")
          .find(_.startsWith("ID="))
          .map(_.stripPrefix("ID=").replace("\"", "").trim.toLowerCase)
          .getOrElse("linux")
      else "linux"
    catch
      case _: Exception => "linux"

    val logoName = osId match
      case "arch" => "polyonimo_arch_tetris.txt"
      case "ubuntu" => "polyonimo_ubuntu_tetris.txt"
      case _ => "polyomino_tetris.txt"

    val repoAssetsDir = ctx.dotfilesDir / "config" / "fastfetch" / "assets"
    val assetsDir = ctx.configDir / "fastfetch" / "assets"
    os.makeDir.all(assetsDir)
    val currentLogoSymlink = assetsDir / "current_logo.txt"

    val targetLogo = if os.exists(assetsDir / logoName) then Some(assetsDir / logoName)
      else if os.exists(repoAssetsDir / logoName) then Some(repoAssetsDir / logoName)
      else if os.exists(assetsDir / "polyomino_tetris.txt") then Some(assetsDir / "polyomino_tetris.txt")
      else if os.exists(repoAssetsDir / "polyomino_tetris.txt") then Some(repoAssetsDir / "polyomino_tetris.txt")
      else None

    targetLogo match
      case Some(logoFile) =>
        try
          if os.exists(currentLogoSymlink) || os.isLink(currentLogoSymlink) then
            os.remove(currentLogoSymlink)
          os.symlink(currentLogoSymlink, logoFile)
          Right(())
        catch
          case e: Exception => Left(CommandError(s"Fastfetch logo symlink failed: ${e.getMessage}"))
      case None =>
        Right(())

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
