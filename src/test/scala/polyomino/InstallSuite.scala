package polyomino

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.install.{DeployInstaller, ToolInstallers, Manifest, ManifestEntry}
import munit.FunSuite

class InstallSuite extends FunSuite:
  private def withIsolatedContext[T](f: Context => T): T =
    val tempDir = os.temp.dir(prefix = "polyomino-install-test-")
    try
      val ctx = Context.isolated(tempDir, dotfilesDir = os.pwd)
      f(ctx)
    finally
      os.remove.all(tempDir)

  test("DeployInstaller.run executes symlink deployment and writes manifest"):
    withIsolatedContext { ctx =>
      val res = DeployInstaller.run(ctx, List("--links-only"))
      assert(res.isRight)
      assert(os.exists(ctx.shareDir / "manifest.json"))
    }

  test("DeployInstaller creates ~/.local/bin directory"):
    withIsolatedContext { ctx =>
      DeployInstaller.run(ctx, List("--links-only"))
      assert(os.exists(ctx.home / ".local" / "bin"))
    }

  test("DeployInstaller creates symlinks for zsh config"):
    withIsolatedContext { ctx =>
      DeployInstaller.run(ctx, List("--links-only"))
      val zshrcLink = ctx.home / ".zshrc"
      assert(os.exists(zshrcLink) || os.isLink(zshrcLink))
    }

  test("DeployInstaller creates symlinks for swaync config"):
    withIsolatedContext { ctx =>
      DeployInstaller.run(ctx, List("--links-only"))
      val swayncLink = ctx.configDir / "swaync"
      assert(os.exists(swayncLink) || os.isLink(swayncLink))
    }

  test("DeployInstaller creates symlinks for fastfetch config"):
    withIsolatedContext { ctx =>
      DeployInstaller.run(ctx, List("--links-only"))
      val fastfetchLink = ctx.configDir / "fastfetch"
      assert(os.exists(fastfetchLink) || os.isLink(fastfetchLink))
    }

  test("DeployInstaller writes valid manifest JSON"):
    withIsolatedContext { ctx =>
      DeployInstaller.run(ctx, List("--links-only"))
      val manifestFile = ctx.shareDir / "manifest.json"
      assert(os.exists(manifestFile))
      val json = os.read(manifestFile)
      assert(json.contains("\"sourcePath\""))
    }

  test("DeployInstaller.uninstall removes symlinks and deletes manifest"):
    withIsolatedContext { ctx =>
      val installRes = DeployInstaller.run(ctx, List("--links-only"))
      assert(installRes.isRight)
      assert(os.exists(ctx.shareDir / "manifest.json"))
      assert(os.exists(ctx.configDir / "sway"))

      val uninstallRes = DeployInstaller.uninstall(ctx)
      assert(uninstallRes.isRight)
      assert(!os.exists(ctx.shareDir / "manifest.json"))
      assert(!os.exists(ctx.configDir / "sway"))
    }

  test("DeployInstaller.uninstall restores pre-existing configuration backup"):
    withIsolatedContext { ctx =>
      val zshrc = ctx.home / ".zshrc"
      os.write(zshrc, "# pre-existing user zshrc\n")

      val installRes = DeployInstaller.run(ctx, List("--links-only"))
      assert(installRes.isRight)
      assert(os.isLink(zshrc))

      val uninstallRes = DeployInstaller.uninstall(ctx)
      assert(uninstallRes.isRight)
      assert(os.exists(zshrc))
      assert(!os.isLink(zshrc))
      assertEquals(os.read(zshrc), "# pre-existing user zshrc\n")
    }

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
