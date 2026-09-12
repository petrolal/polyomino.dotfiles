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
    label = "Matriz (Gold/Teal)",
    base = "#0F1117",
    mantle = "#191C24",
    crust = "#2B303C",
    text = "#F8FAFC",
    subtext0 = "#94A3B8",
    subtext1 = "#B8C4D1",
    surface0 = "#122232",
    surface1 = "#0E1C28",
    surface2 = "#1E3850",
    overlay0 = "#2E4057",
    accent = "#EBB434",
    blue = "#EBB434",
    teal = "#00D2D3",
    lavender = "#38BDF8",
    sapphire = "#3B82F6",
    sky = "#06B6D4",
    green = "#10B981",
    yellow = "#F59E0B",
    peach = "#FFB347",
    maroon = "#C2413B",
    red = "#EF4444",
    mauve = "#A855F7",
    pink = "#C084FC",
    flamingo = "#D9E2EC",
    rosewater = "#E0E6ED"
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
      val base = kvMap.getOrElse("BASE", "#0F1117")
      val mantle = kvMap.getOrElse("MANTLE", base)
      val crust = kvMap.getOrElse("CRUST", "#2B303C")
      val text = kvMap.getOrElse("TEXT", "#F8FAFC")
      val subtext0 = kvMap.getOrElse("SUBTEXT0", "#94A3B8")
      val subtext1 = kvMap.getOrElse("SUBTEXT1", "#B8C4D1")
      val surface0 = kvMap.getOrElse("SURFACE0", "#122232")
      val surface1 = kvMap.getOrElse("SURFACE1", "#0E1C28")
      val surface2 = kvMap.getOrElse("SURFACE2", "#1E3850")
      val overlay0 = kvMap.getOrElse("OVERLAY0", "#2E4057")
      val blue = kvMap.getOrElse("BLUE", "#EBB434")
      val accent = kvMap.getOrElse("ACCENT", blue)
      val teal = kvMap.getOrElse("TEAL", "#00D2D3")
      val lavender = kvMap.getOrElse("LAVENDER", "#38BDF8")
      val sapphire = kvMap.getOrElse("SAPPHIRE", "#3B82F6")
      val sky = kvMap.getOrElse("SKY", "#06B6D4")
      val green = kvMap.getOrElse("GREEN", "#10B981")
      val yellow = kvMap.getOrElse("YELLOW", "#F59E0B")
      val peach = kvMap.getOrElse("PEACH", "#FFB347")
      val maroon = kvMap.getOrElse("MAROON", "#C2413B")
      val red = kvMap.getOrElse("RED", "#EF4444")
      val mauve = kvMap.getOrElse("MAUVE", "#A855F7")
      val pink = kvMap.getOrElse("PINK", "#C084FC")
      val flamingo = kvMap.getOrElse("FLAMINGO", "#D9E2EC")
      val rosewater = kvMap.getOrElse("ROSEWATER", "#E0E6ED")

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
