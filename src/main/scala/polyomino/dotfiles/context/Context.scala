package polyomino.dotfiles.context

import polyomino.dotfiles.error.{ConfigError, PolyominoError}
import os.Path

case class Context(
    home: Path,
    configDir: Path,
    shareDir: Path,
    dotfilesDir: Path,
    swaySocket: Option[String],
    isTest: Boolean = false
)

object Context:
  def discover(): Either[PolyominoError, Context] =
    try
      val home = os.home
      val configDir = sys.env.get("XDG_CONFIG_HOME").filter(_.trim.nonEmpty).map(os.Path(_)).getOrElse(home / ".config")
      val shareDir = sys.env.get("XDG_DATA_HOME").filter(_.trim.nonEmpty).map(p => os.Path(p) / "polyomino").getOrElse(home / ".local" / "share" / "polyomino")
      val dotfilesDir = sys.env.get("POLYOMINO_DOTFILES_DIR").filter(_.trim.nonEmpty).map(os.Path(_)).getOrElse(
        if os.exists(home / "polyomino.dotfiles") then home / "polyomino.dotfiles" else os.pwd
      )
      val swaySocket = sys.env.get("SWAYSOCK")

      Right(Context(
        home = home,
        configDir = configDir,
        shareDir = shareDir,
        dotfilesDir = dotfilesDir,
        swaySocket = swaySocket,
        isTest = false
      ))
    catch
      case e: Exception => Left(ConfigError(e.getMessage))

  def isolated(tempDir: Path, dotfilesDir: Path = os.pwd): Context =
    val home = tempDir / "home"
    val configDir = home / ".config"
    val shareDir = home / ".local" / "share" / "polyomino"
    os.makeDir.all(home)
    os.makeDir.all(configDir)
    os.makeDir.all(shareDir)
    Context(
      home = home,
      configDir = configDir,
      shareDir = shareDir,
      dotfilesDir = dotfilesDir,
      swaySocket = None,
      isTest = true
    )
