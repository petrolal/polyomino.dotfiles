package polyomino.dotfiles.refresh

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}

object RefreshEngine:
  def runRefresh(ctx: Context): Either[PolyominoError, Unit] =
    if ctx.isTest then
      println("  \u001b[32m[OK]\u001b[0m Test environment detected; skipping live desktop reload signals")
      Right(())
    else
      println("\u001b[1;36m[polyomino refresh]\u001b[0m Triggering runtime app reloads...")

      // Sway reload
      if ctx.swaySocket.isDefined then
        try os.proc("timeout", "2", "swaymsg", "reload").call(check = false, stdout = os.Pipe, stderr = os.Pipe) catch case _: Exception => ()
        println("  \u001b[32m[OK]\u001b[0m Sent swaymsg reload")

      // Kitty reload
      try os.proc("timeout", "2", "killall", "-SIGUSR1", "kitty").call(check = false, stdout = os.Pipe, stderr = os.Pipe) catch case _: Exception => ()
      println("  \u001b[32m[OK]\u001b[0m Sent SIGUSR1 to Kitty instances")

      // Waybar reload (SIGUSR2 reloads config & CSS stylesheet without hiding bar)
      try os.proc("timeout", "2", "killall", "-SIGUSR2", "waybar").call(check = false, stdout = os.Pipe, stderr = os.Pipe) catch case _: Exception => ()
      println("  \u001b[32m[OK]\u001b[0m Sent SIGUSR2 to Waybar instances")

      // SwayNC reload CSS & config
      if isCommandAvailable("swaync-client") then
        try
          os.proc("timeout", "2", "swaync-client", "-rs").call(check = false, stdout = os.Pipe, stderr = os.Pipe)
          os.proc("timeout", "2", "swaync-client", "-R").call(check = false, stdout = os.Pipe, stderr = os.Pipe)
          println("  \u001b[32m[OK]\u001b[0m Reloaded SwayNC styling & config")
        catch
          case _: Exception => ()

      // Mako non-destructive reload
      try
        if isCommandAvailable("makoctl") then
          os.proc("timeout", "2", "makoctl", "reload").call(check = false, stdout = os.Pipe, stderr = os.Pipe)
          println("  \u001b[32m[OK]\u001b[0m Sent makoctl reload")
        else
          os.proc("timeout", "2", "systemctl", "--user", "restart", "mako").call(check = false, stdout = os.Pipe, stderr = os.Pipe)
          println("  \u001b[32m[OK]\u001b[0m Restarted Mako with new theme colors")
      catch
        case _: Exception => ()

      runOsColorscheme(ctx)
      Right(())

  private def isCommandAvailable(cmd: String): Boolean =
    try os.proc("which", cmd).call(check = false, stdout = os.Pipe, stderr = os.Pipe).exitCode == 0 catch case _: Exception => false

  def runOsColorscheme(ctx: Context): Either[PolyominoError, Unit] =
    if ctx.isTest then
      Right(())
    else
      println("\u001b[1;36m[polyomino os-colorscheme]\u001b[0m Syncing GNOME GTK color-scheme...")
      try
        os.proc("gsettings", "set", "org.gnome.desktop.interface", "color-scheme", "prefer-dark").call(check = false)
        println("  \u001b[32m[OK]\u001b[0m Set org.gnome.desktop.interface color-scheme = 'prefer-dark'")
        Right(())
      catch
        case e: Exception => Right(println(s"  \u001b[33m[NOTE]\u001b[0m gsettings update: ${e.getMessage}"))
