package polyomino.dotfiles.autotiling

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}

object AutotilingDaemon:
  def run(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    ctx.swaySocket match
      case None =>
        Left(CommandError("SWAYSOCK environment variable is not set. Sway autotiling daemon requires active Sway session.", 1))
      case Some(sock) =>
        println(s"\u001b[1;36m[polyomino autotiling]\u001b[0m Starting Sway Fibonacci autotiling daemon (socket: $sock)...")
        try
          // Perform initial split calculation
          autoSplitFocusedWindow()
          println("  \u001b[32m[OK]\u001b[0m Autotiling daemon active. Listening for Sway window events...")
          
          // Stream Sway IPC window events continuously
          val subProc = os.proc("swaymsg", "-m", "-t", "subscribe", "[\"window\"]").spawn()
          val reader = new java.io.BufferedReader(new java.io.InputStreamReader(subProc.stdout.wrapped))
          
          var line: String = null
          while { line = reader.readLine(); line != null } do
            if line.contains("\"focus\"") || line.contains("\"new\"") || line.contains("\"move\"") then
              autoSplitFocusedWindow()

          Right(())
        catch
          case e: Exception => Left(CommandError(s"Autotiling daemon failed: ${e.getMessage}"))

  def autoSplitFocusedWindow(): Unit =
    try
      val res = os.proc("swaymsg", "-t", "get_tree").call(check = false)
      if res.exitCode == 0 then
        val json = ujson.read(res.out.text())
        findFocusedWindow(json, None).foreach { (focused, parent) =>
          if shouldAutotile(focused, parent) then
            val rect = focused("rect")
            val width = rect("width").num.toInt
            val height = rect("height").num.toInt
            if width > 0 && height > 0 then
              val targetSplit = if width > height then "split h" else "split v"
              os.proc("swaymsg", targetSplit).call(check = false)
        }
    catch
      case _: Exception => ()

  private def findFocusedWindow(node: ujson.Value, parent: Option[ujson.Value]): Option[(ujson.Value, Option[ujson.Value])] =
    try
      if node.obj.get("focused").exists(_.bool) then
        Some((node, parent))
      else
        val tiledNodes = node.obj.get("nodes").map(_.arr).getOrElse(Vector.empty)
        val floatingNodes = node.obj.get("floating_nodes").map(_.arr).getOrElse(Vector.empty)
        val allChildren = tiledNodes ++ floatingNodes

        allChildren.flatMap(child => findFocusedWindow(child, Some(node))).headOption
    catch
      case _: Exception => None

  private def shouldAutotile(focused: ujson.Value, parent: Option[ujson.Value]): Boolean =
    val isFloating = focused.obj.get("type").exists(_.str == "floating_con")
    val parentLayout = parent.flatMap(_.obj.get("layout").map(_.str)).getOrElse("")
    !isFloating && parentLayout != "tabbed" && parentLayout != "stacked"
