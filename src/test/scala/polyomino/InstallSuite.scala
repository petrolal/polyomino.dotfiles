package polyomino

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.install.{DeployInstaller, ToolInstallers, Manifest, ManifestEntry}
import munit.FunSuite

class InstallSuite extends FunSuite:
  private val isCI = sys.env.contains("CI") || sys.env.contains("GITHUB_ACTIONS")

  test("DeployInstaller.run executes symlink deployment and writes manifest"):
    val ctx = Context.discover().toOption.get
    val res = DeployInstaller.run(ctx, List("--links-only"))
    assert(res.isRight)
    assert(os.exists(ctx.shareDir / "manifest.json"))

  test("DeployInstaller creates ~/.local/bin directory"):
    val ctx = Context.discover().toOption.get
    DeployInstaller.run(ctx, List("--links-only"))
    assert(os.exists(ctx.home / ".local" / "bin"))

  test("DeployInstaller creates symlinks for zsh config"):
    assume(!isCI)
    val ctx = Context.discover().toOption.get
    DeployInstaller.run(ctx, List("--links-only"))
    val zshrcLink = ctx.home / ".zshrc"
    assert(os.exists(zshrcLink) || os.isLink(zshrcLink))

  test("DeployInstaller creates symlinks for swaync config"):
    assume(!isCI)
    val ctx = Context.discover().toOption.get
    DeployInstaller.run(ctx, List("--links-only"))
    val swayncLink = ctx.configDir / "swaync"
    assert(os.exists(swayncLink) || os.isLink(swayncLink))

  test("DeployInstaller creates symlinks for fastfetch config"):
    assume(!isCI)
    val ctx = Context.discover().toOption.get
    DeployInstaller.run(ctx, List("--links-only"))
    val fastfetchLink = ctx.configDir / "fastfetch"
    assert(os.exists(fastfetchLink) || os.isLink(fastfetchLink))

  test("DeployInstaller writes valid manifest JSON"):
    assume(!isCI)
    val ctx = Context.discover().toOption.get
    DeployInstaller.run(ctx, List("--links-only"))
    val manifestFile = ctx.shareDir / "manifest.json"
    if os.exists(manifestFile) then
      val json = os.read(manifestFile)
      assert(json.contains("\"sourcePath\""))

  test("ManifestEntry serializes to JSON"):
    val entry = ManifestEntry(
      sourcePath = "/path/to/source",
      targetPath = "/path/to/target",
      backupPath = Some("/path/to/backup")
    )
    val json = upickle.default.write(entry)
    assert(json.contains("sourcePath"))

  test("ToolInstallers.detectKnownSubcommands contains expected tasks"):
    val tasks = Seq(
      "install-deps", "install-brew", "install-gh", "install-coursier",
      "install-fonts", "install-apps", "install-sway", "install-swayfx", "install-swaync",
      "install-browser", "install-devops", "install-telegram", "install-zsh",
      "install-sdkman", "install-node", "install-npm", "install-npx", "install-tools",
      "install-spotify", "install-spotify-player", "install-yazi", "install-fastfetch", "full-install"
    )
    tasks.foreach { task =>
      assert(DeployInstaller.Subcommands.contains(task), s"Missing task: $task")
    }

  test("ToolInstallers.runTool rejects invalid tools"):
    val ctx = Context.discover().toOption.get
    val res = ToolInstallers.runTool("install-invalid-tool-xyz", ctx, Nil)
    assert(res.isLeft)

  test("ToolInstallers.detectPackageManager returns valid PM"):
    val pm = ToolInstallers.detectPackageManager()
    assert(pm.toString.nonEmpty)

  test("DeployInstaller.ensureDotfilesRepo returns Right on valid checkout"):
    val ctx = Context.discover().toOption.get
    val res = DeployInstaller.ensureDotfilesRepo(ctx)
    assert(res.isRight)

  test("Manifest serialization roundtrip"):
    val manifest = Manifest(
      version = "0.1.0",
      timestamp = 123456789L,
      entries = List(
        ManifestEntry(
          sourcePath = "/source/test",
          targetPath = "/target/test",
          backupPath = Some("/backup/test")
        )
      )
    )
    val json = upickle.default.write(manifest)
    val readBack = upickle.default.read[Manifest](json)
    assertEquals(readBack.version, "0.1.0")
    assertEquals(readBack.timestamp, 123456789L)
    assertEquals(readBack.entries.length, 1)
    assertEquals(readBack.entries.head.sourcePath, "/source/test")

  test("DeployInstaller.Subcommands contains power menu and lock aliases"):
    val aliases = Seq("rubik-lock", "preview-lock", "power-menu", "powermenu", "whichkey", "menu")
    aliases.foreach { alias =>
      assert(DeployInstaller.Subcommands.contains(alias), s"Missing subcommand alias: $alias")
    }
