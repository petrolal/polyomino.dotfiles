package polyomino.dotfiles.theme

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}

object ThemeEngine:
  def run(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    if args.isEmpty then
      polyomino.dotfiles.pickers.WofiPickers.runThemePicker(ctx, args)
    else
      val flavor = args.head
      var mode = "wallpaper"
      var customWallpaper: Option[String] = None
      var interval = "30m"

      var i = 1
      while i < args.length do
        args(i) match
          case "--flat" =>
            mode = "flat"
            i += 1
          case "--wallpaper" if i + 1 < args.length =>
            mode = "wallpaper"
            customWallpaper = Some(args(i + 1))
            i += 2
          case "--rotate" =>
            mode = "rotate"
            if i + 1 < args.length && !args(i + 1).startsWith("-") then
              interval = args(i + 1)
              i += 2
            else
              i += 1
          case _ =>
            i += 1

      applyTheme(ctx, flavor, mode, customWallpaper, interval)

  def getActivePalette(ctx: Context): Palette =
    val stateFile = ctx.configDir / "polyomino" / "theme" / "state"
    if os.exists(stateFile) then
      val flavorOpt = os.read.lines(stateFile).find(_.startsWith("FLAVOR=")).map(_.stripPrefix("FLAVOR="))
      flavorOpt.map(f => Palette.find(f, ctx)).getOrElse(Palette.FallbackPalette)
    else
      Palette.find("matriz", ctx)

  def applyTheme(
      ctx: Context,
      flavor: String,
      mode: String = "wallpaper",
      customWallpaper: Option[String] = None,
      interval: String = "30m"
  ): Either[PolyominoError, Unit] =
    println(s"\u001b[1;35m[polyomino theme]\u001b[0m Applying theme '$flavor' (mode: $mode)...")
    val palette = Palette.find(flavor, ctx)

    val stateDir = ctx.configDir / "polyomino" / "theme"
    os.makeDir.all(stateDir)

    // Dynamic Wallpaper Canvas Generator (aligns Base Background + Overlay0/Crust Dot Matrix)
    val dynamicCanvasFile = stateDir / s"canvas_${palette.name}.svg"
    generateDynamicCanvasSvg(palette, dynamicCanvasFile)

    // Resolve wallpaper path
    val activeWallpaper = mode match
      case "flat" => dynamicCanvasFile.toString
      case "rotate" => resolveWallpaperForFlavor(ctx, palette.name).getOrElse(dynamicCanvasFile.toString)
      case _ => customWallpaper.getOrElse(resolveWallpaperForFlavor(ctx, palette.name).getOrElse(dynamicCanvasFile.toString))

    // 1. Write global state file ~/.config/polyomino/theme/state (read by Neovim and desktop apps)
    val stateFile = stateDir / "state"
    val nvimColorscheme = s"${palette.name}-theme"
    val stateContent =
      s"""FLAVOR=${palette.name}
         |MODE=$mode
         |WALLPAPER=$activeWallpaper
         |WALLPAPER_SOURCE=${if mode == "rotate" then "rotate" else if mode == "flat" then "flat" else "single"}
         |INTERVAL=$interval
         |NVIM_COLORSCHEME=$nvimColorscheme
         |""".stripMargin
    os.write.over(stateFile, stateContent)
    println(s"  \u001b[32m[OK]\u001b[0m Global theme state written -> $stateFile")
    // 2a. Write token CSS file for external scripts (~/.config/polyomino/theme/tokens.css)
    val tokensFile = stateDir / "tokens.css"
    val colorsCssFile = stateDir / "colors.css"
    val tokensContent =
      s"""@define-color base ${palette.base};
@define-color mantle ${palette.mantle};
@define-color crust ${palette.crust};
@define-color surface0 ${palette.surface0};
@define-color surface1 ${palette.surface1};
@define-color surface2 ${palette.surface2};
@define-color overlay0 ${palette.overlay0};
@define-color text ${palette.text};
@define-color subtext0 ${palette.subtext0};
@define-color subtext1 ${palette.subtext1};
@define-color accent ${palette.accent};
@define-color blue ${palette.blue};
@define-color teal ${palette.teal};
@define-color green ${palette.green};
@define-color yellow ${palette.yellow};
@define-color peach ${palette.peach};
@define-color maroon ${palette.maroon};
@define-color red ${palette.red};
@define-color mauve ${palette.mauve};
@define-color pink ${palette.pink};
@define-color lavender ${palette.lavender};
@define-color sapphire ${palette.sapphire};
@define-color sky ${palette.sky};
@define-color flamingo ${palette.flamingo};
@define-color rosewater ${palette.rosewater};

/* Polyomino Welcome Center Semantic Aliases */
@define-color bg_void ${palette.base};
@define-color bg_mantle ${palette.mantle};
@define-color card_surface ${palette.surface0};
@define-color card_surface_hover ${palette.surface1};
@define-color card_surface_active ${palette.surface2};
@define-color border_crisp ${palette.overlay0};
@define-color accent_violet #8b5cf6;
@define-color accent_violet_glow #a78bfa;
@define-color accent_amber #d97706;
@define-color accent_gold #f59e0b;
@define-color accent_blue #3b82f6;
@define-color accent_blue_glow #60a5fa;
@define-color accent_teal #06b6d4;"""
    os.write.over(tokensFile, tokensContent)
    os.write.over(colorsCssFile, tokensContent)
    println(s"  \u001b[32m[OK]\u001b[0m Token CSS written -> $tokensFile & $colorsCssFile")

    // 2. Render Sway colors.conf (~/.config/sway/colors.conf)
    val swayDir = ctx.configDir / "sway"
    os.makeDir.all(swayDir)
    val swayColorsFile = swayDir / "colors.conf"
    val wallpaperDirective = if activeWallpaper.nonEmpty then
      s"output * bg \"$activeWallpaper\" fill"
    else
      "# No wallpaper"

    val swayColorsContent =
      s"""# Generated by polyomino dotfiles theme engine — flavor=${palette.name} mode=$mode
         |$wallpaperDirective
         |
         |# class                 border              bg                text                indicator           child_border
         |client.focused          ${palette.blue}     ${palette.base}   ${palette.text}     ${palette.blue}     ${palette.blue}
         |client.focused_inactive ${palette.overlay0} ${palette.base}   ${palette.subtext0} ${palette.overlay0}  ${palette.overlay0}
         |client.unfocused        ${palette.overlay0} ${palette.base}   ${palette.subtext0} ${palette.overlay0}  ${palette.overlay0}
         |client.urgent           ${palette.red}      ${palette.base}   ${palette.text}     ${palette.red}       ${palette.red}
         |""".stripMargin
    os.write.over(swayColorsFile, swayColorsContent)
    val isSystemSwayfx = try {
      val v = if os.exists(os.Path("/usr/bin/sway")) then
        os.proc("/usr/bin/sway", "--version").call(check = false).out.text().toLowerCase
      else
        os.proc("sway", "--version").call(check = false).out.text().toLowerCase
      v.contains("swayfx")
    } catch {
      case _: Exception => false
    }

    val swayFxFile = swayDir / "fx.conf"
    val swayFxContent = if isSystemSwayfx then
      """# Generated by polyomino dotfiles theme engine — SwayFX effects active
        |corner_radius 8
        |smart_corner_radius off
        |blur enable
        |blur_xray disable
        |blur_passes 3
        |blur_radius 5
        |blur_noise 0.02
        |
        |# Enforce blur across terminal classes
        |for_window [app_id="Alacritty"] blur enable
        |for_window [app_id="kitty"] blur enable
        |for_window [app_id="foot"] blur enable
        |for_window [app_id="floating-term"] blur enable
        |
        |# Depth shadows for glass containers
        |shadows enable
        |shadow_blur_radius 14
        |shadow_color #000000B3
        |shadow_inactive_color #00000066
        |
        |default_dim_inactive 0.15
        |""".stripMargin
    else
      """# Generated by polyomino dotfiles theme engine — Standard Sway active
        |# SwayFX effects disabled to prevent config validation redbar errors on system Sway
        |""".stripMargin
    os.write.over(swayFxFile, swayFxContent)
    val dotfilesFxFile = ctx.dotfilesDir / "config" / "sway" / "fx.conf"
    if !ctx.isTest && os.exists(ctx.dotfilesDir / "config" / "sway") && dotfilesFxFile != swayFxFile then
      try os.write.over(dotfilesFxFile, swayFxContent) catch case _: Exception => ()

    println(s"  \u001b[32m[OK]\u001b[0m Rendered Sway colors -> $swayColorsFile")

    // 3. Render Kitty theme.conf & colors.conf (~/.config/kitty/)
    val kittyDir = ctx.configDir / "kitty"
    os.makeDir.all(kittyDir)
    val kittyThemeFile = kittyDir / "theme.conf"
    val kittyColorsFile = kittyDir / "colors.conf"
    val kittyContent =
      s"""# Generated by polyomino.dotfiles theme engine
         |background ${palette.base}
         |foreground ${palette.text}
         |selection_background ${palette.blue}
         |selection_foreground ${palette.base}
         |cursor ${palette.blue}
         |cursor_text_color ${palette.base}
         |
         |# Active / Inactive Tab Styling (CAD separator tabs)
         |active_tab_foreground   ${palette.base}
         |active_tab_background   ${palette.blue}
         |inactive_tab_foreground ${palette.subtext0}
         |inactive_tab_background ${palette.mantle}
         |
         |# Window borders
         |active_border_color   ${palette.blue}
         |inactive_border_color ${palette.overlay0}
         |
         |# Standard 16 ANSI colors (Polyomino Palette)
         |color0 ${palette.mantle}
         |color8 ${palette.overlay0}
         |color1 ${palette.red}
         |color9 ${palette.red}
         |color2 ${palette.green}
         |color10 ${palette.green}
         |color3 #d97706
         |color11 #f59e0b
         |color4 #8b5cf6
         |color12 #3b82f6
         |color5 #06b6d4
         |color13 #a78bfa
         |color6 #00d2d3
         |color14 #22d3ee
         |color7 ${palette.text}
         |color15 #FFFFFF
         |""".stripMargin
    os.write.over(kittyThemeFile, kittyContent)
    os.write.over(kittyColorsFile, kittyContent)
    println(s"  \u001b[32m[OK]\u001b[0m Rendered Kitty theme -> $kittyThemeFile")

    // 4. Render Waybar theme CSS & style.css (~/.config/waybar/)
    val waybarDir = ctx.configDir / "waybar"
    os.makeDir.all(waybarDir)
    val waybarThemeFile = waybarDir / "theme.css"
    val waybarStyleFile = waybarDir / "style.css"
    val waybarContent =
      s"""/* Generated by polyomino.dotfiles theme engine */
         |@define-color base ${palette.base};
         |@define-color mantle ${palette.mantle};
         |@define-color crust ${palette.crust};
         |@define-color surface0 ${palette.surface0};
         |@define-color surface1 ${palette.surface1};
         |@define-color surface2 ${palette.surface2};
         |@define-color overlay0 ${palette.overlay0};
         |@define-color text ${palette.text};
         |@define-color subtext0 ${palette.subtext0};
         |@define-color subtext1 ${palette.subtext1};
         |@define-color accent ${palette.accent};
         |@define-color blue ${palette.blue};
         |@define-color teal ${palette.teal};
         |@define-color green ${palette.green};
         |@define-color yellow ${palette.yellow};
         |@define-color peach ${palette.peach};
         |@define-color maroon ${palette.maroon};
         |@define-color red ${palette.red};
         |@define-color mauve ${palette.mauve};
         |@define-color pink ${palette.pink};
         |@define-color lavender ${palette.lavender};
         |@define-color sapphire ${palette.sapphire};
         |@define-color sky ${palette.sky};
         |@define-color flamingo ${palette.flamingo};
         |@define-color rosewater ${palette.rosewater};
         |""".stripMargin
    os.write.over(waybarThemeFile, waybarContent)
    println(s"  \u001b[32m[OK]\u001b[0m Rendered Waybar theme -> $waybarThemeFile")

    val waybarStyleContent =
      s"""@import "${ctx.configDir / "polyomino" / "theme" / "tokens.css"}";
        |
        |/* Archcraft-inspired clean vector layout, polyomino palette */
        |* {
        |    font-family: "JetBrainsMono Nerd Font", "JetBrains Mono", monospace;
        |    font-size: 13px;
        |    min-height: 0;
        |    border: none;
        |    border-radius: 0px;
        |    transition: none;
        |}
        |
        |window#waybar {
        |    background-color: alpha(@base, 0.92);
        |    color: @subtext1;
        |    border-bottom: 1px solid @overlay0;
        |    margin: 0;
        |}
        |
        |window#waybar.hidden {
        |    opacity: 0.5;
        |}
        |
        |.modules-left,
        |.modules-center,
        |.modules-right {
        |    background: transparent;
        |    padding: 0;
        |}
        |
        |#left,
        |#center,
        |#right {
        |    background-color: transparent;
        |    padding: 0;
        |    margin: 0;
        |}
        |
        |tooltip {
        |    background-color: @base;
        |    color: @text;
        |    border: 1px solid @overlay0;
        |    padding: 8px 12px;
        |}
        |
        |tooltip label {
        |    color: @text;
        |    padding: 4px 8px;
        |    font-size: 13px;
        |}
        |
        |/* ── left anchor: polyomino launcher, accent underline ─────────────── */
        |#custom-polyomino {
        |    background: transparent;
        |    color: @accent;
        |    font-weight: bold;
        |    padding: 2px 10px;
        |    margin: 3px 6px 3px 4px;
        |    border-bottom: 2px solid @accent;
        |}
        |
        |/* ── workspaces: minimal underline vector ──────────────────────────── */
        |#workspaces {
        |    background: transparent;
        |    margin: 0 4px;
        |}
        |
        |#workspaces button {
        |    padding: 2px 8px;
        |    margin: 0 2px;
        |    background: transparent;
        |    color: @subtext0;
        |    border-bottom: 2px solid transparent;
        |    font-weight: bold;
        |}
        |
        |#workspaces button label {
        |    padding: 0;
        |}
        |
        |#workspaces button:hover {
        |    color: @text;
        |    border-bottom: 2px solid @overlay0;
        |}
        |
        |#workspaces button.focused,
        |#workspaces button.active {
        |    color: @yellow;
        |    border-bottom: 2px solid @accent;
        |}
        |
        |#workspaces button.urgent {
        |    color: @red;
        |    border-bottom: 2px solid @red;
        |}
        |
        |/* ── media, sits plain on the left cluster ─────────────────────────── */
        |#custom-media {
        |    color: @subtext0;
        |    padding: 0 6px;
        |}
        |
        |#custom-media.playing {
        |    color: @green;
        |}
        |
        |#custom-media.paused {
        |    color: @yellow;
        |}
        |
        |/* ── center: dim, ellipsized focused-window title ──────────────────── */
        |#window {
        |    color: @subtext0;
        |    padding: 0 8px;
        |}
        |
        |#window.empty {
        |    padding: 0;
        |    margin: 0;
        |}
        |
        |/* ── right cluster: flat modules, colored underline per icon ───────── */
        |.modules-right {
        |    margin: 0;
        |    padding: 0;
        |}
        |
        |.modules-right > widget {
        |    margin: 0;
        |}
        |
        |.modules-right > widget > * {
        |    padding: 2px 8px;
        |    margin: 3px 2px;
        |    background: transparent;
        |    border-bottom: 2px solid transparent;
        |}
        |
        |#tray {
        |    border-bottom: 2px solid @subtext0;
        |}
        |
        |#pulseaudio {
        |    color: @green;
        |    border-bottom: 2px solid @green;
        |}
        |
        |#bluetooth {
        |    color: @sapphire;
        |    border-bottom: 2px solid @sapphire;
        |}
        |
        |#network {
        |    color: @teal;
        |    border-bottom: 2px solid @teal;
        |}
        |
        |#network.disconnected {
        |    color: @red;
        |    border-bottom: 2px solid @red;
        |}
        |
        |#cpu {
        |    color: @peach;
        |    border-bottom: 2px solid @peach;
        |}
        |
        |#memory {
        |    color: @mauve;
        |    border-bottom: 2px solid @mauve;
        |}
        |
        |#battery {
        |    color: @sapphire;
        |    border-bottom: 2px solid @sapphire;
        |}
        |
        |#battery.warning {
        |    color: @yellow;
        |    border-bottom: 2px solid @yellow;
        |}
        |
        |#battery.critical {
        |    color: @red;
        |    border-bottom: 2px solid @red;
        |}
        |
        |#custom-notification {
        |    color: @subtext0;
        |    border-bottom: 2px solid @subtext0;
        |}
        |
        |#custom-notification.notification,
        |#custom-notification.dnd-notification,
        |#custom-notification.inhibited-notification,
        |#custom-notification.dnd-inhibited-notification {
        |    color: @red;
        |    border-bottom: 2px solid @red;
        |}
        |
        |#clock {
        |    color: @text;
        |    font-weight: bold;
        |    border-bottom: 2px solid @accent;
        |}
        |""".stripMargin
    os.write.over(waybarStyleFile, waybarStyleContent)
    println(s"  \u001b[32m[OK]\u001b[0m Rendered Waybar style -> $waybarStyleFile")

    // 5. Render Wofi & Rofi launcher themes (~/.config/wofi/, ~/.config/rofi/)
    val wofiDir = ctx.configDir / "wofi"
    os.makeDir.all(wofiDir)
    val wofiThemeFile = wofiDir / "theme.css"
    val wofiStyleFile = wofiDir / "style.css"
    val wofiContent =
      s"""/* Generated by polyomino.dotfiles theme engine — flavor=${palette.name} */
         |* {
         |    font-family: "JetBrainsMono Nerd Font", monospace;
         |}
         |window {
         |    background-color: ${palette.mantle};
         |    color: ${palette.text};
         |    border: 2px solid ${palette.accent};
         |    border-radius: 0px;
         |    font-family: "JetBrainsMono Nerd Font", monospace;
         |    font-size: 13px;
         |}
         |#input {
         |    background-color: ${palette.base};
         |    color: ${palette.text};
         |    border: 2px solid ${palette.accent};
         |    border-radius: 0px;
         |    padding: 10px 14px;
         |    margin: 10px;
         |    outline: none;
         |    box-shadow: none;
         |    caret-color: ${palette.accent};
         |    font-family: "JetBrainsMono Nerd Font", monospace;
         |    font-size: 13px;
         |    min-height: 28px;
         |}
         |#input:focus {
         |    background-color: ${palette.base};
         |    color: ${palette.text};
         |    border: 2px solid ${palette.accent};
         |    outline: none;
         |    box-shadow: none;
         |}
         |#input placeholder {
         |    color: ${palette.accent};
         |    font-family: "JetBrainsMono Nerd Font", monospace;
         |    font-size: 13px;
         |    opacity: 0.85;
         |}
         |#input image {
         |    color: ${palette.accent};
         |    border: none;
         |    background: transparent;
         |    margin-right: 10px;
         |}
         |#inner-box {
         |    background-color: transparent;
         |    margin: 4px 6px;
         |}
         |#outer-box {
         |    padding: 2px;
         |}
         |#scroll {
         |    margin: 4px;
         |}
         |#entry {
         |    padding: 10px 14px;
         |    margin: 3px 6px;
         |    border-radius: 0px;
         |    background-color: ${palette.base};
         |    color: ${palette.text};
         |    border-left: 3px solid transparent;
         |    min-height: 36px;
         |}
         |#entry:selected {
         |    background-color: ${palette.accent};
         |    color: ${palette.base};
         |    border-radius: 0px;
         |    outline: none;
         |    border-left: 3px solid ${palette.text};
         |}
         |#entry:selected #text,
         |#text:selected {
         |    color: ${palette.base};
         |    font-weight: bold;
         |}
         |#img,
         |#entry image {
         |    margin-right: 14px;
         |    margin-left: 2px;
         |    vertical-align: middle;
         |}
         |#text {
         |    color: ${palette.text};
         |    font-family: "JetBrainsMono Nerd Font", monospace;
         |    font-size: 13px;
         |    margin: auto 0;
         |    padding: 2px 0;
         |}
         |#prompt {
         |    color: ${palette.accent};
         |    font-family: "JetBrainsMono Nerd Font", monospace;
         |    font-size: 13px;
         |    font-weight: bold;
         |    margin-right: 8px;
         |    padding: 0 4px;
         |}
         |""".stripMargin
    os.write.over(wofiThemeFile, wofiContent)
    os.write.over(wofiStyleFile, wofiContent)
    println(s"  \u001b[32m[OK]\u001b[0m Rendered Wofi theme -> $wofiStyleFile")

    val rofiDir = ctx.configDir / "rofi"
    os.makeDir.all(rofiDir)
    val rofiThemeFile = rofiDir / "theme.rasi"
    val rofiConfigFile = rofiDir / "config.rasi"
    val rofiThemeContent =
      s"""/* Generated by polyomino.dotfiles theme engine — flavor=${palette.name} */
         |* {
         |    bg: ${palette.base};
         |    fg: ${palette.text};
         |    accent: ${palette.accent};
         |    mantle: ${palette.mantle};
         |    surface: ${palette.surface0};
         |    border-col: ${palette.accent};
         |    font: "JetBrainsMono Nerd Font 12";
         |    background-color: transparent;
         |    text-color: @fg;
         |}
         |window {
         |    background-color: @bg;
         |    text-color: @fg;
         |    border: 1px;
         |    border-color: @border-col;
         |    border-radius: 8px;
         |    padding: 14px;
         |    width: 680px;
         |}
         |mainbox {
         |    children: [ inputbar, listview ];
         |}
         |inputbar {
         |    children: [ prompt, entry ];
         |    background-color: @mantle;
         |    border: 1px solid ${palette.overlay0};
         |    border-radius: 6px;
         |    padding: 6px 10px;
         |    margin: 0 0 10px 0;
         |}
         |prompt {
         |    background-color: @accent;
         |    text-color: ${palette.base};
         |    padding: 4px 10px;
         |    border-radius: 4px;
         |    font-weight: bold;
         |}
         |entry {
         |    placeholder: "Search applications...";
         |    placeholder-color: ${palette.subtext0};
         |    padding: 4px 8px;
         |    text-color: @fg;
         |}
         |listview {
         |    lines: 6;
         |    columns: 2;
         |    fixed-height: false;
         |}
         |element {
         |    padding: 8px 12px;
         |    border-radius: 6px;
         |    background-color: @surface;
         |    margin: 3px 4px;
         |    border: 1px solid ${palette.overlay0};
         |}
         |element selected {
         |    background-color: @accent;
         |    text-color: ${palette.base};
         |    border-radius: 6px;
         |    border: 1px solid @accent;
         |}
         |element-text {
         |    text-color: inherit;
         |}
         |element-icon {
         |    size: 24px;
         |    margin: 0 8px 0 0;
         |}
         |""".stripMargin
    val rofiConfigContent =
      s"""/* Generated by polyomino.dotfiles theme engine — flavor=${palette.name} */
        |configuration {
        |    modi: "drun,run,window";
        |    show-icons: true;
        |    icon-theme: "Papirus";
        |    terminal: "kitty";
        |    drun-display-format: "⊞ {name}";
        |}
        |@theme "${rofiThemeFile.toString}"
        |""".stripMargin
    os.write.over(rofiThemeFile, rofiThemeContent)
    os.write.over(rofiConfigFile, rofiConfigContent)
    println(s"  \u001b[32m[OK]\u001b[0m Rendered Rofi theme -> $rofiThemeFile")

    // 5b. Render Fuzzel application launcher config (~/.config/fuzzel/fuzzel.ini)
    val fuzzelDir = ctx.configDir / "fuzzel"
    os.makeDir.all(fuzzelDir)
    val fuzzelConfigFile = fuzzelDir / "fuzzel.ini"
    val baseClean = palette.base.stripPrefix("#").toLowerCase
    val textClean = palette.text.stripPrefix("#").toLowerCase
    val accentClean = palette.accent.stripPrefix("#").toLowerCase
    val fuzzelContent =
      s"""# Generated by polyomino.dotfiles theme engine — flavor=${palette.name}
         |font=JetBrainsMono Nerd Font:size=12
         |prompt="⊞  "
         |terminal=kitty -e
         |icon-theme=Papirus
         |fields=filename,name,generic,exec,categories,keywords
         |lines=10
         |width=40
         |horizontal-pad=20
         |vertical-pad=12
         |inner-pad=8
         |
         |[border]
         |width=1
         |radius=6
         |
         |[colors]
         |background=${baseClean}f5
         |text=${textClean}ff
         |match=${accentClean}ff
         |selection=1c2128ff
         |selection-text=${textClean}ff
         |selection-match=${accentClean}ff
         |border=${accentClean}ff
         |""".stripMargin
    os.write.over(fuzzelConfigFile, fuzzelContent)
    val dotfilesFuzzelFile = ctx.dotfilesDir / "config" / "fuzzel" / "fuzzel.ini"
    if !ctx.isTest && os.exists(ctx.dotfilesDir / "config") && dotfilesFuzzelFile != fuzzelConfigFile then
      try
        os.makeDir.all(dotfilesFuzzelFile / os.up)
        os.write.over(dotfilesFuzzelFile, fuzzelContent)
      catch case _: Exception => ()
    println(s"  \u001b[32m[OK]\u001b[0m Rendered Fuzzel config -> $fuzzelConfigFile")

    // 6. Render Swaylock config (~/.config/swaylock/config)
    val swaylockDir = ctx.configDir / "swaylock"
    os.makeDir.all(swaylockDir)
    val swaylockConfigFile = swaylockDir / "config"
    val baseHex = palette.base.stripPrefix("#")
    val mantleHex = palette.mantle.stripPrefix("#")
    val textHex = palette.text.stripPrefix("#")
    val accentHex = palette.accent.stripPrefix("#")
    val redHex = palette.red.stripPrefix("#")

    val imageSetting = if activeWallpaper.nonEmpty then s"image=$activeWallpaper\nscaling=fill" else s"color=$baseHex"

    val swaylockContent =
      s"""# Generated by polyomino dotfiles theme engine
         |$imageSetting
         |font=JetBrainsMono Nerd Font
         |font-size=26
         |indicator-radius=115
         |indicator-thickness=14
         |ring-color=$mantleHex
         |inside-color=${baseHex}CC
         |text-color=$textHex
         |key-hl-color=$accentHex
         |bs-hl-color=$redHex
         |line-color=$mantleHex
         |inside-ver-color=${accentHex}CC
         |ring-ver-color=$accentHex
         |inside-wrong-color=${redHex}CC
         |ring-wrong-color=$redHex
         |inside-clear-color=${baseHex}CC
         |ring-clear-color=$accentHex
         |text-clear-color=$textHex
         |text-ver-color=$textHex
         |text-wrong-color=$textHex
         |show-failed-attempts
         |""".stripMargin
    os.write.over(swaylockConfigFile, swaylockContent)
    println(s"  \u001b[32m[OK]\u001b[0m Rendered Swaylock config -> $swaylockConfigFile")

    // 7. Update active-theme symlink for compatibility
    val activeThemeSymlink = swayDir / "active-theme"
    try
      if os.exists(activeThemeSymlink) || os.isLink(activeThemeSymlink) then os.remove(activeThemeSymlink)
      os.write.over(activeThemeSymlink, s"THEME_NAME=${palette.name}\nACCENT_COLOR=${palette.accent}\n")
    catch
      case _: Exception => ()

    // 8. Apply wallpaper live via Sway output bg
    if !ctx.isTest && ctx.swaySocket.isDefined && activeWallpaper.nonEmpty then
      try
        os.proc("timeout", "2", "swaymsg", "output", "*", "bg", activeWallpaper, "fill").call(check = false, stdout = os.Pipe, stderr = os.Pipe)
        println(s"  \u001b[32m[OK]\u001b[0m Applied wallpaper via Sway -> $activeWallpaper")
      catch
        case _: Exception => ()

    // 8b. Render Mako notification daemon config (~/.config/mako/config)
    val makoDir = ctx.configDir / "mako"
    os.makeDir.all(makoDir)
    val makoFile = makoDir / "config"
    val makoContent =
      s"""# Mako notification daemon configuration
         |# Generated by polyomino.dotfiles theme engine — flavor=${palette.name}
         |
         |output=*
         |width=360
         |margin=10
         |padding=12
         |border-size=1
         |border-radius=6
         |background-color=${palette.surface0}
         |border-color=${palette.accent}
         |text-color=${palette.text}
         |font=JetBrainsMono Nerd Font 10
         |format=⊞ %s\\n%b
         |default-timeout=5000
         |on-button-left=dismiss
         |on-button-middle=dismiss-all
         |on-button-right=dismiss-all
         |on-touch=dismiss
         |""".stripMargin
    os.write.over(makoFile, makoContent)
    val dotfilesMakoFile = ctx.dotfilesDir / "config" / "mako" / "config"
    if !ctx.isTest && os.exists(ctx.dotfilesDir / "config") && dotfilesMakoFile != makoFile then
      try
        os.makeDir.all(dotfilesMakoFile / os.up)
        os.write.over(dotfilesMakoFile, makoContent)
      catch case _: Exception => ()
    println(s"  \u001b[32m[OK]\u001b[0m Rendered Mako config -> $makoFile")

    // 8c. Render SwayNC style stylesheet (~/.config/swaync/style.css)
    val swayncDir = ctx.configDir / "swaync"
    os.makeDir.all(swayncDir)
    val swayncFile = swayncDir / "style.css"
    val swayncContent =
      s"""/* Generated by polyomino.dotfiles theme engine — flavor=${palette.name} */
         |* {
         |    font-family: "JetBrainsMono Nerd Font", sans-serif;
         |    font-size: 13px;
         |}
         |
         |.control-center {
         |    background-color: ${palette.base};
         |    color: ${palette.text};
         |    border: 1px solid ${palette.accent};
         |    border-radius: 8px;
         |    padding: 14px;
         |}
         |
         |.control-center-list {
         |    background: transparent;
         |}
         |
         |.floating-notifications {
         |    background: transparent;
         |}
         |
         |.notification-row {
         |    outline: none;
         |    margin: 6px 0;
         |}
         |
         |.notification {
         |    background-color: ${palette.surface0};
         |    border: 1px solid ${palette.overlay0};
         |    border-radius: 6px;
         |    padding: 10px;
         |    color: ${palette.text};
         |}
         |
         |.notification:hover {
         |    border-color: ${palette.accent};
         |}
         |
         |.notification-content {
         |    background: transparent;
         |    padding: 4px;
         |}
         |
         |.notification-default-action,
         |.notification-action {
         |    background: transparent;
         |    border: none;
         |    border-radius: 4px;
         |    box-shadow: none;
         |    padding: 4px;
         |    margin: 0;
         |    color: ${palette.text};
         |}
         |
         |.notification-default-action:hover,
         |.notification-action:hover {
         |    background-color: ${palette.surface1};
         |}
         |
         |.close-button {
         |    background-color: ${palette.surface0};
         |    color: ${palette.text};
         |    border: 1px solid ${palette.overlay0};
         |    border-radius: 4px;
         |    margin: 6px;
         |    padding: 2px;
         |    min-width: 24px;
         |    min-height: 24px;
         |    box-shadow: none;
         |}
         |
         |.close-button:hover {
         |    background-color: ${palette.red};
         |    color: ${palette.base};
         |    border-color: ${palette.red};
         |}
         |
         |.widget-title {
         |    color: ${palette.text};
         |    font-size: 15px;
         |    font-weight: bold;
         |    margin: 8px;
         |}
         |
         |.widget-title > button {
         |    background-color: ${palette.surface0};
         |    color: ${palette.text};
         |    border: 1px solid ${palette.overlay0};
         |    border-radius: 6px;
         |    padding: 4px 10px;
         |}
         |
         |.widget-title > button:hover {
         |    background-color: ${palette.accent};
         |    color: ${palette.base};
         |    border-color: ${palette.accent};
         |}
         |
         |switch,
         |switch trough,
         |switch slider {
         |    border-radius: 4px;
         |    box-shadow: none;
         |    outline: none;
         |}
         |
         |.widget-dnd {
         |    background-color: ${palette.surface0};
         |    border: 1px solid ${palette.overlay0};
         |    border-radius: 6px;
         |    padding: 8px 12px;
         |    margin: 8px;
         |}
         |
         |.widget-dnd > switch,
         |.widget-dnd > switch trough {
         |    background-color: ${palette.base};
         |    border-radius: 4px;
         |    border: 1px solid ${palette.overlay0};
         |    box-shadow: none;
         |}
         |
         |.widget-dnd > switch:checked,
         |.widget-dnd > switch:checked trough {
         |    background-color: ${palette.accent};
         |    border: 1px solid ${palette.accent};
         |    box-shadow: none;
         |}
         |
         |.widget-dnd > switch slider,
         |.widget-dnd > switch > slider {
         |    background-color: ${palette.accent};
         |    border-radius: 4px;
         |    border: 1px solid ${palette.accent};
         |    box-shadow: none;
         |    outline: none;
         |}
         |
         |.widget-dnd > switch:checked slider,
         |.widget-dnd > switch:checked > slider {
         |    background-color: ${palette.base};
         |    border-radius: 4px;
         |    border: 1px solid ${palette.base};
         |    box-shadow: none;
         |    outline: none;
         |}
         |
         |.widget-mpris {
         |    background-color: ${palette.surface0};
         |    border: 1px solid ${palette.overlay0};
         |    border-radius: 6px;
         |    padding: 8px;
         |    margin: 8px;
         |}
         |
         |.widget-mpris-title {
         |    font-weight: bold;
         |    color: ${palette.text};
         |}
         |
         |.widget-mpris-subtitle {
         |    color: ${palette.text};
         |    opacity: 0.8;
         |}
         |
         |.widget-calendar {
         |    background-color: ${palette.surface0};
         |    border: 1px solid ${palette.overlay0};
         |    border-radius: 6px;
         |    padding: 12px;
         |    margin: 8px;
         |    font-size: 14px;
         |}
         |
         |.widget-calendar > calendar {
         |    background-color: transparent;
         |    color: ${palette.text};
         |    font-size: 14px;
         |    padding: 4px;
         |}
         |
         |.widget-calendar > calendar:selected {
         |    background-color: ${palette.accent};
         |    color: ${palette.base};
         |    border-radius: 4px;
         |}
         |
         |.widget-calendar > calendar.header {
         |    color: ${palette.accent};
         |    font-weight: bold;
         |    font-size: 15px;
         |    padding: 6px;
         |}
         |
         |.widget-calendar > calendar.button {
         |    color: ${palette.text};
         |    background: transparent;
         |    border-radius: 4px;
         |    padding: 4px 8px;
         |}
         |
         |.widget-calendar > calendar.button:hover {
         |    background-color: ${palette.accent};
         |    color: ${palette.base};
         |}
         |""".stripMargin
    os.write.over(swayncFile, swayncContent)
    println(s"  \u001b[32m[OK]\u001b[0m Rendered SwayNC style -> $swayncFile")

    // 9. Trigger live reloads across desktop applications
    polyomino.dotfiles.refresh.RefreshEngine.runRefresh(ctx)
    Right(())

  def resolveWallpaperForFlavor(ctx: Context, flavor: String): Option[String] =
    val wallpapersDir = ctx.dotfilesDir / "themes" / "wallpapers"
    if os.exists(wallpapersDir) then
      // Prefer any wallpaper that belongs to this flavor (`<flavor>.svg`,
      // `<flavor>_2.png`, `<flavor>-nebula.svg`, …); WallpaperEngine owns the
      // matching rules and is the source of truth for the picker/cycler too.
      polyomino.dotfiles.wallpaper.WallpaperEngine
        .wallpapersForFlavor(ctx, flavor)
        .headOption
        .map(_.toString)
        .orElse {
          os.list(wallpapersDir).find(f => f.ext == "svg" || f.ext == "png" || f.ext == "jpg").map(_.toString)
        }
    else None

  def generateDynamicCanvasSvg(palette: Palette, targetPath: os.Path): Unit =
    val content =
      s"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 3840 2160" width="100%" height="100%">
         |  <!-- Monolith Dark Canvas (${palette.base}) -->
         |  <rect width="3840" height="2160" fill="${palette.base}" />
         |
         |  <!-- CAD Coordinate Dot Matrix Grid (${palette.overlay0} / ${palette.crust}) -->
         |  <defs>
         |    <pattern id="dot-matrix" x="0" y="0" width="48" height="48" patternUnits="userSpaceOnUse">
         |      <circle cx="24" cy="24" r="1.2" fill="${palette.overlay0}" fill-opacity="0.5" />
         |    </pattern>
         |  </defs>
         |  <rect width="3840" height="2160" fill="url(#dot-matrix)" />
         |
         |  <!-- Subtle Structural Grid Crosshairs -->
         |  <g stroke="${palette.crust}" stroke-width="1.5" fill="none">
         |    <line x1="0" y1="1080" x2="3840" y2="1080" stroke-dasharray="16,16" opacity="0.4" />
         |    <line x1="1920" y1="0" x2="1920" y2="2160" stroke-dasharray="16,16" opacity="0.4" />
         |  </g>
         |
         |  <!-- Center CAD Viewport Box -->
         |  <g transform="translate(1920, 1080)" fill="none">
         |    <rect x="-360" y="-240" width="720" height="480" stroke="${palette.crust}" stroke-width="1" />
         |    <!-- Corner Brackets -->
         |    <path d="M -360,-216 L -360,-240 L -336,-240" stroke="${palette.overlay0}" stroke-width="2" />
         |    <path d="M 336,-240 L 360,-240 L 360,-216" stroke="${palette.overlay0}" stroke-width="2" />
         |    <path d="M 360,216 L 360,240 L 336,240" stroke="${palette.overlay0}" stroke-width="2" />
         |    <path d="M -336,240 L -360,240 L -360,216" stroke="${palette.overlay0}" stroke-width="2" />
         |    <!-- Primary Accent Focal Mark -->
         |    <circle cx="0" cy="0" r="4" fill="${palette.blue}" />
         |    <circle cx="0" cy="0" r="12" stroke="${palette.teal}" stroke-width="1" stroke-dasharray="3,3" />
         |  </g>
         |
         |  <!-- Bottom-Left Technical HUD -->
         |  <g transform="translate(140, 1970)">
         |    <path d="M 0,-70 L 0,35 L 280,35" stroke="${palette.crust}" stroke-width="1.5" fill="none" />
         |    <rect x="-4" y="-70" width="8" height="8" fill="${palette.blue}" />
         |    <text x="25" y="-20" fill="${palette.text}"
         |      font-family="'JetBrains Mono', 'Fira Code', monospace" font-size="32"
         |      font-weight="700" letter-spacing="0.25em">${palette.name.toUpperCase}</text>
         |    <text x="26" y="18" fill="${palette.blue}"
         |      font-family="'JetBrains Mono', 'Fira Code', monospace" font-size="16"
         |      font-weight="600" letter-spacing="0.2em">POLYOMINO</text>
         |  </g>
         |</svg>
         |""".stripMargin
    try
      os.makeDir.all(targetPath / os.up)
      os.write.over(targetPath, content)
    catch case _: Exception => ()

  def listWallpapers(ctx: Context): Seq[String] =
    val wallpapersDir = ctx.dotfilesDir / "themes" / "wallpapers"
    if os.exists(wallpapersDir) then
      os.list(wallpapersDir)
        .filter(f => f.ext == "svg" || f.ext == "png" || f.ext == "jpg")
        .map(_.toString)
        .sorted
    else Nil
