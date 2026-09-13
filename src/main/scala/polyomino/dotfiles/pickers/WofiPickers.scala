package polyomino.dotfiles.pickers

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}
import polyomino.dotfiles.theme.ThemeEngine

object WofiPickers:

  /** Path to the shared GTK tile-menu script (grid of tiles, glass/blur
    * backdrop) that all pickers below use in place of a wofi dmenu list. */
  private def tilemenuScript(ctx: Context): os.Path =
    val script = ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-tilemenu.py"
    if os.exists(script) then script else ctx.configDir / "sway" / "scripts" / "polyomino-tilemenu.py"

  private def tile(id: String, icon: String, title: String, desc: String, accent: String, badge: Option[String] = None, variant: String = "card"): ujson.Value =
    val obj = ujson.Obj("id" -> id, "icon" -> icon, "title" -> title, "desc" -> desc, "accent" -> accent, "variant" -> variant)
    badge.foreach(b => obj("badge") = b)
    obj

  /** Launches the shared tile-menu GTK window with the given tiles and
    * returns the selected tile's id, or "" if the window was cancelled. */
  private def tilePick(
    ctx: Context,
    title: String,
    tiles: Seq[ujson.Value],
    columns: Int,
    width: Int,
    height: Int,
    info: Boolean = false
  ): String =
    val script = tilemenuScript(ctx)
    val args: Seq[os.Shellable] = (Seq(
      "python3", script.toString,
      "--title", title,
      "--columns", columns.toString,
      "--width", width.toString,
      "--height", height.toString
    ) ++ (if info then Seq("--info") else Seq.empty)).map(s => (s: os.Shellable))
    val res = os.proc(args*).call(stdin = ujson.write(tiles), check = false)
    res.out.text().trim

  def runThemePicker(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    // Toggle behavior: check if theme picker is already open
    try
      val checkRes = os.proc("pgrep", "-f", "polyomino-tilemenu.*Theme").call(check = false)
      if checkRes.exitCode == 0 then
        val pids = checkRes.out.text().trim.split("\\s+").filter(_.nonEmpty)
        for pid <- pids do
          try os.proc("kill", pid).call(check = false) catch case _: Exception => ()
        return Right(())
    catch
      case _: Exception => ()

    println("[1;35m[polyomino theme-picker][0m Launching tile GUI theme picker...")

    val themes = polyomino.dotfiles.theme.Palette.listAll(ctx)
    val activePalette = ThemeEngine.getActivePalette(ctx)

    try
      // Step 1: Select Theme
      val themeTiles = themes.map { name =>
        val pal = polyomino.dotfiles.theme.Palette.find(name, ctx)
        val isActive = pal.name.equalsIgnoreCase(activePalette.name)
        tile(pal.name, "◆", pal.name.capitalize, pal.label, "accent", badge = if isActive then Some("ACTIVE") else None)
      }
      val selectedTheme = tilePick(ctx, "[ ⊞ ] Theme", themeTiles, columns = 2, width = 520, height = 360)
      if selectedTheme.isEmpty then return Right(())

      println(s"  [32m[OK][0m Selected theme '$selectedTheme'")

      // Step 2: Select Wallpaper Mode
      val modeTiles = Seq(
        tile("static", "▣", "Static Wallpaper", "Keep a single wallpaper for this flavor", "teal"),
        tile("rotate", "↻", "Rotate Wallpapers", "Cycle through the flavor's wallpapers every 30m", "sapphire")
      )
      val mode = tilePick(ctx, s"[ ⊞ ] Mode · ${selectedTheme.capitalize}", modeTiles, columns = 2, width = 480, height = 260) match
        case "rotate" => "rotate"
        case "static" => "wallpaper"
        case _ => return Right(())
      val interval = "30m"

      // Step 3 (static mode only): if the chosen flavor ships more than one
      // wallpaper, let the user pick which one instead of silently taking the
      // first. "Auto" keeps the previous behaviour.
      val customWallpaper: Option[String] =
        if mode != "wallpaper" then None
        else
          val palName = polyomino.dotfiles.theme.Palette.find(selectedTheme, ctx).name
          val choices = polyomino.dotfiles.wallpaper.WallpaperEngine.wallpapersForFlavor(ctx, palName)
          if choices.size <= 1 then None
          else
            val AutoId = "__auto__"
            val wpTiles = tile(AutoId, "◆", "Auto (Default)", "Let polyomino pick the flavor's default", "accent") +:
              choices.map(p => tile(p.toString, "🖼", p.last, "", "peach"))
            val picked = tilePick(ctx, s"[ ⊞ ] Wallpaper · ${selectedTheme.capitalize}", wpTiles, columns = 3, width = 620, height = 420)
            if picked.isEmpty || picked == AutoId then None
            else Some(picked)

      ThemeEngine.applyTheme(ctx, selectedTheme, mode = mode, customWallpaper = customWallpaper, interval = interval)
    catch
      case e: Exception => Left(CommandError(s"Theme-picker failed: ${e.getMessage}"))

  def runWallpaperPicker(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    import polyomino.dotfiles.wallpaper.WallpaperEngine

    // Toggle behavior: close an already-open wallpaper picker
    try
      val checkRes = os.proc("pgrep", "-f", "polyomino-tilemenu.*Wallpaper").call(check = false)
      if checkRes.exitCode == 0 then
        val pids = checkRes.out.text().trim.split("\\s+").filter(_.nonEmpty)
        for pid <- pids do
          try os.proc("kill", pid).call(check = false) catch case _: Exception => ()
        return Right(())
    catch
      case _: Exception => ()

    val flavor = WallpaperEngine.activeFlavor(ctx)
    val options = WallpaperEngine.wallpapersForFlavor(ctx, flavor)
    if options.isEmpty then
      return Left(CommandError(s"No wallpapers for flavor '$flavor' in themes/wallpapers/"))

    println(s"[1;35m[polyomino wallpaper-picker][0m Launching tile GUI wallpaper picker for '$flavor'...")

    val current = WallpaperEngine.currentWallpaper(ctx)
    val RandomId = "__random__"
    val tiles = tile(RandomId, "🎲", "Random", "Cycle to a random wallpaper", "mauve") +: options.map { p =>
      val isActive = current.contains(p.toString)
      tile(p.toString, "🖼", p.last, "", "peach", badge = if isActive then Some("ACTIVE") else None)
    }

    try
      val selected = tilePick(ctx, s"[ ⊞ ] Wallpaper · ${flavor.capitalize}", tiles, columns = 3, width = 640, height = 440)
      if selected.isEmpty then return Right(())
      if selected == RandomId then
        WallpaperEngine.run(ctx, List("random"))
      else
        val path = os.Path(selected)
        WallpaperEngine.run(ctx, List(path.baseName))
    catch
      case e: Exception => Left(CommandError(s"Wallpaper-picker failed: ${e.getMessage}"))

  /** `polyomino menu` — the click target for the waybar POLYOMINO pill.
    *
    * A tile-menu GTK window that fans out to the desktop's own tools: the Tetris
    * power menu, the theme/wallpaper pickers, an "edit a config file" sub-grid,
    * and the healthcheck. Everything it launches is an existing subcommand or
    * `polyomino-*` alias — the menu is pure front-end. Re-invoking it while it
    * is open closes it (matches the calendar / picker toggle behaviour). */
  def runMenu(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    // Toggle: a second click closes the open menu.
    try
      val checkRes = os.proc("pgrep", "-f", "polyomino-tilemenu.*polyomino$").call(check = false)
      if checkRes.exitCode == 0 then
        val pids = checkRes.out.text().trim.split("\\s+").filter(_.nonEmpty)
        for pid <- pids do
          try os.proc("kill", pid).call(check = false) catch case _: Exception => ()
        return Right(())
    catch
      case _: Exception => ()

    val term = sys.env.get("TERMINAL").filter(_.nonEmpty).getOrElse("kitty")
    val binDir = ctx.home / ".local" / "bin"
    val polyomino = (binDir / "polyomino").toString

    // Waybar runs one process for every bar, so an `on-click` can't tell which
    // monitor's pill was clicked. The per-output bar objects in config.jsonc
    // pass `--output <name>`; the spawned sub-pickers still honour it.
    val outputArgs: Seq[String] =
      args.sliding(2).collectFirst {
        case Seq("--output" | "-o", name) if name.nonEmpty => Seq("-o", name)
      }.getOrElse(Seq.empty)

    val entries = Seq(
      tile("welcome", "✨", "Welcome center", "", "accent", variant = "square"),
      tile("gamemode", "🎮", "Game mode", "", "green", variant = "square"),
      tile("power", "⏻", "Power menu", "", "red", variant = "square"),
      tile("theme", "🎨", "Theme and wallpaper", "", "mauve", variant = "square"),
      tile("wallpaper", "🖼", "Wallpaper", "", "peach", variant = "square"),
      tile("edit", "⚙", "Edit a config file", "", "sapphire", variant = "square"),
      tile("health", "🩺", "Healthcheck", "", "teal", variant = "square")
    )

    // `polyomino menu` is a grandchild of waybar's `sh -c` on-click; when this
    // process exits right after spawning, that sh exits too and SIGHUPs its
    // process group. A bare child (e.g. `polyomino-theme-picker`, which then
    // blocks on its own tile menu) dies with it. `setsid` puts the child in a fresh
    // session/process-group so it outlives us — the same detachment sway's
    // `exec` gives the equivalent keybindings.
    val hasSetsid = os.proc("sh", "-c", "command -v setsid").call(check = false).exitCode == 0
    def spawn(cmd: Seq[String]): Unit =
      val full = if hasSetsid then "setsid" +: cmd else cmd
      val shellable: Seq[os.Shellable] = full.map(s => (s: os.Shellable))
      os.proc(shellable*).spawn(stdout = os.Inherit, stderr = os.Inherit)

    try
      tilePick(ctx, "[ ⊞ ] polyomino", entries, columns = 3, width = 640, height = 300) match
        case "" => Right(())
        case "welcome" =>
          spawn(Seq((binDir / "polyomino-welcome").toString))
          Right(())
        case "gamemode" =>
          spawn(Seq(polyomino, "gamemode", "toggle"))
          Right(())
        case "power" =>
          spawn(Seq(term, "--class=polyomino-power-menu", "-o", "font_size=14", "-e", polyomino, "power-menu"))
          Right(())
        case "theme" =>
          spawn(Seq((binDir / "polyomino-theme-picker").toString) ++ outputArgs)
          Right(())
        case "wallpaper" =>
          spawn(Seq((binDir / "polyomino-wallpaper-picker").toString) ++ outputArgs)
          Right(())
        case "health" =>
          spawn(Seq(term, "-e", "sh", "-c", s"'$polyomino' healthcheck; printf '\\n[enter to close] '; read _"))
          Right(())
        case "edit" =>
          val editor = sys.env.get("EDITOR").filter(_.nonEmpty)
            .orElse(Some("nvim").filter(e => os.proc("sh", "-c", s"command -v $e").call(check = false).exitCode == 0))
            .getOrElse("vi")
          val candidates = Seq(
            ("sway/config", "sway/config", "Sway Window Manager", "sky"),
            ("waybar/config.jsonc", "waybar/config.jsonc", "Waybar Bar Layout", "teal"),
            ("waybar/modules.jsonc", "waybar/modules.jsonc", "Waybar Modules", "teal"),
            ("waybar/style.css", "waybar/style.css", "Waybar Stylesheet", "teal"),
            ("kitty/kitty.conf", "kitty/kitty.conf", "Kitty Terminal", "green"),
            ("wofi/config", "wofi/config", "Wofi Launcher Config", "mauve"),
            ("wofi/style.css", "wofi/style.css", "Wofi Launcher Style", "mauve"),
            ("swaync/config.json", "swaync/config.json", "SwayNC Notification Center", "sapphire"),
            ("mako/config", "mako/config", "Mako Notifications", "sapphire"),
            ("dunst/dunstrc", "dunst/dunstrc", "Dunst Notifications", "sapphire")
          ).map { case (id, rel, desc, accent) => (id, ctx.configDir / os.RelPath(rel), desc, accent) }
            ++ Seq(("zsh/.zshrc", ctx.dotfilesDir / "zsh" / ".zshrc", "Zsh Shell Config", "peach"))
          val existing = candidates.filter { case (_, p, _, _) => os.exists(p) }
          if existing.isEmpty then Right(())
          else
            val configTiles = existing.map { case (id, _, desc, accent) => tile(id, "⚙", id, desc, accent) }
            val chosenId = tilePick(ctx, "[ ⊞ ] Edit config", configTiles, columns = 2, width = 560, height = 420)
            existing.find(_._1 == chosenId) match
              case Some((_, path, _, _)) =>
                spawn(Seq(term, "-e", editor, path.toString))
                Right(())
              case None => Right(())
        case _ => Right(())
    catch
      case e: Exception => Left(CommandError(s"Menu failed: ${e.getMessage}"))

  def runWhichkey(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    // Toggle behavior: check if whichkey is already running
    try
      val checkRes = os.proc("pgrep", "-f", "(rofi.*whichkey|wofi.*which-key|polyomino-tilemenu.*which-key)").call(check = false)
      if checkRes.exitCode == 0 then
        val pids = checkRes.out.text().trim.split("\\s+").filter(_.nonEmpty)
        for pid <- pids do
          try os.proc("kill", pid).call(check = false) catch case _: Exception => ()
        return Right(())
    catch
      case _: Exception => ()

    println(" [1;35m[polyomino whichkey] [0m Displaying Sway keybindings cheatsheet...")
    val keybindingsList = resolveSwayKeybindings(ctx)
    val entries = if keybindingsList.nonEmpty then keybindingsList else defaultKeybindings

    val rofiWhichKeyTheme = ctx.configDir / "rofi" / "whichkey.rasi"
    val rofiFallbackTheme = ctx.dotfilesDir / "config" / "rofi" / "whichkey.rasi"
    val wofiConfigFile = ctx.configDir / "wofi" / "config-whichkey"
    val wofiStyleFile = ctx.configDir / "wofi" / "style.css"

    val hasRofi = os.proc("sh", "-c", "command -v rofi").call(check = false).exitCode == 0
    val hasWofi = os.proc("sh", "-c", "command -v wofi").call(check = false).exitCode == 0

    if hasRofi then
      val rofiLines = entries.map { line =>
        val parts = line.split("→", 2)
        val key = parts.headOption.getOrElse(line).trim
        val rawAction = if parts.length > 1 then parts(1).trim else ""
        val actionColWidth = 30
        val displayAction = if rawAction.length > actionColWidth then rawAction.take(actionColWidth - 1) + "…" else rawAction.padTo(actionColWidth, ' ')
        s"<span background='rgba(139, 92, 246, 0.15)' foreground='#a78bfa' weight='bold'> KEY </span> <span background='rgba(139, 92, 246, 0.22)' foreground='#c084fc' weight='bold'> $key </span> <b>$displayAction</b> <span foreground='rgba(139, 92, 246, 0.6)'>Execute →</span>"
      }.mkString("\n")

      val themePath = if os.exists(rofiWhichKeyTheme) then rofiWhichKeyTheme else rofiFallbackTheme
      val cmd: Seq[os.Shellable] = Seq("rofi", "-dmenu", "-i", "-markup-rows", "-p", "❯", "-theme", themePath.toString).map(s => (s: os.Shellable))
      try
        os.proc(cmd*).call(stdin = rofiLines, check = false)
        Right(())
      catch
        case e: Exception => Left(CommandError(s"Whichkey rofi failed: ${e.getMessage}"))
    else if hasWofi then
      val wofiLines = entries.map { line =>
        val parts = line.split("→", 2)
        val key = parts.headOption.getOrElse(line).trim
        val rawAction = if parts.length > 1 then parts(1).trim else ""
        val actionColWidth = 30
        val displayAction = if rawAction.length > actionColWidth then rawAction.take(actionColWidth - 1) + "…" else rawAction.padTo(actionColWidth, ' ')
        s"<span background='rgba(139, 92, 246, 0.15)' foreground='#a78bfa' weight='bold'> KEY </span> <span background='rgba(139, 92, 246, 0.22)' foreground='#c084fc' weight='bold'> $key </span> <b>$displayAction</b> <span foreground='rgba(139, 92, 246, 0.6)'>Execute →</span>"
      }.mkString("\n")

      val confArgs = if os.exists(wofiConfigFile) then Seq("--conf", wofiConfigFile.toString) else Seq("--columns", "2", "--lines", "10", "--width", "980")
      val styleArgs = if os.exists(wofiStyleFile) then Seq("--style", wofiStyleFile.toString) else Seq.empty
      val cmd: Seq[os.Shellable] = (Seq("wofi", "--show", "dmenu", "--prompt", "[ ⮽ ] POLYOMINO // WHICH-KEY", "--allow-markup", "--insensitive") ++ confArgs ++ styleArgs).map(s => (s: os.Shellable))
      try
        os.proc(cmd*).call(stdin = wofiLines, check = false)
        Right(())
      catch
        case e: Exception => Left(CommandError(s"Whichkey wofi failed: ${e.getMessage}"))
    else
      val tiles = entries.zipWithIndex.map { case (line, idx) =>
        val parts = line.split("→", 2)
        val key = parts.headOption.getOrElse(line).trim
        val action = if parts.length > 1 then parts(1).trim else ""
        tile(idx.toString, "", action, "", "accent", badge = Some(key))
      }
      try
        tilePick(ctx, "[ ⊞ ] which-key", tiles, columns = 2, width = 820, height = 720, info = true)
        Right(())
      catch
        case e: Exception => Left(CommandError(s"Whichkey failed: ${e.getMessage}"))

  private def resolveSwayKeybindings(ctx: Context): Seq[String] =
    try
      val configContent = getSwayConfigContent(ctx)
      if configContent.nonEmpty then expandBindings(configContent) else Nil
    catch
      case _: Exception => Nil

  private def getSwayConfigContent(ctx: Context): String =
    try
      val res = os.proc("swaymsg", "-t", "get_config").call(check = false)
      if res.exitCode == 0 then
        val json = ujson.read(res.out.text())
        json.obj.get("config").map(_.str).getOrElse("")
      else
        val configFile = ctx.configDir / "sway" / "config"
        if os.exists(configFile) then os.read(configFile) else ""
    catch
      case _: Exception =>
        val configFile = ctx.configDir / "sway" / "config"
        if os.exists(configFile) then os.read(configFile) else ""

  def expandBindings(config: String): Seq[String] =
    var varDefs = Map.empty[String, String]
    var bindings = Vector.empty[String]
    var inSubmode = false
    val seenActions = scala.collection.mutable.Set[String]()

    for rawLine <- config.linesIterator do
      // Strip comments from end of line
      val line = rawLine.takeWhile(_ != '#').trim
      if line.startsWith("mode ") && line.endsWith("{") then
        inSubmode = true
      else if inSubmode && line == "}" then
        inSubmode = false
      else if !inSubmode && line.nonEmpty then
        if line.startsWith("set ") then
          val parts = line.split("\\s+", 3)
          if parts.length >= 3 && parts(1).startsWith("$") then
            varDefs += (parts(1) -> parts(2).replace("\"", ""))
        else if line.startsWith("bindsym ") || line.startsWith("bindcode ") then
          var bindingLine = line
          bindingLine = bindingLine.replaceFirst("^(bindsym|bindcode)\\s+", "")
          while bindingLine.startsWith("--") do
            bindingLine = bindingLine.replaceFirst("^--[a-z-]+\\s+", "")

          for (varName, varVal) <- varDefs do
            bindingLine = bindingLine.replace(varName, varVal)

          val parts = bindingLine.split("\\s+", 2)
          if parts.length == 2 then
            val key = parts(0).trim
            val action = parts(1).trim

            // Skip raw numerical fallback keycodes (e.g. "107", "Mod4+61", "Mod4+193")
            val isRawCodeOnly = key.matches("^\\d+$") || key.matches("^.*\\+\\d+$")

            if !isRawCodeOnly then
              // Deduplicate whichkey and screenshot internal aliases
              val isWhichKey = action.contains("polyomino-whichkey")
              val isScreenshotFull = action.contains("polyomino screenshot full") || action.contains("polyomino-screenshot full")

              val shouldAdd = if isWhichKey then
                if seenActions.contains("whichkey") then false else { seenActions += "whichkey"; true }
              else if isScreenshotFull then
                if seenActions.contains("screenshot_full") then false else { seenActions += "screenshot_full"; true }
              else
                true

              if shouldAdd then
                val displayKey = if isWhichKey then "Mod4+? / Mod4+/" else key
                val displayAction = friendlyAction(action)
                bindings = bindings :+ f"$displayKey%-24s → $displayAction"

    bindings

  private def friendlyAction(action: String): String =
    action match
      case a if a.contains("sway-draw-window") || a.contains("draw-window") => "Draw interactive floating window (slurp)"
      case a if a.contains("floating-term") => "Floating terminal"
      case a if a.contains("sway-project-launcher") => "Project picker launcher (Neovim)"
      case a if a.contains("polyomino-menu") || a.contains("polyomino menu") => "Polyomino launcher menu"
      case a if a.contains("polyomino-whichkey") => "Keybindings cheatsheet"
      case a if a.contains("polyomino-theme-picker") => "Theme and wallpaper picker"
      case a if a.contains("wallpaper next") || a.contains("wallpaper cycle") => "Next wallpaper (active theme)"
      case a if a.contains("wallpaper prev") || a.contains("wallpaper previous") => "Previous wallpaper (active theme)"
      case a if a.contains("wallpaper random") => "Random wallpaper (active theme)"
      case a if a.contains("polyomino-wallpaper") || a.contains("wallpaper-picker") || a.contains("polyomino wallpaper") => "Wallpaper picker (active theme)"
      case a if a.contains("preview-lock") => "Preview lockscreen (Safe test window)"
      case a if a.contains("rubik-lock") || a.contains("polyomino-lock") || a.contains("lock") => "Lock screen (3D Rubik's Cube Lock)"
      case a if a.contains("power-menu") || a.contains("powermenu") => "Power menu (Session & Power Control)"
      case a if a.contains("systemctl suspend") => "Suspend system"
      case a if a.contains("systemctl poweroff") => "Shutdown system"
      case a if a.contains("systemctl reboot") => "Reboot system"
      case a if a.contains("polyomino screenshot full") => "Screenshot full screen"
      case a if a.contains("polyomino screenshot region") => "Screenshot region selection"
      case a if a.contains("polyomino screenshot window") => "Screenshot active window"
      case a if a.contains("swaync-client -t -sw") => "Toggle notification center"
      case a if a.contains("kitty -e yazi") => "File manager (yazi)"
      case a if a.contains("kitty -e spotify_player") => "Spotify player TUI"
      case a if a.contains("kitty -e bluetui") => "Bluetooth manager (bluetui)"
      case a if a.contains("kitty -e impala") => "Network manager (impala)"
      case a if a.contains("kitty -e ncpamixer") => "Audio mixer (ncpamixer)"
      case a if a.contains("kitty -e aerc") => "Email client (aerc)"
      case a if a.contains("google-chrome-stable") => "Google Chrome"
      case a if a.contains("wdisplays") => "Display configuration (wdisplays)"
      case a if a.contains("swaynag") => "Exit Sway dialog"
      case a if a.contains("wofi --show drun") => "Application launcher"
      case a if a == "exec kitty" => "Open Kitty terminal"
      case a if a == "kill" => "Close focused window"
      case a if a == "reload" => "Reload Sway config"
      case a if a == "fullscreen" => "Toggle fullscreen"
      case a if a == "floating toggle" => "Toggle floating mode"
      case a if a == "focus mode_toggle" => "Toggle focus tiling/floating"
      case a if a == "focus parent" => "Focus parent container"
      case a if a == "splith" => "Split horizontal"
      case a if a == "splitv" => "Split vertical"
      case a if a == "layout toggle split" => "Toggle split layout"
      case a if a == "layout stacking" => "Layout stacking"
      case a if a == "layout tabbed" => "Layout tabbed"
      case a if a.startsWith("workspace number ") => s"Switch to Workspace ${a.stripPrefix("workspace number ")}"
      case a if a.startsWith("move container to workspace number ") => s"Move container to Workspace ${a.stripPrefix("move container to workspace number ")}"
      case a if a.startsWith("focus ") => s"Focus ${a.stripPrefix("focus ")}"
      case a if a.startsWith("move ") => s"Move ${a.stripPrefix("move ")}"
      case a if a == "move scratchpad" => "Move window to scratchpad"
      case a if a == "scratchpad show" => "Show scratchpad"
      case a if a == "mode \"resize\"" => "Enter resize mode"
      case other => other

  val defaultKeybindings: Seq[String] = Seq(
    "Mod4+Return              → Open Kitty terminal",
    "Mod4+d                   → Application launcher",
    "Mod4+Shift+q             → Close focused window",
    "Mod4+Shift+e             → Exit Sway dialog",
    "Mod4+f                   → Toggle fullscreen",
    "Mod4+v                   → Split vertical",
    "Mod4+b                   → Split horizontal",
    "Mod4+Shift+f             → File manager (yazi)",
    "Mod4+Shift+m             → Spotify (spotify_player)",
    "Mod4+Shift+u             → Bluetooth manager (bluetui)",
    "Mod4+Shift+v             → Audio mixer (ncpamixer)",
    "Mod4+Shift+a             → Email client (aerc)",
    "Mod4+Shift+n             → Toggle notification center",
    "Mod4+Shift+t             → Theme and wallpaper picker",
    "Mod4+Shift+p             → Wallpaper picker (active theme)",
    "Mod4+F6                  → Next wallpaper (active theme)",
    "Mod4+? / Mod4+/          → Keybindings cheatsheet",
    "Mod4+Escape              → Lock screen (3D Rubik's Cube Lock)",
    "Print                    → Screenshot full screen",
    "Mod4+Print               → Screenshot region selection",
    "Mod4+Shift+Print         → Screenshot active window"
  )
