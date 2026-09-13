package polyomino.dotfiles.install

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}
import upickle.default._

object DeployInstaller:
  val DotfilesRepoUrl: String = "https://github.com/petrolal/polyomino.dotfiles.git"

  // Guarantees `cs bootstrap io.github.petrolal::polyomino -o ~/.local/bin/polyomino`
  // followed by `polyomino install` works with no prior manual `git clone` step:
  // if ctx.dotfilesDir isn't already a checkout, fetch it before wiring symlinks.
  def ensureDotfilesRepo(ctx: Context): Either[PolyominoError, Unit] =
    if os.exists(ctx.dotfilesDir / "config") && os.exists(ctx.dotfilesDir / "zsh") then
      Right(())
    else if os.exists(ctx.dotfilesDir) then
      Left(CommandError(
        s"${ctx.dotfilesDir} exists but doesn't look like a polyomino.dotfiles checkout " +
        s"(missing config/ or zsh/). Remove it or set POLYOMINO_DOTFILES_DIR to the correct path."
      ))
    else
      println(s"[1;36m[polyomino install][0m No dotfiles checkout found at ${ctx.dotfilesDir} - cloning $DotfilesRepoUrl...")
      try
        val res = os.proc("git", "clone", DotfilesRepoUrl, ctx.dotfilesDir.toString).call(check = false)
        if res.exitCode == 0 then
          println(s"  [32m[OK][0m Cloned dotfiles to ${ctx.dotfilesDir}")
          Right(())
        else
          Left(CommandError(s"git clone of $DotfilesRepoUrl failed with exit code ${res.exitCode}", res.exitCode))
      catch
        case e: Exception => Left(CommandError(s"Failed to clone dotfiles repo: ${e.getMessage}"))

  val Subcommands: Seq[String] = Seq(
    "theme", "runtime-refresh", "os-colorscheme", "lock", "preview-lock", "rubik-lock", "idle",
    "screenshot", "draw-window", "sway-draw-window", "calendar", "autotiling", "healthcheck", "backup", "restore", "update", "release", "sdk", "notify-config",
    "install", "deploy", "uninstall", "welcome", "gamemode", "install-deps", "install-gaming", "install-games", "install-gamemode", "install-brew", "install-homebrew",
    "install-gh", "install-github-cli",
    "install-fonts", "install-apps", "install-sway", "install-swayfx", "install-swaync", "install-notifications", "install-browser", "install-devops", "install-zsh", "install-sdkman",
    "install-tools", "install-telegram", "install-node", "install-npm", "install-npx", "install-nvm", "install-yazi", "install-fastfetch", "install-spotify", "install-spotify-player", "full-install", "theme-picker", "theme-cycle", "wallpaper", "wallpaper-picker", "whichkey", "wichkey", "launcher", "app-launcher", "drun", "menu", "projects", "project-launcher", "sway-project-launcher", "media-status", "fastfetch-logo", "power-menu", "powermenu",
    "rom-launcher", "patch-rom", "sokoban", "2048", "sweeper", "lightsout", "nonogram"
  )

  def run(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    if args.contains("--uninstall") || args.contains("uninstall") then
      uninstall(ctx, args)
    else
      println("\u001b[1;32m[polyomino install]\u001b[0m Deploying polyomino.dotfiles configurations & symlinks...")

      if args.exists(a => a == "--bootstrap" || a == "-b" || a == "--with-bootstrap" || a == "--deps") then
        val bootstrapScript = ctx.dotfilesDir / "bootstrap.sh"
        if os.exists(bootstrapScript) then
          println(s"\u001b[1;36m[polyomino install]\u001b[0m Executing bootstrap.sh...")
          try os.proc("bash", bootstrapScript.toString).call(stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
          catch case e: Exception => println(s"  \u001b[33m[WARN]\u001b[0m bootstrap.sh exited: ${e.getMessage}")

      ensureDotfilesRepo(ctx) match
        case Left(err) => Left(err)
        case Right(_) => runDeploy(ctx, args)

  private def runDeploy(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    val binDir = ctx.home / ".local" / "bin"
    os.makeDir.all(binDir)

    val mainBinary = binDir / "polyomino"
    println(s"  [32m[OK][0m Target executable: $mainBinary")

    var manifestEntries = List.empty[ManifestEntry]

    // Ensure ~/Projects workspace directory exists
    os.makeDir.all(ctx.home / "Projects")

    // Ensure Tetravim Neovim distribution is cloned and symlinked
    val tetravimDir = ctx.home / "tetravim.nvim"
    val nvimConfigDir = ctx.configDir / "nvim"
    if !ctx.isTest && !os.exists(tetravimDir) then
      try
        println(s"\u001b[1;36m[polyomino install]\u001b[0m Cloning Tetravim Neovim distribution...")
        val res = os.proc("git", "clone", "git@github.com:petrolal/tetravim.nvim.git", tetravimDir.toString).call(check = false)
        if res.exitCode != 0 then
          println(s"  \u001b[33m[WARN]\u001b[0m SSH clone failed; falling back to HTTPS...")
          os.proc("git", "clone", "https://github.com/petrolal/tetravim.nvim.git", tetravimDir.toString).call(check = false)
      catch
        case e: Exception => println(s"  \u001b[33m[NOTE]\u001b[0m Tetravim clone skipped: ${e.getMessage}")

    // 1. Clean & deploy dotfile configuration symlinks from scratch
    val timestamp = System.currentTimeMillis()
    val backupBaseDir = ctx.home / ".polyomino_backup" / timestamp.toString

    if os.exists(tetravimDir) then
      try
        if os.exists(nvimConfigDir) && !os.isLink(nvimConfigDir) then
          val backupTarget = backupBaseDir / "nvim"
          os.makeDir.all(backupBaseDir)
          os.copy(nvimConfigDir, backupTarget)
          os.remove.all(nvimConfigDir)
        else if os.isLink(nvimConfigDir) then
          os.remove(nvimConfigDir)
        os.makeDir.all(ctx.configDir)
        os.proc("ln", "-s", tetravimDir.toString, nvimConfigDir.toString).call()
        manifestEntries = manifestEntries :+ ManifestEntry(
          sourcePath = tetravimDir.toString,
          targetPath = nvimConfigDir.toString,
          backupPath = None
        )
        println(s"  \u001b[32m[OK]\u001b[0m Symlinked $nvimConfigDir -> $tetravimDir")
      catch
        case e: Exception => println(s"  \u001b[33m[NOTE]\u001b[0m Tetravim symlink skipped: ${e.getMessage}")

    val configMappings = Seq(
      (ctx.home / ".zshrc", ctx.dotfilesDir / "zsh" / ".zshrc"),
      (ctx.home / ".oh-my-zsh" / "custom" / "themes" / "polyomino.zsh-theme", ctx.dotfilesDir / "zsh" / "themes" / "polyomino.zsh-theme"),
      (ctx.configDir / "polyomino" / "zsh_config", ctx.dotfilesDir / "zsh" / "zsh_config"),
      (ctx.configDir / "starship.toml", ctx.dotfilesDir / "config" / "starship.toml"),
      (ctx.configDir / "sway", ctx.dotfilesDir / "config" / "sway"),
      (ctx.configDir / "kitty", ctx.dotfilesDir / "config" / "kitty"),
      (ctx.configDir / "waybar", ctx.dotfilesDir / "config" / "waybar"),
      (ctx.configDir / "wofi", ctx.dotfilesDir / "config" / "wofi"),
      (ctx.configDir / "rofi", ctx.dotfilesDir / "config" / "rofi"),
      (ctx.configDir / "swaync", ctx.dotfilesDir / "config" / "swaync"),
      (ctx.configDir / "mako", ctx.dotfilesDir / "config" / "mako"),
      (ctx.configDir / "fuzzel", ctx.dotfilesDir / "config" / "fuzzel"),
      (ctx.configDir / "alacritty", ctx.dotfilesDir / "config" / "alacritty"),
      (ctx.configDir / "foot", ctx.dotfilesDir / "config" / "foot"),
      (ctx.configDir / "fastfetch", ctx.dotfilesDir / "config" / "fastfetch"),
      (ctx.configDir / "spotify-player", ctx.dotfilesDir / "config" / "spotify-player"),
      (ctx.configDir / "gamemode", ctx.dotfilesDir / "config" / "gamemode"),
      (ctx.configDir / "MangoHud", ctx.dotfilesDir / "config" / "MangoHud"),
      (ctx.configDir / "systemd" / "user" / "mako.service", ctx.dotfilesDir / "config" / "systemd" / "user" / "mako.service")
    )

    var configSymlinkCount = 0
    for (targetPath, sourcePath) <- configMappings do
      if os.exists(sourcePath) then
        var backupPathOpt: Option[String] = None

        if os.exists(targetPath) && !os.isLink(targetPath) then
          try
            val backupTarget = backupBaseDir / targetPath.last
            os.makeDir.all(backupBaseDir)
            os.copy(targetPath, backupTarget)
            backupPathOpt = Some(backupTarget.toString)
            println(s"  [32m[OK][0m Preserved pre-existing config -> $backupTarget")
          catch
            case e: Exception => println(s"  [33m[NOTE][0m Config backup skipped for ${targetPath.last}: ${e.getMessage}")

        // Remove any existing path (file or directory) to make room for symlink
        try
          if os.exists(targetPath) then os.remove.all(targetPath)
          if os.isLink(targetPath) then os.remove(targetPath)
        catch
          case e: Exception => println(s"  [33m[WARN][0m Could not remove existing $targetPath: ${e.getMessage}")

        // Create symlink if path was successfully removed
        if !os.exists(targetPath) && !os.isLink(targetPath) then
          try
            os.makeDir.all(targetPath / os.up)
            // Use ln command for more reliable symlink creation
            os.proc("ln", "-s", sourcePath.toString, targetPath.toString).call()
            configSymlinkCount += 1
            manifestEntries = manifestEntries :+ ManifestEntry(
              sourcePath = sourcePath.toString,
              targetPath = targetPath.toString,
              backupPath = backupPathOpt
            )
            println(s"  [32m[OK][0m Symlinked $targetPath -> $sourcePath")
          catch
            case e: Exception => println(s"  [33m[NOTE][0m Symlink creation failed for ${targetPath.last}: ${e.getMessage}")
        else
          println(s"  [33m[WARN][0m Skipping symlink for ${targetPath.last} - could not remove existing path")

    // 2. Purge obsolete polyomino-* symlinks not in Subcommands
    val validSymlinkNames = Subcommands.map(cmd => s"polyomino-$cmd").toSet
    var purgedCount = 0
    if os.exists(binDir) then
      for file <- os.list(binDir) do
        val filename = file.last
        if filename.startsWith("polyomino-") && !validSymlinkNames.contains(filename) then
          try
            os.remove(file)
            purgedCount += 1
          catch case _: Exception => ()

    if purgedCount > 0 then
      println(s"  [32m[OK][0m Cleaned up $purgedCount obsolete symlinks in $binDir")

    // 3. Clean & deploy CLI subcommand symlinks in ~/.local/bin/
    var binSymlinkCount = 0

    for cmd <- Subcommands do
      val symlinkPath = binDir / s"polyomino-$cmd"
      val scriptSource = if cmd == "rubik-lock" then
        ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-rubik-lock"
      else if cmd == "welcome" then
        ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-welcome.py"
      else if cmd == "rom-launcher" then
        ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-rom-launcher.sh"
      else if cmd == "patch-rom" then
        ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-patch-rom.sh"
      else if cmd == "sokoban" then
        ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-sokoban"
      else if cmd == "2048" then
        ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-2048"
      else if cmd == "sweeper" then
        ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-sweeper"
      else if cmd == "lightsout" then
        ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-lightsout"
      else if cmd == "nonogram" then
        ctx.dotfilesDir / "config" / "sway" / "scripts" / "polyomino-nonogram"
      else mainBinary
      try
        if os.exists(symlinkPath) || os.isLink(symlinkPath) then os.remove(symlinkPath)
        // Use ln command for reliable symlink creation
        os.proc("ln", "-s", scriptSource.toString, symlinkPath.toString).call()
        binSymlinkCount += 1
        manifestEntries = manifestEntries :+ ManifestEntry(
          sourcePath = scriptSource.toString,
          targetPath = symlinkPath.toString,
          backupPath = None
        )
      catch
        case e: Exception => println(s"  [33m[NOTE][0m Failed to create symlink for polyomino-$cmd: ${e.getMessage}")

    // 4. Save manifest JSON
    val manifestDir = ctx.shareDir
    os.makeDir.all(manifestDir)
    val manifestFile = manifestDir / "manifest.json"
    val manifestData = Manifest(
      version = "0.1.0",
      timestamp = System.currentTimeMillis(),
      entries = manifestEntries
    )

    try
      val jsonText = write(manifestData, indent = 2)
      os.write.over(manifestFile, jsonText)
      println(s"  [32m[OK][0m Manifest written -> $manifestFile (${manifestEntries.size} entries)")
    catch
      case e: Exception => println(s"  [33m[NOTE][0m Manifest write failed: ${e.getMessage}")

    println(s"  [32m[OK][0m Created $configSymlinkCount config symlinks and $binSymlinkCount CLI subcommand symlinks")
    println("\n[1;32m[SUCCESS][0m polyomino.dotfiles deployment complete!")

    // Apply active desktop theme to re-render all config files
    if !ctx.isTest then
      val activePalette = polyomino.dotfiles.theme.ThemeEngine.getActivePalette(ctx)
      polyomino.dotfiles.theme.ThemeEngine.applyTheme(ctx, activePalette.name)

    if !args.contains("--links-only") then
      println("\n[1;32m[polyomino full-install][0m Installing all system dependencies, desktop apps, fonts, and tooling...")
      for
        _ <- ToolInstallers.runTool("full-install", ctx, args)
        _ <- polyomino.dotfiles.validate.Validator.run(ctx, args)
      yield ()
    else
      Right(())

  def uninstall(ctx: Context, args: List[String] = Nil): Either[PolyominoError, Unit] =
    println("\u001b[1;33m[polyomino uninstall]\u001b[0m Removing polyomino dotfiles symlinks and restoring configurations...")
    val manifestFile = ctx.shareDir / "manifest.json"

    var restoredCount = 0
    var removedCount = 0

    if os.exists(manifestFile) then
      try
        val content = os.read(manifestFile)
        val manifest = upickle.default.read[Manifest](content)

        for entry <- manifest.entries do
          val targetPath = os.Path(entry.targetPath, os.pwd)
          // Remove target symlink/file if it exists
          if os.exists(targetPath) || os.isLink(targetPath) then
            try
              os.remove.all(targetPath)
              removedCount += 1
              println(s"  \u001b[32m[OK]\u001b[0m Removed $targetPath")
            catch
              case e: Exception => println(s"  \u001b[33m[WARN]\u001b[0m Failed to remove $targetPath: ${e.getMessage}")

          // Restore backup if available
          entry.backupPath.foreach { bPathStr =>
            val bPath = os.Path(bPathStr, os.pwd)
            if os.exists(bPath) then
              try
                os.makeDir.all(targetPath / os.up)
                os.copy(bPath, targetPath)
                restoredCount += 1
                println(s"  \u001b[32m[OK]\u001b[0m Restored backup to $targetPath")
              catch
                case e: Exception => println(s"  \u001b[33m[WARN]\u001b[0m Failed to restore backup from $bPath: ${e.getMessage}")
          }

        os.remove(manifestFile)
        println(s"  \u001b[32m[OK]\u001b[0m Removed manifest: $manifestFile")
      catch
        case e: Exception =>
          return Left(CommandError(s"Failed to read or process manifest at $manifestFile: ${e.getMessage}"))
    else
      println(s"  \u001b[33m[NOTE]\u001b[0m No manifest found at $manifestFile. Cleaning up standard symlinks...")

    // Also clean up any lingering CLI subcommand symlinks in ~/.local/bin/
    val binDir = ctx.home / ".local" / "bin"
    if os.exists(binDir) then
      for cmd <- Subcommands do
        val symlink = binDir / s"polyomino-$cmd"
        if os.isLink(symlink) then
          try
            os.remove(symlink)
            removedCount += 1
          catch
            case _: Exception => ()

    println(s"\n\u001b[1;32m[SUCCESS]\u001b[0m Uninstallation complete! Removed $removedCount links, restored $restoredCount backups.")
    Right(())
