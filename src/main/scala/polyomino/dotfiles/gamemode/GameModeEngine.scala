package polyomino.dotfiles.gamemode

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}

object GameModeEngine:
  private def stateFile(ctx: Context): os.Path = ctx.configDir / "polyomino" / "gamemode.state"
  private def pidFile(ctx: Context): os.Path = ctx.configDir / "polyomino" / "gamemode.pid"

  def isActive(ctx: Context): Boolean =
    os.exists(stateFile(ctx))

  def run(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    args.headOption match
      case Some("--waybar") | Some("--json") =>
        printWaybarJson(ctx)
        Right(())
      case Some("toggle") =>
        toggle(ctx)
      case Some("on") | Some("enable") | Some("start") =>
        enable(ctx)
      case Some("off") | Some("disable") | Some("stop") =>
        disable(ctx)
      case Some("status") =>
        printHumanStatus(ctx)
        Right(())
      case _ =>
        if args.isEmpty then
          printHumanStatus(ctx)
          Right(())
        else
          Left(CommandError(s"Unknown gamemode subcommand: ${args.mkString(" ")}", 1))

  def printWaybarJson(ctx: Context): Unit =
    if isActive(ctx) then
      val json =
        """{"text": "󰊴 GAME", "alt": "active", "tooltip": "Game Mode ACTIVE\n• SwayFX Blur: Disabled\n• VRR (Adaptive Sync): Enabled\n• CPU/Power: Performance\n• Screen Sleep: Inhibited", "class": "active"}"""
      println(json)
    else
      val json =
        """{"text": "󰊴", "alt": "inactive", "tooltip": "Game Mode INACTIVE\nClick to activate gaming optimizations", "class": "inactive"}"""
      println(json)

  private def printHumanStatus(ctx: Context): Unit =
    if isActive(ctx) then
      println("\u001b[1;32m[polyomino gamemode]\u001b[0m State: \u001b[1;32mACTIVE\u001b[0m")
      println("  • Blur: Disabled")
      println("  • VRR (Adaptive Sync): Enabled")
      println("  • Power Profile: Performance")
      println("  • Screen Sleep/Lock: Inhibited")
    else
      println("\u001b[1;36m[polyomino gamemode]\u001b[0m State: \u001b[1;30mINACTIVE\u001b[0m")
      println("  Run 'polyomino gamemode toggle' or 'polyomino gamemode on' to activate.")

  def toggle(ctx: Context): Either[PolyominoError, Unit] =
    if isActive(ctx) then disable(ctx) else enable(ctx)

  def enable(ctx: Context): Either[PolyominoError, Unit] =
    os.makeDir.all(ctx.configDir / "polyomino")
    os.write.over(stateFile(ctx), s"active=true\ntimestamp=${System.currentTimeMillis()}\n")

    if ctx.isTest then
      println("  [OK] GameMode enabled (test mode)")
      return Right(())

    println("\u001b[1;32m[polyomino gamemode]\u001b[0m Activating gaming performance optimizations...")

    // 1. Disable SwayFX compositor blur for max GPU fillrate
    try os.proc("swaymsg", "blur disable").call(check = false)
    catch case _: Exception => ()

    // 2. Enable Adaptive Sync (VRR/G-Sync/FreeSync)
    try os.proc("swaymsg", "output * adaptive_sync on").call(check = false)
    catch case _: Exception => ()

    // 3. Boost power profile / CPU governor
    try
      if isCommandAvailable("powerprofilesctl") then
        os.proc("powerprofilesctl", "set", "performance").call(check = false)
      else if isCommandAvailable("cpupower") then
        os.proc("sudo", "cpupower", "frequency-set", "-g", "performance").call(check = false)
    catch case _: Exception => ()

    // 4. Inhibit screen sleep and lock
    try
      if isCommandAvailable("systemd-inhibit") then
        val pb = new java.lang.ProcessBuilder("systemd-inhibit", "--what=idle", "--who=polyomino-gamemode", "--why=Gaming Active", "sleep", "infinity")
        pb.redirectInput(java.lang.ProcessBuilder.Redirect.DISCARD)
        pb.redirectOutput(java.lang.ProcessBuilder.Redirect.DISCARD)
        pb.redirectError(java.lang.ProcessBuilder.Redirect.DISCARD)
        val proc = pb.start()
        try os.write.over(pidFile(ctx), proc.pid().toString)
        catch case _: Exception => ()
    catch case _: Exception => ()

    // 5. Desktop notification (appears immediately on activation)
    notifyDesktop(
      "Game Mode Activated",
      "Performance optimizations ENABLED 🚀\n• VRR: On | Blur: Off | Profile: Performance"
    )

    // 6. Trigger Waybar update
    triggerWaybarSignal()

    println("  \u001b[32m[OK]\u001b[0m Game Mode ACTIVE")
    Right(())

  def disable(ctx: Context): Either[PolyominoError, Unit] =
    if os.exists(stateFile(ctx)) then
      try os.remove(stateFile(ctx))
      catch case _: Exception => ()

    if ctx.isTest then
      println("  [OK] GameMode disabled (test mode)")
      return Right(())

    println("\u001b[1;36m[polyomino gamemode]\u001b[0m Restoring standard desktop configuration...")

    // 1. Restore SwayFX compositor blur
    try os.proc("swaymsg", "blur enable").call(check = false)
    catch case _: Exception => ()

    // 2. Restore Adaptive Sync (VRR off)
    try os.proc("swaymsg", "output * adaptive_sync off").call(check = false)
    catch case _: Exception => ()

    // 3. Restore power profile
    try
      if isCommandAvailable("powerprofilesctl") then
        os.proc("powerprofilesctl", "set", "balanced").call(check = false)
      else if isCommandAvailable("cpupower") then
        os.proc("sudo", "cpupower", "frequency-set", "-g", "powersave").call(check = false)
    catch case _: Exception => ()

    // 5. Kill idle inhibitor process
    if os.exists(pidFile(ctx)) then
      try
        val pid = os.read(pidFile(ctx)).trim
        if pid.nonEmpty then
          os.proc("kill", pid).call(check = false)
        os.remove(pidFile(ctx))
      catch case _: Exception => ()

    try os.proc("pkill", "-f", "systemd-inhibit --what=idle --who=polyomino-gamemode").call(check = false)
    catch case _: Exception => ()

    // 6. Desktop notification (appears immediately on deactivation)
    notifyDesktop(
      "Game Mode Deactivated",
      "Standard desktop configuration restored.\n• VRR: Off | Blur: On | Profile: Balanced"
    )

    // 7. Trigger Waybar update
    triggerWaybarSignal()

    println("  \u001b[32m[OK]\u001b[0m Game Mode DEACTIVATED")
    Right(())

  private def triggerWaybarSignal(): Unit =
    try os.proc("pkill", "-RTMIN+8", "waybar").call(check = false)
    catch case _: Exception => ()

  private def notifyDesktop(title: String, body: String): Unit =
    try
      if isCommandAvailable("notify-send") then
        os.proc(
          "notify-send",
          "-u", "normal",
          "-t", "3000",
          "-a", "polyomino",
          "-i", "input-gaming",
          "-h", "string:synchronous:gamemode",
          "-h", "string:transient:true",
          title,
          body
        ).call(check = false)
    catch case _: Exception => ()

  private def isCommandAvailable(cmd: String): Boolean =
    try os.proc("which", cmd).call(check = false).exitCode == 0 catch case _: Exception => false
