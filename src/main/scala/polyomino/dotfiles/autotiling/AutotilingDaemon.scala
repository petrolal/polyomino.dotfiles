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
          var subProc: os.SubProcess = null
          var reader: java.io.BufferedReader = null
          try
            subProc = os.proc("swaymsg", "-m", "-t", "subscribe", "[\"window\"]").spawn(stderr = os.Inherit)
            reader = new java.io.BufferedReader(new java.io.InputStreamReader(subProc.stdout.wrapped))
            
            var line: String = null
            while { line = reader.readLine(); line != null } do
              try
                val eventJson = ujson.read(line)
                val change = eventJson.obj.get("change").map(_.str).getOrElse("")
                if change == "focus" || change == "new" || change == "move" then
                  autoSplitFocusedWindow()
              catch
                case _: Exception => ()

            Right(())
          finally
            if reader != null then try reader.close() catch case _: Exception => ()
            if subProc != null then
              try
                subProc.destroy()
                subProc.destroyForcibly()
              catch case _: Exception => ()
        catch
          case e: Exception => Left(CommandError(s"Autotiling daemon failed: ${e.getMessage}"))

  def autoSplitFocusedWindow(): Unit =
    try
      val res = os.proc("swaymsg", "-t", "get_tree").call(check = false)
      if res.exitCode == 0 then
        val json = ujson.read(res.out.text())
        calculateSplit(json).foreach { targetSplit =>
          os.proc("swaymsg", targetSplit).call(check = false)
        }
    catch
      case _: Exception => ()

  def calculateSplit(tree: ujson.Value): Option[String] =
    findFocusedWindow(tree, None).flatMap { (focused, parent) =>
      if shouldAutotile(focused, parent) then
        try
          val rect = focused("rect")
          val width = rect("width").num.toInt
          val height = rect("height").num.toInt
          if width > 0 && height > 0 then
            Some(if width > height then "split h" else "split v")
          else None
        catch
          case _: Exception => None
      else None
    }

  def findFocusedWindow(node: ujson.Value, parent: Option[ujson.Value] = None): Option[(ujson.Value, Option[ujson.Value])] =
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

  def shouldAutotile(focused: ujson.Value, parent: Option[ujson.Value]): Boolean =
    val isFloatingCon = focused.obj.get("type").exists(_.str == "floating_con")
    val parentIsFloating = parent.exists(_.obj.get("type").exists(_.str == "floating_con"))
    val hasFloatingState = focused.obj.get("floating").exists {
      case ujson.Str(s) => s.contains("on")
      case ujson.Bool(b) => b
      case _ => false
    }
    val isFloating = isFloatingCon || parentIsFloating || hasFloatingState
    val isConOrFloating = focused.obj.get("type").exists(t => t.str == "con" || t.str == "floating_con")
    val parentLayout = parent.flatMap(_.obj.get("layout").map(_.str)).getOrElse("")
    !isFloating && isConOrFloating && parentLayout != "tabbed" && parentLayout != "stacked"
