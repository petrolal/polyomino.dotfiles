package polyomino

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{PolyominoError, UnknownCommandError}

object Main:
  def main(args: Array[String]): Unit =
    val exitCode = dispatch(args)
    if exitCode != 0 then sys.exit(exitCode)

  def dispatch(args: Array[String]): Int =
    val rawProg = sys.env.getOrElse("POLYOMINO_PROG_NAME", getArgv0())
    val progName = if rawProg.nonEmpty then rawProg else "polyomino"
    val binaryBasename = try os.Path(progName, os.pwd).last catch case _: Exception => progName

    val (cmd, restArgs) = if binaryBasename.startsWith("polyomino-") then
      (binaryBasename.stripPrefix("polyomino-"), args.toList)
    else
      args.headOption match
        case Some(cmdArg) if !cmdArg.startsWith("-") => (cmdArg, args.tail.toList)
        case _ =>
          printUmbrellaHelp()
          return 0

    runCommand(cmd, restArgs) match
      case Right(_) => 0
      case Left(err) =>
        if err.message.nonEmpty then
          println(s"\u001b[1;31m[polyomino] error:\u001b[0m ${err.message}")
        err.code

  private def getArgv0(): String =
    try
      val bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("/proc/self/cmdline"))
      val nul = bytes.indexOf(0.toByte)
      if nul > 0 then new String(bytes, 0, nul, "UTF-8")
      else new String(bytes, "UTF-8")
    catch
      case _: Exception => ""

  private def runCommand(name: String, args: List[String]): Either[PolyominoError, Unit] =
    for
      ctx <- Context.discover()
      res <- dispatchModule(name, ctx, args)
    yield res

  private def dispatchModule(name: String, ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    name match
      case "version" | "-v" | "--version" => Right(println("polyomino 0.1.0 (Scala 3.5.2 Native Image)"))
      case "healthcheck" | "validate" => polyomino.dotfiles.validate.Validator.run(ctx, args)
      case "sdd" => polyomino.dotfiles.sdd.SpecDrivenDev.run(ctx, args)
      case "lock" => polyomino.dotfiles.sysutils.SysUtils.runLock(ctx, args)
      case "preview-lock" | "lock-preview" => polyomino.dotfiles.sysutils.SysUtils.runLockPreview(ctx, args)
      case "idle" => polyomino.dotfiles.sysutils.SysUtils.runIdle(ctx)
      case "screenshot" => polyomino.dotfiles.sysutils.SysUtils.runScreenshot(ctx, args)
      case "power-menu" | "powermenu" => polyomino.dotfiles.power.PowerMenu.run(ctx, args)
      case "draw-window" | "sway-draw-window" => polyomino.dotfiles.sysutils.SysUtils.runDrawWindow(ctx, args)
      case "calendar" => polyomino.dotfiles.calendar.CalendarPopup.run(ctx, args)
      case "theme" => polyomino.dotfiles.theme.ThemeEngine.run(ctx, args)
      case "wallpaper" | "wallpapers" => polyomino.dotfiles.wallpaper.WallpaperEngine.run(ctx, args)
      case "runtime-refresh" => polyomino.dotfiles.refresh.RefreshEngine.runRefresh(ctx)
      case "os-colorscheme" => polyomino.dotfiles.refresh.RefreshEngine.runOsColorscheme(ctx)
      case "notify-config" => polyomino.dotfiles.refresh.NotificationIntegration.configureApps(ctx)
      case "autotiling" => polyomino.dotfiles.autotiling.AutotilingDaemon.run(ctx, args)
      case "theme-picker" => polyomino.dotfiles.pickers.WofiPickers.runThemePicker(ctx, args)
      case "wallpaper-picker" => polyomino.dotfiles.pickers.WofiPickers.runWallpaperPicker(ctx, args)
      case "whichkey" | "wichkey" => polyomino.dotfiles.pickers.WofiPickers.runWhichkey(ctx, args)
      case "backup" => polyomino.dotfiles.maintenance.Maintenance.runBackup(ctx, args)
      case "restore" => polyomino.dotfiles.maintenance.Maintenance.runRestore(ctx, args)
      case "update" => polyomino.dotfiles.maintenance.Maintenance.runUpdate(ctx, args)
      case "install" | "deploy" => polyomino.dotfiles.install.DeployInstaller.run(ctx, args)
      case name if name.startsWith("install-") => polyomino.dotfiles.install.ToolInstallers.runTool(name, ctx, args)
      case "full-install" => polyomino.dotfiles.install.ToolInstallers.runTool("full-install", ctx, args)
      case other => Left(UnknownCommandError(other))

  val UmbrellaHelp: String =
    """polyomino — tooling for the polyomino.dotfiles Sway/Wayland desktop.
      |
      |Usage:
      |  polyomino <command> [args...]
      |  polyomino-<command> [args...]      (installed alias)
      |
      |Commands:
      |  install          full setup: symlinks config + installs/updates all dependencies
      |  theme            select a desktop flavor + background mode and apply it live
      |  wallpaper        swap the wallpaper within the active flavor (next|prev|random|list|<name>)
      |  runtime-refresh  refresh running apps (sway/waybar/kitty/wofi/neovim/os)
      |  os-colorscheme   sync the GNOME/GTK color-scheme setting
      |  lock             lock the screen (Polyomino 3D Rubik Lock / swaylock)
      |  preview-lock     preview and test lock screen safely without locking session
      |  idle             run the swayidle daemon (auto-lock, dpms, suspend)
      |  screenshot       capture a screenshot (full|region|window)
      |  power-menu       Tetris-themed power/exit modal (reboot | shutdown | lock+suspend)
      |  autotiling       Fibonacci spiral autotiling daemon for Sway
      |  healthcheck      read-only health check of the deployed setup
      |  backup           snapshot managed configs to a timestamped tarball
      |  restore          restore a snapshot created by backup
      |  update           git pull the dotfiles and re-run the installer
      |  notify-config    configure installed apps to use system notifications
      |  sdd              token-efficient spec-driven development for AI workflows
      |  install-deps     install system & build dependencies (sbt, gcc, git, etc.)
      |  install-brew     install Homebrew package manager
      |  install-gh       install GitHub CLI (gh)
      |  install-coursier install Coursier (cs) Scala application manager
      |  install-fonts    install the JetBrainsMono Nerd Font
      |  install-apps     install core desktop apps (sway/waybar/kitty/etc.)
      |  install-swaync   install SwayNC notification daemon & control center
      |  install-browser  install a web browser (chromium/firefox)
      |  install-devops   install devops & cloud tooling (docker/terraform/ansible/aws/gcp/oci/kubectl/etc.)
      |  install-zsh      install zsh + oh-my-zsh + plugins and set the shell
      |  install-sdkman   install SDKMAN! and JVM tooling
      |  install-tools    install TUI tools (spotify_player, bluetui, aerc)
      |  install-fastfetch install Fastfetch system information tool
      |  full-install     install all system packages, desktop apps, fonts, and tooling
      |  theme-picker     wofi GUI front-end for the theme command
      |  wallpaper-picker wofi GUI to pick a wallpaper for the active flavor
      |  whichkey         wofi cheatsheet of the live sway keybindings
      |
      |Run `polyomino <command> --help` for command-specific usage.
      |""".stripMargin

  private def printUmbrellaHelp(): Unit =
    print(UmbrellaHelp)
