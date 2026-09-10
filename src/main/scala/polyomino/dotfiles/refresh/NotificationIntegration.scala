package polyomino.dotfiles.refresh

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}

object NotificationIntegration:
  def configureApps(ctx: Context): Either[PolyominoError, Unit] =
    println("[1;36m[polyomino notify-config][0m Configuring apps for system notifications...")

    val results = scala.collection.mutable.ListBuffer[String]()

    // Enable notification service if available
    try
      os.proc("systemctl", "--user", "daemon-reload").call(check = false, stdout = os.Pipe, stderr = os.Pipe)
      if isCommandAvailable("swaync") then
        os.proc("systemctl", "--user", "disable", "mako").call(check = false, stdout = os.Pipe, stderr = os.Pipe)
        results += "  \u001b[32m[OK]\u001b[0m SwayNC notification daemon configured"
      else
        os.proc("systemctl", "--user", "enable", "mako").call(check = false, stdout = os.Pipe, stderr = os.Pipe)
        os.proc("systemctl", "--user", "start", "mako").call(check = false, stdout = os.Pipe, stderr = os.Pipe)
        results += "  \u001b[32m[OK]\u001b[0m Mako systemd service enabled"
    catch
      case _: Exception => results += "  \u001b[33m[NOTE]\u001b[0m Notification daemon systemd setup skipped"

    // Configure Chromium/Chrome
    configureChromium(ctx) match
      case Right(_) => results += "  \u001b[32m[OK]\u001b[0m Chromium/Chrome configured for native notifications"
      case Left(_) => results += "  \u001b[33m[NOTE]\u001b[0m Chromium not found or already configured"

    // Configure Firefox (already uses system notifications by default on Wayland)
    results += "  \u001b[32m[OK]\u001b[0m Firefox uses system notifications by default"

    // Configure Slack (if installed)
    configureSlack(ctx) match
      case Right(_) => results += "  \u001b[32m[OK]\u001b[0m Slack configured for system notifications"
      case Left(_) => results += "  \u001b[33m[NOTE]\u001b[0m Slack not installed"

    for line <- results do println(line)

    println("  \u001b[1;32m[SUCCESS]\u001b[0m Application notification integration complete!")
    Right(())

  private def configureChromium(ctx: Context): Either[PolyominoError, Unit] =
    try
      val binDir = ctx.home / ".local" / "bin"
      os.makeDir.all(binDir)

      val chromiumWrapper = binDir / "chromium"
      val wrapperScript = """#!/bin/bash
# Chromium launcher with native notification support for Wayland
exec /usr/bin/chromium --enable-features=UseOsNotificationCenter "$@"
"""

      os.write.over(chromiumWrapper, wrapperScript)
      os.perms.set(chromiumWrapper, "rwxr-xr-x")

      // Also create google-chrome wrapper if it exists
      if isCommandAvailable("google-chrome") || isCommandAvailable("google-chrome-stable") then
        val chromeWrapper = binDir / "google-chrome"
        val chromeScript = """#!/bin/bash
# Google Chrome launcher with native notification support for Wayland
exec /usr/bin/google-chrome --enable-features=UseOsNotificationCenter "$@"
"""
        os.write.over(chromeWrapper, chromeScript)
        os.perms.set(chromeWrapper, "rwxr-xr-x")

      Right(())
    catch
      case _: Exception => Left(CommandError("Chromium wrapper creation failed", 1))

  private def isCommandAvailable(cmd: String): Boolean =
    try os.proc("which", cmd).call(check = false).exitCode == 0 catch case _: Exception => false

  private def configureSlack(ctx: Context): Either[PolyominoError, Unit] =
    val slackConfigDir = ctx.configDir / "Slack"
    if os.exists(slackConfigDir) then
      try
        val settingsFile = slackConfigDir / "settings.json"
        if os.exists(settingsFile) then
          val content = os.read(settingsFile)
          if !content.contains("\"useNativeNotifications\": true") then
            val updatedContent = if content.contains("\"useNativeNotifications\": false") then
              content.replace(
                "\"useNativeNotifications\": false",
                "\"useNativeNotifications\": true"
              )
            else if content.trim.startsWith("{") && content.trim.endsWith("}") then
              val trimmed = content.trim
              trimmed.substring(0, trimmed.lastIndexOf('}')) + ",\n  \"useNativeNotifications\": true\n}\n"
            else content
            os.write.over(settingsFile, updatedContent)
        Right(())
      catch
        case _: Exception => Left(CommandError("Slack config update failed", 1))
    else
      Left(CommandError("Slack not installed", 1))
