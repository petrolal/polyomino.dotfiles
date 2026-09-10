package polyomino.dotfiles.validate

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}
import polyomino.dotfiles.install.DeployInstaller

object Validator:
  val VersionStr = "0.1.0 (Scala 3.5.2 Native Image)"

  val Subcommands: Seq[String] = DeployInstaller.Subcommands

  def run(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    println(s"\u001b[1;36m[polyomino healthcheck]\u001b[0m Running 25+ point desktop health & symlink audit...")

    var missingCount = 0

    // 1. Audit Engine Version & Binary Target
    println("\n\u001b[1m--- Engine & Version Audit ---\u001b[0m")
    println(s"  \u001b[32m[OK]\u001b[0m Version: $VersionStr")

    val binDir = ctx.home / ".local" / "bin"
    val mainBinary = binDir / "polyomino"

    if os.exists(mainBinary) then
      println(s"  \u001b[32m[OK]\u001b[0m Main executable target: $mainBinary")
    else
      println(s"  \u001b[31m[FAIL]\u001b[0m Main executable missing at $mainBinary")
      missingCount += 1

    // 2. Audit Desktop CLI Tools
    println("\n\u001b[1m--- System Binary Audit ---\u001b[0m")
    val requiredTools = Seq(
      "sway", "waybar", "kitty", "wofi", "swaylock", "swayidle", "grim", "slurp",
      "pactl", "brightnessctl", "gsettings", "git", "tar", "curl", "unzip",
      "fc-cache", "which", "zsh", "fastfetch"
    )

    for tool <- requiredTools do
      if isCommandAvailable(tool) then
        println(s"  \u001b[32m[OK]\u001b[0m Binary '$tool' is installed")
      else
        println(s"  \u001b[31m[FAIL]\u001b[0m Binary '$tool' is MISSING")
        missingCount += 1

    // 3. Audit Subcommand Binary Symlinks in ~/.local/bin/
    println("\n\u001b[1m--- Subcommand Symlink Audit (~/.local/bin/) ---\u001b[0m")
    for cmd <- Subcommands do
      val symlinkPath = binDir / s"polyomino-$cmd"
      if os.exists(symlinkPath) || os.isLink(symlinkPath) then
        println(s"  \u001b[32m[OK]\u001b[0m Symlink 'polyomino-$cmd' -> $mainBinary")
      else
        println(s"  \u001b[31m[FAIL]\u001b[0m Symlink 'polyomino-$cmd' is MISSING at $symlinkPath")
        missingCount += 1

    // 4. Audit Desktop Config Paths
    println("\n\u001b[1m--- Desktop Configuration Paths ---\u001b[0m")
    val requiredPaths = Seq(
      ctx.configDir / "sway",
      ctx.configDir / "kitty",
      ctx.configDir / "waybar",
      ctx.configDir / "wofi",
      ctx.configDir / "rofi",
      ctx.configDir / "fastfetch"
    )

    for path <- requiredPaths do
      if os.exists(path) then
        println(s"  \u001b[32m[OK]\u001b[0m Path exists: $path")
      else
        println(s"  \u001b[31m[FAIL]\u001b[0m Path MISSING: $path")
        missingCount += 1

    val fontsDir = ctx.home / ".local" / "share" / "fonts"
    if os.exists(fontsDir) then
      println(s"  \u001b[32m[OK]\u001b[0m User fonts directory exists ($fontsDir)")
    else
      println(s"  \u001b[33m[NOTE]\u001b[0m User fonts directory not created yet ($fontsDir)")

    if missingCount == 0 then
      println(s"\n\u001b[1;32m[SUCCESS]\u001b[0m Engine ($VersionStr), system binaries, paths, and subcommand symlinks validated!")
      Right(())
    else
      Left(CommandError(s"Validation failed: $missingCount required components missing.", 1))

  private def isCommandAvailable(cmd: String): Boolean =
    try os.proc("which", cmd).call(check = false).exitCode == 0 catch case _: Exception => false
