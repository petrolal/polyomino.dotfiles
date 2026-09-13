package polyomino.dotfiles.theme

import polyomino.dotfiles.context.Context
import upickle.default._

case class Palette(
    name: String,
    label: String,
    base: String,
    mantle: String,
    crust: String = "#2B303C",
    text: String,
    subtext0: String = "#94A3B8",
    subtext1: String = "#B8C4D1",
    surface0: String = "#122232",
    surface1: String = "#0E1C28",
    surface2: String = "#1E3850",
    overlay0: String = "#2E4057",
    accent: String,
    blue: String,
    teal: String = "#00D2D3",
    lavender: String = "#38BDF8",
    sapphire: String = "#3B82F6",
    sky: String = "#06B6D4",
    green: String,
    yellow: String,
    peach: String = "#FFB347",
    maroon: String = "#C2413B",
    red: String,
    mauve: String = "#A855F7",
    pink: String = "#C084FC",
    flamingo: String = "#D9E2EC",
    rosewater: String = "#E0E6ED",
    wallpaper: Option[String] = None
) derives ReadWriter

object Palette:
  val FallbackPalette: Palette = Palette(
    name = "matriz",
    label = "Matriz (Polyomino Void)",
    base = "#0D1117",
    mantle = "#161B22",
    crust = "#010409",
    text = "#F0F6FC",
    subtext0 = "#8B949E",
    subtext1 = "#C9D1D9",
    surface0 = "#1C2128",
    surface1 = "#21262D",
    surface2 = "#2D333B",
    overlay0 = "#30363D",
    accent = "#8B5CF6",
    blue = "#8B5CF6",
    teal = "#06B6D4",
    lavender = "#A78BFA",
    sapphire = "#3B82F6",
    sky = "#22D3EE",
    green = "#10B981",
    yellow = "#F59E0B",
    peach = "#D97706",
    maroon = "#D97706",
    red = "#EF4444",
    mauve = "#8B5CF6",
    pink = "#C084FC",
    flamingo = "#E0E6ED",
    rosewater = "#F0F6FC"
  )

  def listAll(ctx: Context): Seq[String] = {
    val discovered = discoverConfFiles(ctx).keys.toSeq
    val core = Seq("matriz", "encruza", "caravela", "aruanda")
    (discovered ++ core).distinct.sorted
  }

  def find(name: String, ctx: Context): Palette = {
    val lower = name.toLowerCase
    val confMap = discoverConfFiles(ctx)
    confMap.get(lower).getOrElse(FallbackPalette)
  }

  private def discoverConfFiles(ctx: Context): Map[String, Palette] =
    var result = Map.empty[String, Palette]
    val searchDirs = Seq(
      ctx.dotfilesDir / "themes" / "palettes",
      ctx.configDir / "polyomino" / "themes"
    )

    for dir <- searchDirs if os.exists(dir) do
      for file <- os.list(dir) if file.ext == "conf" do
        parseConfFile(file).foreach { pal =>
          result += (pal.name.toLowerCase -> pal)
          result += (file.baseName.toLowerCase -> pal)
        }

    result

  private def parseConfFile(file: os.Path): Option[Palette] =
    try
      val lines = os.read.lines(file)
      var kvMap = Map.empty[String, String]
      for line <- lines do
        val trimmed = line.trim
        if !trimmed.startsWith("#") && trimmed.contains("=") then
          val parts = trimmed.split("=", 2)
          val k = parts(0).trim.toUpperCase
          // Extract value between the first and last double quotes, ignore comments and stray characters
          val raw = parts(1).trim
          val firstQuote = raw.indexOf("\"")
          val lastQuote = raw.lastIndexOf("\"")
          val extracted = if (firstQuote >= 0 && lastQuote > firstQuote) {
            raw.substring(firstQuote + 1, lastQuote)
          } else {
            raw.split("#", 2)(0).trim // fallback to raw before comment
          }
          val v = extracted.stripSuffix("\\") // remove trailing backslash if present
          kvMap += (k -> v)

      val name = kvMap.getOrElse("THEME_NAME", file.baseName)
      val label = kvMap.getOrElse("THEME_LABEL", name)
      val base = kvMap.getOrElse("BASE", "#0D1117")
      val mantle = kvMap.getOrElse("MANTLE", "#161B22")
      val crust = kvMap.getOrElse("CRUST", "#010409")
      val text = kvMap.getOrElse("TEXT", "#F0F6FC")
      val subtext0 = kvMap.getOrElse("SUBTEXT0", "#8B949E")
      val subtext1 = kvMap.getOrElse("SUBTEXT1", "#C9D1D9")
      val surface0 = kvMap.getOrElse("SURFACE0", "#1C2128")
      val surface1 = kvMap.getOrElse("SURFACE1", "#21262D")
      val surface2 = kvMap.getOrElse("SURFACE2", "#2D333B")
      val overlay0 = kvMap.getOrElse("OVERLAY0", "#30363D")
      val blue = kvMap.getOrElse("BLUE", "#8B5CF6")
      val accent = kvMap.getOrElse("ACCENT", blue)
      val teal = kvMap.getOrElse("TEAL", "#06B6D4")
      val lavender = kvMap.getOrElse("LAVENDER", "#A78BFA")
      val sapphire = kvMap.getOrElse("SAPPHIRE", "#3B82F6")
      val sky = kvMap.getOrElse("SKY", "#22D3EE")
      val green = kvMap.getOrElse("GREEN", "#10B981")
      val yellow = kvMap.getOrElse("YELLOW", "#F59E0B")
      val peach = kvMap.getOrElse("PEACH", "#D97706")
      val maroon = kvMap.getOrElse("MAROON", "#D97706")
      val red = kvMap.getOrElse("RED", "#EF4444")
      val mauve = kvMap.getOrElse("MAUVE", "#8B5CF6")
      val pink = kvMap.getOrElse("PINK", "#C084FC")
      val flamingo = kvMap.getOrElse("FLAMINGO", "#E0E6ED")
      val rosewater = kvMap.getOrElse("ROSEWATER", "#F0F6FC")

      Some(Palette(
        name = name,
        label = label,
        base = base,
        mantle = mantle,
        crust = crust,
        text = text,
        subtext0 = subtext0,
        subtext1 = subtext1,
        surface0 = surface0,
        surface1 = surface1,
        surface2 = surface2,
        overlay0 = overlay0,
        accent = accent,
        blue = blue,
        teal = teal,
        lavender = lavender,
        sapphire = sapphire,
        sky = sky,
        green = green,
        yellow = yellow,
        peach = peach,
        maroon = maroon,
        red = red,
        mauve = mauve,
        pink = pink,
        flamingo = flamingo,
        rosewater = rosewater
      ))
    catch
      case _: Exception => None
