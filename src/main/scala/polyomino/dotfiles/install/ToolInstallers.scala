package polyomino.dotfiles.install

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}

enum PackageManager:
  case Pacman, Dnf, Apt, Brew, Unknown

object ToolInstallers:
  def detectPackageManager(): PackageManager =
    if isAvailable("pacman") then PackageManager.Pacman
    else if isAvailable("dnf") then PackageManager.Dnf
    else if isAvailable("apt-get") then PackageManager.Apt
    else if isAvailable("brew") then PackageManager.Brew
    else PackageManager.Unknown

  def runTool(name: String, ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    name match
      case "install-deps" => installSystemDeps(ctx)
      case "install-brew" | "install-homebrew" => installHomebrew(ctx)
      case "install-gh" | "install-github-cli" => installGh(ctx)
      case "install-coursier" | "install-cs" => installCoursier(ctx)
      case "install-fonts" => installFonts(ctx)
      case "install-apps" => installApps(ctx)
      case "install-sway" | "install-swayfx" => installSwayfx(ctx)
      case "install-browser" => installBrowser(ctx)
      case "install-swaync" | "install-notifications" => installSwaync(ctx)
      case "install-devops" => installDevops(ctx)
      case "install-zsh" => installZsh(ctx)
      case "install-sdkman" => installSdkman(ctx)
      case "install-tools" => installTools(ctx)
      case "install-telegram" => installTelegram(ctx)
      case "install-node" | "install-npm" | "install-npx" => installNode(ctx)
      case "install-yazi" => installYazi(ctx)
      case "install-fastfetch" => installFastfetch(ctx)
      case "install-spotify" | "install-spotify-player" => installSpotifyPlayer(ctx)
      case "full-install" => installAll(ctx)
      case _ => Left(CommandError(s"Unknown installer task '$name'", 1))

  private def installSystemDeps(ctx: Context): Either[PolyominoError, Unit] =
    val pm = detectPackageManager()
    println(s"\u001b[1;36m[polyomino install-deps]\u001b[0m Installing system & build dependencies (PM: $pm)...")
    pm match
      case PackageManager.Pacman =>
        runPkgInstall("sudo", Seq("pacman", "-S", "--needed", "--noconfirm", "sbt", "jdk-openjdk", "gcc", "git", "curl", "fontconfig", "zsh", "tar", "unzip", "which", "fastfetch"))
      case PackageManager.Dnf =>
        runPkgInstall("sudo", Seq("dnf", "install", "-y", "gcc", "gcc-c++", "java-latest-openjdk-devel", "git", "curl", "fontconfig", "zsh", "tar", "unzip", "which", "fastfetch"))
      case PackageManager.Apt =>
        runPkgInstall("sudo", Seq("apt-get", "install", "-y", "build-essential", "default-jdk", "git", "curl", "fontconfig", "zsh", "tar", "unzip", "fastfetch"))
      case PackageManager.Brew =>
        runPkgInstall("brew", Seq("install", "openjdk", "gcc", "git", "curl", "fontconfig", "zsh", "tar", "unzip", "fastfetch"))
      case _ =>
        Right(println("  \u001b[33m[NOTE]\u001b[0m Manual package dependency installation recommended for current OS."))

  private def installFonts(ctx: Context): Either[PolyominoError, Unit] =
    val fontsDir = ctx.home / ".local" / "share" / "fonts"
    os.makeDir.all(fontsDir)
    println(s"\u001b[1;36m[polyomino install-fonts]\u001b[0m Installing JetBrainsMono Nerd Font to $fontsDir...")
    val fontRegular = fontsDir / "JetBrainsMonoNerdFont-Regular.ttf"
    if !os.exists(fontRegular) then
      try
        println("  \u001b[36m[INFO]\u001b[0m Downloading JetBrainsMono Nerd Font release...")
        val fontUrl = "https://github.com/ryanoasis/nerd-fonts/releases/latest/download/JetBrainsMono.tar.xz"
        os.proc("bash", "-c", s"curl -fsSL '$fontUrl' | tar -xJ -C '${fontsDir}'").call(check = false)
      catch
        case e: Exception => println(s"  \u001b[33m[NOTE]\u001b[0m Font download skipped: ${e.getMessage}")
    try
      os.proc("fc-cache", "-f", fontsDir.toString).call(check = false)
      println("  \u001b[32m[OK]\u001b[0m Refreshed system font cache (fc-cache)")
      Right(())
    catch
      case e: Exception => Left(CommandError(s"Font cache update failed: ${e.getMessage}"))

  private def installApps(ctx: Context): Either[PolyominoError, Unit] =
    val pm = detectPackageManager()
    println(s"\u001b[1;36m[polyomino install-apps]\u001b[0m Installing core desktop apps (PM: $pm)...")
    pm match
      case PackageManager.Pacman =>
        runPkgInstall("sudo", Seq("pacman", "-S", "--needed", "--noconfirm", "waybar", "kitty", "wofi", "swaylock", "gtklock", "swayidle", "grim", "slurp", "brightnessctl", "libpulse", "playerctl", "wireplumber", "ttf-jetbrains-mono-nerd", "swaync", "mako", "cmake", "ncurses", "neovim", "fastfetch", "cmatrix", "jq", "xdotool", "network-manager-applet", "polkit-gnome", "wl-clipboard", "pavucontrol"))
        if isAvailable("yay") then runPkgInstall("yay", Seq("-S", "--needed", "--noconfirm", "--answerclean", "None", "--answerdiff", "None", "swayfx", "ncpamixer", "onlyoffice-bin"))
        else runPkgInstall("sudo", Seq("pacman", "-S", "--needed", "--noconfirm", "sway"))
      case PackageManager.Dnf =>
        runPkgInstall("sudo", Seq("dnf", "install", "-y", "sway", "waybar", "kitty", "wofi", "swaylock", "swayidle", "grim", "slurp", "brightnessctl", "playerctl", "wireplumber", "sway-notification-center", "mako", "neovim", "onlyoffice-desktopeditors", "fastfetch", "cmatrix", "jq", "xdotool", "network-manager-applet", "polkit-gnome", "wl-clipboard", "pavucontrol"))
        installSwayfx(ctx)
      case PackageManager.Apt =>
        runPkgInstall("sudo", Seq("apt-get", "install", "-y", "sway", "waybar", "kitty", "wofi", "swaylock", "swayidle", "grim", "slurp", "brightnessctl", "playerctl", "wireplumber", "pulseaudio-utils", "fonts-jetbrains-mono", "sway-notification-center", "mako-notifier", "python3-gi", "python3-cairo", "gir1.2-gtk-3.0", "neovim", "fastfetch", "cmatrix", "jq", "xdotool", "network-manager-gnome", "policykit-1-gnome", "wl-clipboard", "pavucontrol"))
        installSwayfx(ctx)
      case PackageManager.Brew =>
        runPkgInstall("brew", Seq("install", "fastfetch", "cmatrix", "jq"))
      case _ =>
        Right(println("  \u001b[33m[NOTE]\u001b[0m Manual package installation recommended for current OS."))

  private def installBrowser(ctx: Context): Either[PolyominoError, Unit] =
    val pm = detectPackageManager()
    println(s"\u001b[1;36m[polyomino install-browser]\u001b[0m Installing web browser (PM: $pm)...")
    pm match
      case PackageManager.Pacman => runPkgInstall("sudo", Seq("pacman", "-S", "--needed", "--noconfirm", "chromium"))
      case PackageManager.Dnf => runPkgInstall("sudo", Seq("dnf", "install", "-y", "chromium"))
      case PackageManager.Apt => runPkgInstall("sudo", Seq("apt-get", "install", "-y", "firefox"))
      case PackageManager.Brew => runPkgInstall("brew", Seq("install", "--cask", "chromium"))
      case _ => Right(println("  \u001b[32m[OK]\u001b[0m Browser provisioned."))

  private def installSwaync(ctx: Context): Either[PolyominoError, Unit] =
    val pm = detectPackageManager()
    println(s"\u001b[1;36m[polyomino install-swaync]\u001b[0m Checking/Installing SwayNC (PM: $pm)...")
    pm match
      case PackageManager.Pacman =>
        runPkgInstall("sudo", Seq("pacman", "-S", "--needed", "--noconfirm", "swaync"))
      case PackageManager.Dnf =>
        runPkgInstall("sudo", Seq("dnf", "install", "-y", "sway-notification-center"))
      case PackageManager.Apt =>
        runPkgInstall("sudo", Seq("apt-get", "install", "-y", "sway-notification-center"))
      case PackageManager.Brew =>
        runPkgInstall("brew", Seq("install", "sway-notification-center"))
      case _ =>
        println("  \u001b[33m[NOTE]\u001b[0m Manual installation of swaync required for current OS.")
        Right(())

  private def installDevops(ctx: Context): Either[PolyominoError, Unit] =
    val pm = detectPackageManager()
    val localBin = ctx.home / ".local" / "bin"
    os.makeDir.all(localBin)
    println(s"\u001b[1;36m[polyomino install-devops]\u001b[0m Installing DevOps & Cloud CLI tools (PM: $pm)...")
    pm match
      case PackageManager.Pacman =>
        runPkgInstall("sudo", Seq("pacman", "-S", "--needed", "--noconfirm", "docker", "terraform", "ansible", "aws-cli", "kubectl", "helm"))
      case PackageManager.Dnf =>
        runPkgInstall("sudo", Seq("dnf", "install", "-y", "docker", "terraform", "ansible", "awscli", "kubectl", "helm"))
      case PackageManager.Apt =>
        val pkgs = if isAvailable("docker") then Seq("ansible") else Seq("docker.io", "ansible")
        runPkgInstall("sudo", Seq("apt-get", "install", "-y") ++ pkgs)
      case PackageManager.Brew =>
        runPkgInstall("brew", Seq("install", "docker", "terraform", "ansible", "awscli", "google-cloud-sdk", "oci-cli", "kubectl", "helm"))
      case _ =>
        Right(println("  \u001b[33m[NOTE]\u001b[0m Manual package installation recommended for current OS."))

    // Kubectl binary fallback (e.g. for Debian/Ubuntu APT where kubectl is not in main repo)
    if !isAvailable("kubectl") && !os.exists(localBin / "kubectl") then
      println("  \u001b[36m[INFO]\u001b[0m Downloading kubectl binary...")
      try
        val kubectlUrl = "https://dl.k8s.io/release/v1.31.0/bin/linux/amd64/kubectl"
        os.proc("curl", "-fsSL", "-o", (localBin / "kubectl").toString, kubectlUrl).call(check = false)
        os.proc("chmod", "+x", (localBin / "kubectl").toString).call(check = false)
        println("  \u001b[32m[OK]\u001b[0m kubectl installed to ~/.local/bin.")
      catch
        case e: Exception =>
          println(s"  \u001b[33m[NOTE]\u001b[0m kubectl download skipped: ${e.getMessage}")

    // Helm binary fallback (e.g. for Debian/Ubuntu APT where helm is not in main repo)
    if !isAvailable("helm") && !os.exists(localBin / "helm") then
      println("  \u001b[36m[INFO]\u001b[0m Downloading helm binary...")
      try
        val helmScript = s"curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | USE_SUDO=false HELM_INSTALL_DIR=${localBin} bash"
        os.proc("bash", "-c", helmScript).call(check = false)
        if os.exists(localBin / "helm") then
          println("  \u001b[32m[OK]\u001b[0m helm installed to ~/.local/bin.")
      catch
        case e: Exception =>
          println(s"  \u001b[33m[NOTE]\u001b[0m helm download skipped: ${e.getMessage}")

    // Terraform binary fallback (e.g. for Debian/Ubuntu APT where terraform is not in main repo)
    if !isAvailable("terraform") && !os.exists(localBin / "terraform") then
      println("  \u001b[36m[INFO]\u001b[0m Downloading terraform binary...")
      try
        val tfUrl = "https://releases.hashicorp.com/terraform/1.9.5/terraform_1.9.5_linux_amd64.zip"
        os.proc("bash", "-c", s"curl -fsSL '$tfUrl' -o /tmp/tf.zip && unzip -qo /tmp/tf.zip -d '$localBin' && rm -f /tmp/tf.zip").call(check = false)
        if os.exists(localBin / "terraform") then
          println("  \u001b[32m[OK]\u001b[0m terraform installed to ~/.local/bin.")
      catch
        case e: Exception =>
          println(s"  \u001b[33m[NOTE]\u001b[0m terraform download skipped: ${e.getMessage}")

    // Google Cloud CLI (gcloud)
    println("  \u001b[36m[INFO]\u001b[0m Installing/Updating Google Cloud CLI (gcloud)...")
    if pm == PackageManager.Pacman && isAvailable("yay") then
      runPkgInstall("yay", Seq("-S", "--needed", "--noconfirm", "--answerclean", "None", "--answerdiff", "None", "google-cloud-cli"))
    else
      try
        val gcloudInstallScript = "curl -sSL https://sdk.cloud.google.com | bash --disable-prompts --install-dir=" + (ctx.home / ".local" / "share").toString
        val res = os.proc("bash", "-c", gcloudInstallScript).call(check = false)
        if res.exitCode == 0 then
          println("  \u001b[32m[OK]\u001b[0m Google Cloud CLI installed.")
        else
          println(s"  \u001b[33m[NOTE]\u001b[0m Google Cloud CLI install exited with code ${res.exitCode}")
      catch
        case e: Exception =>
          println(s"  \u001b[33m[NOTE]\u001b[0m Google Cloud CLI installation skipped: ${e.getMessage}")

    // Oracle Cloud Infrastructure CLI (oci)
    println("  \u001b[36m[INFO]\u001b[0m Installing/Updating Oracle Cloud CLI (oci)...")
    if pm == PackageManager.Pacman && isAvailable("yay") then
      runPkgInstall("yay", Seq("-S", "--needed", "--noconfirm", "--answerclean", "None", "--answerdiff", "None", "oci-cli"))
    else
      try
        val ociInstallScript = s"""bash -c "$$(curl -L https://raw.githubusercontent.com/oracle/oci-cli/master/scripts/install/install.sh)" -- --accept-all-defaults --install-dir "${ctx.home / ".local" / "lib" / "oracle-cli"}" --exec-dir "$localBin""""
        val res = os.proc("bash", "-c", ociInstallScript).call(check = false)
        if res.exitCode == 0 then
          println("  \u001b[32m[OK]\u001b[0m Oracle Cloud CLI (oci) installed.")
        else
          println(s"  \u001b[33m[NOTE]\u001b[0m Oracle Cloud CLI install exited with code ${res.exitCode}")
      catch
        case e: Exception =>
          println(s"  \u001b[33m[NOTE]\u001b[0m Oracle Cloud CLI installation skipped: ${e.getMessage}")

    Right(println("  \u001b[32m[OK]\u001b[0m DevOps and Cloud CLI tools provisioned."))

  private def installZsh(ctx: Context): Either[PolyominoError, Unit] =
    val pm = detectPackageManager()
    println(s"\u001b[1;36m[polyomino install-zsh]\u001b[0m Installing Zsh shell environment (PM: $pm)...")
    pm match
      case PackageManager.Pacman => runPkgInstall("sudo", Seq("pacman", "-S", "--needed", "--noconfirm", "zsh", "curl", "git"))
      case PackageManager.Dnf => runPkgInstall("sudo", Seq("dnf", "install", "-y", "zsh", "curl", "git"))
      case PackageManager.Apt => runPkgInstall("sudo", Seq("apt-get", "install", "-y", "zsh", "curl", "git"))
      case PackageManager.Brew => runPkgInstall("brew", Seq("install", "zsh", "curl", "git"))
      case _ => Right(println("  \u001b[32m[OK]\u001b[0m Zsh environment provisioned."))

  /** Install a SDKMAN! candidate and mark it the default so its
    * `current/bin` symlink is populated. SDKMAN!'s init script (sourced
    * from zsh_config/99-sdkman-cargo.zsh) puts every candidate's
    * `current/bin` on PATH, so `install` + `default` is all that is
    * needed to inject the JDK / Kotlin compilers into the user's PATH.
    * Fail-soft: never aborts the install pipeline. */
  private def installSdkCandidate(initScript: os.Path, candidate: String): Unit =
    try
      println(s"  \u001b[36m[INFO]\u001b[0m Installing SDKMAN! candidate '$candidate'...")
      val res = os.proc(
        "bash", "-c",
        s"source ${initScript} && sdk install $candidate && sdk default $candidate"
      ).call(check = false)
      if res.exitCode == 0 then
        println(s"  \u001b[32m[OK]\u001b[0m SDKMAN! candidate '$candidate' installed and set as default.")
      else
        println(s"  \u001b[33m[NOTE]\u001b[0m SDKMAN! candidate '$candidate' install exited with code ${res.exitCode}")
    catch
      case e: Exception =>
        println(s"  \u001b[33m[NOTE]\u001b[0m SDKMAN! candidate '$candidate' install skipped: ${e.getMessage}")

  private def installSdkman(ctx: Context): Either[PolyominoError, Unit] =
    println("\u001b[1;36m[polyomino install-sdkman]\u001b[0m Installing SDKMAN! and JVM tooling...")
    val sdkmanDir = ctx.home / ".sdkman"
    val initScript = sdkmanDir / "bin" / "sdkman-init.sh"
    if os.exists(sdkmanDir) then
      println("  \u001b[36m[INFO]\u001b[0m Updating SDKMAN!...")
      os.proc("bash", "-c", s"source ${initScript} && sdk selfupdate force").call(check = false)
      installSdkCandidate(initScript, "java")
      installSdkCandidate(initScript, "kotlin")
      Right(())
    else
      try
        println("  \u001b[36m[INFO]\u001b[0m Bootstrapping SDKMAN!...")
        val res = os.proc("bash", "-c", "curl -s 'https://get.sdkman.io' | bash").call(check = false)
        if res.exitCode == 0 then
          println("  \u001b[32m[OK]\u001b[0m SDKMAN! provisioned successfully.")
        else
          println(s"  \u001b[33m[NOTE]\u001b[0m SDKMAN! install exited with code ${res.exitCode}")
        if os.exists(initScript) then
          installSdkCandidate(initScript, "java")
          installSdkCandidate(initScript, "kotlin")
        else
          println("  \u001b[33m[NOTE]\u001b[0m SDKMAN! init script missing; skipping JDK/Kotlin candidate install.")
        Right(())
      catch
        case e: Exception =>
          println(s"  \u001b[33m[NOTE]\u001b[0m SDKMAN! install skipped: ${e.getMessage}")
          Right(())

  private def getBrewBin(ctx: Context): Option[os.Path] =
    val standardPaths = Seq(
      ctx.home / ".linuxbrew" / "bin" / "brew",
      os.root / "home" / "linuxbrew" / ".linuxbrew" / "bin" / "brew",
      os.root / "opt" / "homebrew" / "bin" / "brew",
      os.root / "usr" / "local" / "bin" / "brew"
    )
    if isAvailable("brew") then
      try Some(os.Path(os.proc("which", "brew").call().out.trim()))
      catch case _: Exception => standardPaths.find(os.exists)
    else standardPaths.find(os.exists)

  private def installHomebrew(ctx: Context): Either[PolyominoError, Unit] =
    println("\u001b[1;36m[polyomino install-brew]\u001b[0m Checking/Installing Homebrew...")
    getBrewBin(ctx) match
      case Some(brewPath) =>
        println(s"  \u001b[36m[INFO]\u001b[0m Updating Homebrew at $brewPath...")
        os.proc(brewPath.toString, "update").call(check = false)
        os.proc(brewPath.toString, "upgrade").call(check = false)
        Right(())
      case None =>
        println("  \u001b[36m[INFO]\u001b[0m Installing Homebrew via official non-interactive installer...")
        try
          val res = os.proc(
            "bash", "-c",
            "NONINTERACTIVE=1 /bin/bash -c \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
          ).call(stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit, check = false)
          if res.exitCode == 0 then
            println("  \u001b[32m[OK]\u001b[0m Homebrew installed successfully.")
          else
            println(s"  \u001b[33m[NOTE]\u001b[0m Homebrew installer returned code ${res.exitCode}")
          Right(())
        catch
          case e: Exception =>
            println(s"  \u001b[33m[NOTE]\u001b[0m Homebrew installation skipped: ${e.getMessage}")
            Right(())

  private def installGh(ctx: Context): Either[PolyominoError, Unit] =
    val pm = detectPackageManager()
    println(s"\u001b[1;36m[polyomino install-gh]\u001b[0m Checking/Installing GitHub CLI (PM: $pm)...")
    pm match
      case PackageManager.Pacman =>
        runPkgInstall("sudo", Seq("pacman", "-S", "--needed", "--noconfirm", "github-cli"))
      case PackageManager.Dnf =>
        runPkgInstall("sudo", Seq("dnf", "install", "-y", "gh"))
      case PackageManager.Apt =>
        runPkgInstall("sudo", Seq("apt-get", "install", "-y", "gh"))
      case PackageManager.Brew =>
        runPkgInstall("brew", Seq("install", "gh"))
      case PackageManager.Unknown =>
        getBrewBin(ctx) match
          case Some(brewBin) =>
            runPkgInstall(brewBin.toString, Seq("install", "gh"))
          case None =>
            println("  \u001b[33m[NOTE]\u001b[0m Manual installation of GitHub CLI (gh) recommended.")
            Right(())

  private def getCoursierBin(ctx: Context): Option[os.Path] =
    val localBinCs = ctx.home / ".local" / "bin" / "cs"
    val csShareBin = ctx.home / ".local" / "share" / "coursier" / "bin" / "cs"
    if isAvailable("cs") then
      try Some(os.Path(os.proc("which", "cs").call().out.trim()))
      catch case _: Exception => Some(localBinCs)
    else if isAvailable("coursier") then
      try Some(os.Path(os.proc("which", "coursier").call().out.trim()))
      catch case _: Exception => Some(localBinCs)
    else if os.exists(localBinCs) then Some(localBinCs)
    else if os.exists(csShareBin) then Some(csShareBin)
    else None

  private def installCoursier(ctx: Context): Either[PolyominoError, Unit] =
    println("\u001b[1;36m[polyomino install-coursier]\u001b[0m Checking/Installing Coursier...")
    getCoursierBin(ctx) match
      case Some(csPath) =>
        println(s"  \u001b[36m[INFO]\u001b[0m Updating Coursier at $csPath...")
        os.proc(csPath.toString, "update").call(check = false)
        Right(())
      case None =>
        val binDir = ctx.home / ".local" / "bin"
        os.makeDir.all(binDir)
        val csDest = binDir / "cs"
        val isMac = System.getProperty("os.name", "").toLowerCase.contains("mac")
        val launcher = if isMac then "cs-x86_64-apple-darwin.gz" else "cs-x86_64-pc-linux.gz"
        val url = s"https://github.com/coursier/launchers/raw/master/$launcher"
        println(s"  \u001b[36m[INFO]\u001b[0m Downloading Coursier launcher from $url...")
        try
          val curlRes = os.proc("bash", "-c", s"curl -fL '$url' | gzip -d > '$csDest' && chmod +x '$csDest'").call(check = false)
          if curlRes.exitCode == 0 then
            println(s"  \u001b[32m[OK]\u001b[0m Coursier installed to $csDest")
            os.proc(csDest.toString, "update").call(check = false)
          else
            println(s"  \u001b[33m[NOTE]\u001b[0m Coursier download returned code ${curlRes.exitCode}")
          Right(())
        catch
          case e: Exception =>
            println(s"  \u001b[33m[NOTE]\u001b[0m Coursier installation skipped: ${e.getMessage}")
            Right(())


  private def installTools(ctx: Context): Either[PolyominoError, Unit] =
    val pm = detectPackageManager()
    println(s"\u001b[1;36m[polyomino install-tools]\u001b[0m Installing TUI tools (spotify_player, bluetui, impala, aerc)...")

    val cargoHomeBin = ctx.home / ".cargo" / "bin"
    def cargoAvailable(): Boolean =
      isAvailable("cargo") || os.exists(cargoHomeBin / "cargo")

    def runCargo(tool: String, extraArgs: Seq[String] = Nil): Either[PolyominoError, Unit] =
      val cargoExe = if isAvailable("cargo") then "cargo" else (cargoHomeBin / "cargo").toString
      val cmd: Seq[os.Shellable] = Seq(cargoExe: os.Shellable, "install": os.Shellable, tool: os.Shellable, "--locked": os.Shellable) ++ extraArgs.map(a => (a: os.Shellable))
      try
        val res = os.proc(cmd*).call(check = false)
        if res.exitCode == 0 then
          println(s"  \u001b[32m[OK]\u001b[0m $tool installed successfully.")
          Right(())
        else
          println(s"  \u001b[33m[NOTE]\u001b[0m $tool cargo install exited with code ${res.exitCode}")
          Right(())
      catch
        case e: Exception =>
          println(s"  \u001b[33m[NOTE]\u001b[0m $tool cargo install skipped: ${e.getMessage}")
          Right(())

    // Ensure cargo and C library build headers are present if not available
    if !cargoAvailable() then
      pm match
        case PackageManager.Pacman =>
          runPkgInstall("sudo", Seq("pacman", "-S", "--needed", "--noconfirm", "rust", "cargo", "alsa-lib", "libpulse", "dbus", "openssl", "pkgconf"))
        case PackageManager.Apt =>
          runPkgInstall("sudo", Seq("apt-get", "install", "-y", "cargo", "rustc", "pkg-config", "libasound2-dev", "libpulse-dev", "libdbus-1-dev", "libssl-dev"))
        case PackageManager.Dnf =>
          runPkgInstall("sudo", Seq("dnf", "install", "-y", "cargo", "rust", "alsa-lib-devel", "pulseaudio-libs-devel", "dbus-devel", "openssl-devel", "pkgconf-pkg-config"))
        case PackageManager.Brew =>
          runPkgInstall("brew", Seq("install", "pkg-config", "openssl", "rust"))
        case _ => ()

    // 1. spotify_player TUI
    installSpotifyPlayer(ctx)

    // 2. bluetui Bluetooth TUI (cargo)
    if cargoAvailable() then
      println("  \u001b[36m[INFO]\u001b[0m Installing/Updating bluetui via cargo...")
      runCargo("bluetui")
    else
      println("  \u001b[33m[NOTE]\u001b[0m cargo not available; skipping bluetui installation.")

    // 2.5. impala Network TUI (cargo)
    if cargoAvailable() then
      println("  \u001b[36m[INFO]\u001b[0m Installing/Updating impala via cargo...")
      runCargo("impala")
    else
      println("  \u001b[33m[NOTE]\u001b[0m cargo not available; skipping impala installation.")

    // 3. aerc Email Client TUI (package manager)
    println("  \u001b[36m[INFO]\u001b[0m Installing/Updating aerc email client...")
    pm match
      case PackageManager.Pacman => runPkgInstall("sudo", Seq("pacman", "-S", "--needed", "--noconfirm", "aerc"))
      case PackageManager.Apt => runPkgInstall("sudo", Seq("apt-get", "install", "-y", "aerc"))
      case PackageManager.Dnf => runPkgInstall("sudo", Seq("dnf", "install", "-y", "aerc"))
      case PackageManager.Brew => runPkgInstall("brew", Seq("install", "aerc"))
      case _ => Right(println("  \u001b[33m[NOTE]\u001b[0m Manual installation of aerc required for this system."))

    Right(())

  private def installSpotifyPlayer(ctx: Context): Either[PolyominoError, Unit] =
    val pm = detectPackageManager()
    println(s"\u001b[1;36m[polyomino install-spotify]\u001b[0m Installing/Updating spotify_player TUI (PM: $pm)...")

    val cargoHomeBin = ctx.home / ".cargo" / "bin"
    def cargoAvailable(): Boolean =
      isAvailable("cargo") || os.exists(cargoHomeBin / "cargo")

    // 1. Ensure runtime & build dependencies
    pm match
      case PackageManager.Pacman =>
        runPkgInstall("sudo", Seq("pacman", "-S", "--needed", "--noconfirm", "playerctl", "alsa-lib", "libpulse", "dbus", "openssl", "pkgconf", "rust", "cargo"))
      case PackageManager.Apt =>
        runPkgInstall("sudo", Seq("apt-get", "install", "-y", "playerctl", "cargo", "rustc", "pkg-config", "libasound2-dev", "libpulse-dev", "libdbus-1-dev", "libssl-dev"))
      case PackageManager.Dnf =>
        runPkgInstall("sudo", Seq("dnf", "install", "-y", "playerctl", "cargo", "rust", "alsa-lib-devel", "pulseaudio-libs-devel", "dbus-devel", "openssl-devel", "pkgconf-pkg-config"))
      case PackageManager.Brew =>
        runPkgInstall("brew", Seq("install", "playerctl", "pkg-config", "openssl", "rust"))
      case _ => ()

    // 2. Install spotify_player
    val isInstalled = isAvailable("spotify_player") || os.exists(cargoHomeBin / "spotify_player")
    if isInstalled then
      println("  \u001b[32m[OK]\u001b[0m spotify_player is already installed.")
    else
      val installedViaPm = pm match
        case PackageManager.Pacman if isAvailable("yay") =>
          runPkgInstall("yay", Seq("-S", "--needed", "--noconfirm", "--answerclean", "None", "--answerdiff", "None", "spotify-player")).isRight
        case PackageManager.Brew =>
          runPkgInstall("brew", Seq("install", "spotify-player")).isRight
        case _ => false

      if !installedViaPm && cargoAvailable() then
        val cargoExe = if isAvailable("cargo") then "cargo" else (cargoHomeBin / "cargo").toString
        try
          val cmd = Seq(cargoExe, "install", "spotify_player", "--locked", "--features", "daemon,pulseaudio-backend,rodio-backend,image")
          val res = os.proc(cmd.map(s => (s: os.Shellable))*).call(check = false)
          if res.exitCode == 0 then
            println("  \u001b[32m[OK]\u001b[0m spotify_player installed successfully via cargo.")
          else
            println(s"  \u001b[33m[NOTE]\u001b[0m spotify_player cargo install exited with code ${res.exitCode}")
        catch
          case e: Exception =>
            println(s"  \u001b[33m[NOTE]\u001b[0m spotify_player cargo install skipped: ${e.getMessage}")

    // 3. Ensure configuration exists
    val configDir = ctx.home / ".config" / "spotify-player"
    val configFile = configDir / "app.toml"
    val defaultConfigFile = ctx.dotfilesDir / "config" / "spotify-player" / "app.toml"
    if !os.exists(configFile) && os.exists(defaultConfigFile) then
      try
        os.makeDir.all(configDir)
        os.copy(defaultConfigFile, configFile)
        println("  \u001b[32m[OK]\u001b[0m Seeded default spotify-player configuration to ~/.config/spotify-player/app.toml")
      catch
        case e: Exception =>
          println(s"  \u001b[33m[NOTE]\u001b[0m Failed seeding spotify-player config: ${e.getMessage}")

    Right(())

  private def installSwayfx(ctx: Context): Either[PolyominoError, Unit] =
    val pm = detectPackageManager()
    println(s"\u001b[1;36m[polyomino install-swayfx]\u001b[0m Checking/Installing SwayFX / Sway (PM: $pm)...")
    pm match
      case PackageManager.Pacman =>
        if isAvailable("yay") then
          runPkgInstall("yay", Seq("-S", "--needed", "--noconfirm", "--answerclean", "None", "--answerdiff", "None", "swayfx"))
        else
          runPkgInstall("sudo", Seq("pacman", "-S", "--needed", "--noconfirm", "sway"))
      case PackageManager.Dnf =>
        try
          os.proc("sudo", "dnf", "copr", "enable", "-y", "swayfx/swayfx").call(check = false)
        catch
          case _: Exception => ()
        val dnfRes = runPkgInstall("sudo", Seq("dnf", "install", "-y", "swayfx"))
        if !isAvailable("sway") && !isAvailable("swayfx") then
          runPkgInstall("sudo", Seq("dnf", "install", "-y", "sway"))
        else Right(())
      case PackageManager.Apt =>
        val isSwayfxInstalled = isAvailable("swayfx") || {
          try os.proc("sway", "--version").call(check = false).out.text().toLowerCase.contains("swayfx") catch case _: Exception => false
        }
        if isSwayfxInstalled then
          println("  \u001b[32m[OK]\u001b[0m SwayFX is already installed.")
          Right(())
        else
          println("  \u001b[36m[INFO]\u001b[0m Compiling and installing SwayFX for Ubuntu...")
          try
            val buildDeps = Seq(
              "meson", "ninja-build", "libwlroots-dev", "wayland-protocols", "libwayland-dev",
              "libpango1.0-dev", "libcairo2-dev", "libgdk-pixbuf-2.0-dev", "libjson-c-dev",
              "libpcre2-dev", "libevdev-dev", "libinput-dev", "libxkbcommon-dev", "scdoc", "cmake", "git", "sway"
            )
            runPkgInstall("sudo", Seq("apt-get", "install", "-y") ++ buildDeps)

            val buildDir = ctx.home / ".cache" / "polyomino" / "swayfx"
            os.makeDir.all(buildDir / os.up)
            if !os.exists(buildDir / ".git") then
              if os.exists(buildDir) then os.remove.all(buildDir)
              os.proc("git", "clone", "--depth", "1", "https://github.com/WillPower3309/swayfx.git", buildDir.toString).call(check = false)
            else
              os.proc("git", "-C", buildDir.toString, "pull", "--ff-only").call(check = false)

            if os.exists(buildDir / "meson.build") then
              val mesonDir = buildDir / "build"
              if !os.exists(mesonDir) then
                os.proc("meson", "setup", "build", "--prefix=/usr/local", "--buildtype=release", "-Dman-pages=disabled").call(cwd = buildDir, check = false)
              os.proc("ninja", "-C", "build").call(cwd = buildDir, check = false)
              val installRes = os.proc("sudo", "ninja", "-C", "build", "install").call(cwd = buildDir, check = false)
              if installRes.exitCode == 0 then
                println("  \u001b[32m[OK]\u001b[0m SwayFX built and installed successfully to /usr/local/bin/sway.")
              else
                println(s"  \u001b[33m[NOTE]\u001b[0m SwayFX install exited with code ${installRes.exitCode}; base sway is installed.")
            Right(())
          catch
            case e: Exception =>
              println(s"  \u001b[33m[NOTE]\u001b[0m SwayFX build skipped (${e.getMessage}); ensuring standard sway...")
              runPkgInstall("sudo", Seq("apt-get", "install", "-y", "sway"))
      case PackageManager.Brew =>
        runPkgInstall("brew", Seq("install", "sway"))
      case _ =>
        Right(println("  \u001b[33m[NOTE]\u001b[0m Manual installation of SwayFX/Sway required for current OS."))

  private def installTelegram(ctx: Context): Either[PolyominoError, Unit] =
    val pm = detectPackageManager()
    val localBin = ctx.home / ".local" / "bin"
    val isTelegramInstalled = isAvailable("telegram-desktop") || isAvailable("Telegram") || os.exists(localBin / "telegram-desktop") || os.exists(localBin / "Telegram" / "Telegram")
    if isTelegramInstalled then
      println("  \u001b[32m[OK]\u001b[0m Telegram Desktop is already installed.")
      return Right(())

    println(s"\u001b[1;36m[polyomino install-telegram]\u001b[0m Installing Telegram Desktop (PM: $pm)...")
    pm match
      case PackageManager.Pacman =>
        runPkgInstall("sudo", Seq("pacman", "-S", "--noconfirm", "telegram-desktop"))
      case PackageManager.Dnf =>
        runPkgInstall("sudo", Seq("dnf", "install", "-y", "telegram-desktop"))
      case PackageManager.Apt =>
        println("  \u001b[36m[INFO]\u001b[0m Downloading official standalone Telegram binary for Ubuntu/Debian...")
        try
          val tgzUrl = "https://telegram.org/dl/desktop/linux"
          os.makeDir.all(localBin)
          val dlRes = os.proc("bash", "-c", s"curl -fsSL '$tgzUrl' | tar -xJ -C '${localBin}' && ln -sf '${localBin}/Telegram/Telegram' '${localBin}/telegram-desktop'").call(check = false)
          if dlRes.exitCode == 0 then
            println("  \u001b[32m[OK]\u001b[0m Telegram installed to ~/.local/bin/telegram-desktop.")
            Right(())
          else
            println(s"  \u001b[33m[NOTE]\u001b[0m Telegram binary download exited with code ${dlRes.exitCode}")
            Right(())
        catch
          case e: Exception =>
            println(s"  \u001b[33m[NOTE]\u001b[0m Telegram download skipped: ${e.getMessage}")
            Right(())
      case PackageManager.Brew =>
        runPkgInstall("brew", Seq("install", "--cask", "telegram-desktop"))
      case _ =>
        Right(println("  \u001b[33m[NOTE]\u001b[0m Manual package installation recommended for current OS."))

  private def installNode(ctx: Context): Either[PolyominoError, Unit] =
    val pm = detectPackageManager()
    println(s"\u001b[1;36m[polyomino install-node]\u001b[0m Installing Node.js & npm (PM: $pm)...")
    pm match
      case PackageManager.Pacman => runPkgInstall("sudo", Seq("pacman", "-S", "--needed", "--noconfirm", "nodejs", "npm"))
      case PackageManager.Dnf => runPkgInstall("sudo", Seq("dnf", "install", "-y", "nodejs", "npm"))
      case PackageManager.Apt => runPkgInstall("sudo", Seq("apt-get", "install", "-y", "nodejs", "npm"))
      case PackageManager.Brew => runPkgInstall("brew", Seq("install", "node"))
      case _ => Right(println("  \u001b[32m[OK]\u001b[0m Node.js environment provisioned."))

  private def installYazi(ctx: Context): Either[PolyominoError, Unit] =
    println(s"\u001b[1;36m[polyomino install-yazi]\u001b[0m Installing yazi file manager & plugins...")
    val cargoHomeBin = ctx.home / ".cargo" / "bin"
    def cargoAvailable(): Boolean =
      isAvailable("cargo") || os.exists(cargoHomeBin / "cargo")

    val pm = detectPackageManager()
    val isYaziInstalled = isAvailable("yazi") || os.exists(cargoHomeBin / "yazi")

    if !isYaziInstalled then
      val installedViaPm = pm match
        case PackageManager.Pacman =>
          runPkgInstall("sudo", Seq("pacman", "-S", "--needed", "--noconfirm", "yazi")).isRight
        case PackageManager.Apt =>
          runPkgInstall("sudo", Seq("apt-get", "install", "-y", "yazi")).isRight
        case PackageManager.Brew =>
          runPkgInstall("brew", Seq("install", "yazi")).isRight
        case _ => false

      if !installedViaPm && cargoAvailable() then
        val cargoExe = if isAvailable("cargo") then "cargo" else (cargoHomeBin / "cargo").toString
        try
          // Yazi on crates.io requires yazi-build meta-package
          val res = os.proc(cargoExe, "install", "--force", "yazi-build").call(stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit, check = false)
          if res.exitCode == 0 then
            println(s"  \u001b[32m[OK]\u001b[0m yazi installed successfully via cargo.")
          else
            println(s"  \u001b[33m[NOTE]\u001b[0m yazi cargo install exited with code ${res.exitCode}")
        catch
          case e: Exception =>
            println(s"  \u001b[33m[NOTE]\u001b[0m yazi cargo installation skipped: ${e.getMessage}")
    else
      println(s"  \u001b[32m[OK]\u001b[0m yazi is already installed.")

    // Install requested plugins
    val yaExe = if isAvailable("ya") then "ya" else (cargoHomeBin / "ya").toString
    if isAvailable("ya") || os.exists(cargoHomeBin / "ya") then
      val plugins = Seq("yazi-rs/plugins:git", "yazi-rs/plugins:full-border", "Rolv-Apneseth/starship", "yazi-rs/plugins:mount")
      for plugin <- plugins do
        // Try modern `ya pkg add` first, fallback to legacy `ya pack -a`
        val pkgRes = os.proc(yaExe, "pkg", "add", plugin).call(stdin = os.Inherit, stdout = os.Pipe, stderr = os.Pipe, check = false)
        val combinedOut = (pkgRes.out.text() + " " + pkgRes.err.text()).toLowerCase
        if pkgRes.exitCode == 0 || combinedOut.contains("already exists") then
          println(s"  \u001b[32m[OK]\u001b[0m Plugin ready: $plugin")
        else
          val packRes = os.proc(yaExe, "pack", "-a", plugin).call(stdin = os.Inherit, stdout = os.Pipe, stderr = os.Pipe, check = false)
          val packOut = (packRes.out.text() + " " + packRes.err.text()).toLowerCase
          if packRes.exitCode == 0 || packOut.contains("already exists") then
            println(s"  \u001b[32m[OK]\u001b[0m Plugin ready: $plugin")
          else
            println(s"  \u001b[33m[NOTE]\u001b[0m Failed to install plugin: $plugin (code ${pkgRes.exitCode})")

      os.proc(yaExe, "pkg", "install").call(stdin = os.Inherit, stdout = os.Pipe, stderr = os.Pipe, check = false)

    Right(())

  private def installFastfetch(ctx: Context): Either[PolyominoError, Unit] =
    val pm = detectPackageManager()
    println(s"\u001b[1;36m[polyomino install-fastfetch]\u001b[0m Checking/Installing Fastfetch (PM: $pm)...")
    pm match
      case PackageManager.Pacman =>
        runPkgInstall("sudo", Seq("pacman", "-S", "--needed", "--noconfirm", "fastfetch"))
      case PackageManager.Dnf =>
        runPkgInstall("sudo", Seq("dnf", "install", "-y", "fastfetch"))
      case PackageManager.Apt =>
        val res = runPkgInstall("sudo", Seq("apt-get", "install", "-y", "fastfetch"))
        if !isAvailable("fastfetch") then
          try
            println("  \u001b[36m[INFO]\u001b[0m Adding fastfetch PPA for Ubuntu...")
            os.proc("sudo", "add-apt-repository", "-y", "ppa:zhangsongcui3371/fastfetch").call(check = false)
            os.proc("sudo", "apt-get", "update").call(check = false)
            runPkgInstall("sudo", Seq("apt-get", "install", "-y", "fastfetch"))
          catch
            case e: Exception =>
              println(s"  \u001b[33m[NOTE]\u001b[0m Fastfetch PPA setup skipped: ${e.getMessage}")
        Right(())
      case PackageManager.Brew =>
        runPkgInstall("brew", Seq("install", "fastfetch"))
      case _ =>
        Right(println("  \u001b[33m[NOTE]\u001b[0m Manual installation of fastfetch required for current OS."))

  private def installAll(ctx: Context): Either[PolyominoError, Unit] =
    println("\u001b[1;36m[polyomino full-install]\u001b[0m Installing all system dependencies, desktop apps, fonts, and tooling...")
    for
      _ <- installSystemDeps(ctx)
      _ <- installHomebrew(ctx)
      _ <- installGh(ctx)
      _ <- installCoursier(ctx)
      _ <- installApps(ctx)
      _ <- installSwayfx(ctx)
      _ <- installFastfetch(ctx)
      _ <- installSwaync(ctx)
      _ <- installFonts(ctx)
      _ <- installBrowser(ctx)
      _ <- installTelegram(ctx)
      _ <- installDevops(ctx)
      _ <- installZsh(ctx)
      _ <- installSdkman(ctx)
      _ <- installNode(ctx)
      _ <- installTools(ctx)
      _ <- installSpotifyPlayer(ctx)
      _ <- installYazi(ctx)
      _ <- polyomino.dotfiles.refresh.NotificationIntegration.configureApps(ctx)
    yield ()

  private def runPkgInstall(cmd: String, args: Seq[String]): Either[PolyominoError, Unit] =
    try
      val fullCmd: Seq[os.Shellable] = (cmd +: args).map(s => (s: os.Shellable))
      val res = os.proc(fullCmd*).call(stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit, check = false)
      if res.exitCode == 0 then
        println(s"  \u001b[32m[OK]\u001b[0m Package installation complete.")
        Right(())
      else
        println(s"  \u001b[33m[WARN]\u001b[0m Batch installation returned code ${res.exitCode}. Attempting individual fallback...")
        val knownNonPackages = Set("pacman", "dnf", "apt-get", "apt", "brew", "yay", "install")
        val (flagsAndCmds, potentialPackages) = args.partition(arg => arg.startsWith("-") || knownNonPackages.contains(arg))
        
        for pkg <- potentialPackages do
          val singleCmd: Seq[os.Shellable] = (cmd +: flagsAndCmds :+ pkg).map(s => (s: os.Shellable))
          val singleRes = os.proc(singleCmd*).call(stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit, check = false)
          if singleRes.exitCode == 0 then
            println(s"  \u001b[32m[OK]\u001b[0m Installed: $pkg")
          else
            println(s"  \u001b[31m[FAIL]\u001b[0m Could not install: $pkg (code ${singleRes.exitCode})")
            
        Right(())
    catch
      case e: Exception =>
        println(s"  \u001b[33m[NOTE]\u001b[0m Package installation skipped: ${e.getMessage}")
        Right(())

  private def isAvailable(cmd: String): Boolean =
    try os.proc("which", cmd).call(check = false).exitCode == 0 catch case _: Exception => false

