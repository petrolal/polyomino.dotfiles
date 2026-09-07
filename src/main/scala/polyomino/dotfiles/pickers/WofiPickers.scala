package polyomino.dotfiles.pickers

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}
import polyomino.dotfiles.theme.ThemeEngine

object WofiPickers:
  def runThemePicker(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    // Toggle behavior: check if theme picker is already open
    try
      val checkRes = os.proc("pgrep", "-f", "wofi.*--prompt.*Theme").call(check = false)
      if checkRes.exitCode == 0 then
        val pids = checkRes.out.text().trim.split("\\s+").filter(_.nonEmpty)
        for pid <- pids do
          try os.proc("kill", pid).call(check = false) catch case _: Exception => ()
        return Right(())
    catch
      case _: Exception => ()

    println("\u001b[1;35m[polyomino theme-picker]\u001b[0m Launching Wofi GUI theme picker...")
    val themes = polyomino.dotfiles.theme.Palette.listAll(ctx)
    val inputList = themes.map(escapeMarkup).mkString("\n")

    val wofiConfigFile = ctx.configDir / "wofi" / "config"
    val wofiStyleFile = ctx.configDir / "wofi" / "style.css"

    try
      // Step 1: Select Theme
      val themeArgs = Seq("wofi")
        ++ (if os.exists(wofiConfigFile) then Seq("--conf", wofiConfigFile.toString) else Seq.empty)
        ++ Seq(
          "--show", "dmenu",
          "--prompt", "Select Theme",
          "--width", "560",
          "--lines", "3",
          "--columns", "2",
          "--insensitive",
          "--cache-file", "/dev/null"
        ) ++ (if os.exists(wofiStyleFile) then Seq("--style", wofiStyleFile.toString) else Seq.empty)
      val shellableThemeArgs: Seq[os.Shellable] = themeArgs.map(s => (s: os.Shellable))
      val resTheme = os.proc(shellableThemeArgs*).call(stdin = inputList, check = false)

      val selectedTheme = resTheme.out.text().trim
      if selectedTheme.isEmpty then return Right(())

      println(s"  \u001b[32m[OK]\u001b[0m Selected theme '$selectedTheme'")

      // Step 2: Select Wallpaper Mode
      val modes = Seq("1. Static Wallpaper", "2. Rotate Wallpapers (30m)")
      val modeArgs = Seq("wofi")
        ++ (if os.exists(wofiConfigFile) then Seq("--conf", wofiConfigFile.toString) else Seq.empty)
        ++ Seq(
          "--show", "dmenu",
          "--prompt", s"Wallpaper mode for '$selectedTheme'",
          "--width", "560",
          "--lines", "2",
          "--columns", "2",
          "--insensitive",
          "--cache-file", "/dev/null"
        ) ++ (if os.exists(wofiStyleFile) then Seq("--style", wofiStyleFile.toString) else Seq.empty)
      val shellableModeArgs: Seq[os.Shellable] = modeArgs.map(s => (s: os.Shellable))
      val resMode = os.proc(shellableModeArgs*).call(stdin = modes.mkString("\n"), check = false)

      val selectedModeStr = resMode.out.text().trim
      if selectedModeStr.isEmpty then return Right(())
      val (mode, interval) = if selectedModeStr.contains("Rotate") then
        ("rotate", "30m")
      else
        ("wallpaper", "30m")

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
            val AutoLabel = "◆  Auto (first)"
            val labels = AutoLabel +: choices.map(p => s"   ${p.last}")
            val wpArgs = Seq("wofi")
              ++ (if os.exists(wofiConfigFile) then Seq("--conf", wofiConfigFile.toString) else Seq.empty)
              ++ Seq(
                "--show", "dmenu",
                "--prompt", s"Wallpaper for '$selectedTheme'",
                "--width", "560",
                "--lines", Math.min(labels.size, 8).toString,
                "--columns", "1",
                "--insensitive",
                "--cache-file", "/dev/null"
              ) ++ (if os.exists(wofiStyleFile) then Seq("--style", wofiStyleFile.toString) else Seq.empty)
            val shellableWpArgs: Seq[os.Shellable] = wpArgs.map(s => (s: os.Shellable))
            val resWp = os.proc(shellableWpArgs*).call(stdin = labels.map(escapeMarkup).mkString("\n"), check = false)
            val picked = resWp.out.text().trim
            if picked.isEmpty || picked == AutoLabel then None
            else
              val name = picked.replaceFirst("^(◆  | +)", "").trim
              choices.find(p => p.last == name || p.baseName == name).map(_.toString)

      ThemeEngine.applyTheme(ctx, selectedTheme, mode = mode, customWallpaper = customWallpaper, interval = interval)
    catch
      case e: Exception => Left(CommandError(s"Wofi theme-picker failed: ${e.getMessage}"))

  def runWallpaperPicker(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    import polyomino.dotfiles.wallpaper.WallpaperEngine

    // Toggle behavior: close an already-open wallpaper picker
    try
      val checkRes = os.proc("pgrep", "-f", "wofi.*--prompt.*Wallpaper").call(check = false)
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

    println(s"[1;35m[polyomino wallpaper-picker][0m Launching Wofi GUI wallpaper picker for '$flavor'...")
    val current = WallpaperEngine.currentWallpaper(ctx)
    val RandomLabel = "🎲  Random"
    val labels = RandomLabel +: options.map { p =>
      val mark = if current.contains(p.toString) then "● " else "  "
      s"$mark${p.last}"
    }
    val inputList = labels.map(escapeMarkup).mkString("\n")

    val wofiConfigFile = ctx.configDir / "wofi" / "config"
    val wofiStyleFile = ctx.configDir / "wofi" / "style.css"

    try
      val pickerArgs = Seq("wofi")
        ++ (if os.exists(wofiConfigFile) then Seq("--conf", wofiConfigFile.toString) else Seq.empty)
        ++ Seq(
          "--show", "dmenu",
          "--prompt", s"Wallpaper · $flavor",
          "--width", "560",
          "--lines", Math.min(labels.size, 8).toString,
          "--columns", "1",
          "--insensitive",
          "--cache-file", "/dev/null"
        ) ++ (if os.exists(wofiStyleFile) then Seq("--style", wofiStyleFile.toString) else Seq.empty)
      val shellablePicker: Seq[os.Shellable] = pickerArgs.map(s => (s: os.Shellable))
      val res = os.proc(shellablePicker*).call(stdin = inputList, check = false)

      val selected = res.out.text().trim
      if selected.isEmpty then return Right(())
      if selected == RandomLabel then
        WallpaperEngine.run(ctx, List("random"))
      else
        val name = selected.replaceFirst("^(● |  )", "").trim
        WallpaperEngine.run(ctx, List(name))
    catch
      case e: Exception => Left(CommandError(s"Wofi wallpaper-picker failed: ${e.getMessage}"))

  /** `polyomino menu` — the click target for the waybar POLYOMINO pill.
    *
    * A small wofi list that fans out to the desktop's own tools: the Tetris
    * power menu, the theme/wallpaper pickers, an "edit a config file" sub-list,
    * and the healthcheck. Everything it launches is an existing subcommand or
    * `polyomino-*` alias — the menu is pure front-end. Re-invoking it while it
    * is open closes it (matches the calendar / picker toggle behaviour). */
  def runMenu(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    // Toggle: a second click closes the open menu.
    try
      val checkRes = os.proc("pgrep", "-f", "wofi.*--prompt.*polyomino").call(check = false)
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
    // pass `--output <name>`; without it wofi falls back to the focused output.
    val outputArgs: Seq[String] =
      args.sliding(2).collectFirst {
        case Seq("--output" | "-o", name) if name.nonEmpty => Seq("-o", name)
      }.getOrElse(Seq.empty)

    val PowerMenu = "⏻   Power menu"
    val ThemePick = "🎨  Theme & wallpaper"
    val Wallpaper = "🖼   Wallpaper"
    val EditConf  = "⚙   Edit a config file…"
    val Health    = "🩺  Healthcheck"
    val entries = Seq(PowerMenu, ThemePick, Wallpaper, EditConf, Health)

    val wofiConfigFile = ctx.configDir / "wofi" / "config"
    val wofiStyleFile = ctx.configDir / "wofi" / "style.css"

    def wofiPick(prompt: String, lines: Int, input: Seq[String]): String =
      val a = Seq("wofi")
        ++ outputArgs
        ++ (if os.exists(wofiConfigFile) then Seq("--conf", wofiConfigFile.toString) else Seq.empty)
        ++ Seq(
          "--show", "dmenu",
          "--prompt", prompt,
          "--width", "420",
          "--lines", lines.toString,
          "--columns", "1",
          "--insensitive",
          "--cache-file", "/dev/null"
        ) ++ (if os.exists(wofiStyleFile) then Seq("--style", wofiStyleFile.toString) else Seq.empty)
      val shellable: Seq[os.Shellable] = a.map(s => (s: os.Shellable))
      os.proc(shellable*).call(stdin = input.map(escapeMarkup).mkString("\n"), check = false).out.text().trim

    // `polyomino menu` is a grandchild of waybar's `sh -c` on-click; when this
    // process exits right after spawning, that sh exits too and SIGHUPs its
    // process group. A bare child (e.g. `polyomino-theme-picker`, which then
    // blocks on its own wofi) dies with it. `setsid` puts the child in a fresh
    // session/process-group so it outlives us — the same detachment sway's
    // `exec` gives the equivalent keybindings.
    val hasSetsid = os.proc("sh", "-c", "command -v setsid").call(check = false).exitCode == 0
    def spawn(cmd: Seq[String]): Unit =
      val full = if hasSetsid then "setsid" +: cmd else cmd
      val shellable: Seq[os.Shellable] = full.map(s => (s: os.Shellable))
      os.proc(shellable*).spawn(stdout = os.Inherit, stderr = os.Inherit)

    try
      wofiPick("polyomino", entries.size, entries) match
        case s if s.isEmpty => Right(())
        case PowerMenu =>
          spawn(Seq(term, "--class=polyomino-power-menu", "-o", "font_size=14", "-e", polyomino, "power-menu"))
          Right(())
        case ThemePick =>
          spawn(Seq((binDir / "polyomino-theme-picker").toString))
          Right(())
        case Wallpaper =>
          spawn(Seq((binDir / "polyomino-wallpaper-picker").toString))
          Right(())
        case Health =>
          spawn(Seq(term, "-e", "sh", "-c", s"'$polyomino' healthcheck; printf '\\n[enter to close] '; read _"))
          Right(())
        case EditConf =>
          val editor = sys.env.get("EDITOR").filter(_.nonEmpty)
            .orElse(Some("nvim").filter(e => os.proc("sh", "-c", s"command -v $e").call(check = false).exitCode == 0))
            .getOrElse("vi")
          val candidates = Seq(
            "sway/config", "waybar/config.jsonc", "waybar/modules.jsonc",
            "waybar/style.css.tmpl", "wofi/config", "wofi/style.css.tmpl",
            "kitty/kitty.conf", "mako/config", "swaync/config.json"
          ).map(rel => rel -> (ctx.configDir / os.RelPath(rel)))
            ++ Seq("zsh/.zshrc" -> (ctx.dotfilesDir / "zsh" / ".zshrc"))
          val existing = candidates.filter { case (_, p) => os.exists(p) }
          if existing.isEmpty then Right(())
          else
            val pick = wofiPick("edit", math.min(existing.size, 10), existing.map(_._1))
            existing.find(_._1 == pick) match
              case Some((_, path)) =>
                spawn(Seq(term, "-e", editor, path.toString))
                Right(())
              case None => Right(())
        case _ => Right(())
    catch
      case e: Exception => Left(CommandError(s"Wofi menu failed: ${e.getMessage}"))

  def runWhichkey(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    // Toggle behavior: check if whichkey wofi is already running
    try
      val checkRes = os.proc("pgrep", "-f", "wofi.*--prompt.*which-key").call(check = false)
      if checkRes.exitCode == 0 then
        val pids = checkRes.out.text().trim.split("\\s+").filter(_.nonEmpty)
        for pid <- pids do
          try os.proc("kill", pid).call(check = false) catch case _: Exception => ()
        return Right(())
    catch
      case _: Exception => ()

    println("\u001b[1;35m[polyomino whichkey]\u001b[0m Displaying Sway keybindings cheatsheet...")
    val keybindingsList = resolveSwayKeybindings(ctx)
    val entries = if keybindingsList.nonEmpty then keybindingsList else defaultKeybindings
    val inputList = entries.map(escapeMarkup).mkString("\n")

    val wofiConfigFile = ctx.configDir / "wofi" / "config"
    val wofiStyleFile = ctx.configDir / "wofi" / "style.css"

    val whichkeyArgs = Seq("wofi")
      ++ (if os.exists(wofiConfigFile) then Seq("--conf", wofiConfigFile.toString) else Seq.empty)
      ++ Seq(
        "--show", "dmenu",
        "--prompt", "[⊞] which-key",
        "--width", "880",
        "--lines", "8",
        "--columns", "2",
        "--insensitive",
        "--cache-file", "/dev/null"
      ) ++ (if os.exists(wofiStyleFile) then Seq("--style", wofiStyleFile.toString) else Seq.empty)

    try
      val shellableWhichkey: Seq[os.Shellable] = whichkeyArgs.map(s => (s: os.Shellable))
      os.proc(shellableWhichkey*).spawn(stdin = inputList)
      Right(())
    catch
      case e: Exception => Left(CommandError(s"Wofi whichkey failed: ${e.getMessage}"))

  private def escapeMarkup(str: String): String =
    str
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")

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
      case a if a.contains("polyomino-whichkey") => "Keybindings cheatsheet"
      case a if a.contains("polyomino-theme-picker") => "Theme and wallpaper picker"
      case a if a.contains("wallpaper next") || a.contains("wallpaper cycle") => "Next wallpaper (active theme)"
      case a if a.contains("wallpaper prev") || a.contains("wallpaper previous") => "Previous wallpaper (active theme)"
      case a if a.contains("wallpaper random") => "Random wallpaper (active theme)"
      case a if a.contains("polyomino-wallpaper") || a.contains("wallpaper-picker") || a.contains("polyomino wallpaper") => "Wallpaper picker (active theme)"
      case a if a.contains("preview-lock") => "Preview lockscreen (Safe test window)"
      case a if a.contains("rubik-lock") || a.contains("polyomino-lock") || a.contains("lock") => "Lock screen (3D Rubik's Cube Lock)"
      case a if a.contains("power-menu") || a.contains("powermenu") => "Power menu (Tetris drop)"
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
