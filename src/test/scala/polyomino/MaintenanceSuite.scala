package polyomino

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.maintenance.Maintenance
import munit.FunSuite

class MaintenanceSuite extends FunSuite:
  private def withIsolatedContext[T](f: Context => T): T =
    val tempDir = os.temp.dir(prefix = "polyomino-maintenance-test-")
    try
      val ctx = Context.isolated(tempDir, dotfilesDir = os.pwd)
      // Populate mock sway configuration so backup can succeed
      os.makeDir.all(ctx.configDir / "sway")
      os.write(ctx.configDir / "sway" / "config", "# test sway config\nset $mod Mod4\n")
      f(ctx)
    finally
      os.remove.all(tempDir)

  test("Maintenance.runBackup creates tarball snapshot"):
    withIsolatedContext { ctx =>
      val res = Maintenance.runBackup(ctx, Nil)
      assert(res.isRight, s"Backup failed: $res")
      val backupDir = ctx.shareDir / "backups"
      assert(os.exists(backupDir))
      val archives = os.list(backupDir).filter(_.last.endsWith(".tar.gz"))
      assertEquals(archives.length, 1)
      assert(os.size(archives.head) > 0)
    }

  test("Maintenance.runBackup with custom output path"):
    withIsolatedContext { ctx =>
      val customPath = ctx.home / "custom-backup.tar.gz"
      val res = Maintenance.runBackup(ctx, List(customPath.toString))
      assert(res.isRight, s"Backup with custom path failed: $res")
      assert(os.exists(customPath))
      assert(os.size(customPath) > 0)
    }

  test("Maintenance.runBackup packages multiple managed configs"):
    withIsolatedContext { ctx =>
      os.makeDir.all(ctx.configDir / "kitty")
      os.write(ctx.configDir / "kitty" / "kitty.conf", "font_size 12.0\n")
      os.makeDir.all(ctx.configDir / "waybar")
      os.write(ctx.configDir / "waybar" / "config.jsonc", "{\"layer\": \"top\"}\n")

      val customPath = ctx.home / "multi-backup.tar.gz"
      val res = Maintenance.runBackup(ctx, List(customPath.toString))
      assert(res.isRight, s"Multi-config backup failed: $res")
      assert(os.exists(customPath))

      // Check contents of tar archive
      val listRes = os.proc("tar", "-tf", customPath.toString).call()
      val contents = listRes.out.text()
      assert(contents.contains("sway/config"), "Archive should contain sway/config")
      assert(contents.contains("kitty/kitty.conf"), "Archive should contain kitty/kitty.conf")
      assert(contents.contains("waybar/config.jsonc"), "Archive should contain waybar/config.jsonc")
    }

  test("Maintenance.runBackup fails when sway config directory is missing"):
    val emptyDir = os.temp.dir(prefix = "polyomino-empty-test-")
    try
      val ctx = Context.isolated(emptyDir, dotfilesDir = os.pwd)
      val res = Maintenance.runBackup(ctx, Nil)
      assert(res.isLeft)
    finally
      os.remove.all(emptyDir)

  test("Maintenance.runRestore restores configuration snapshot"):
    withIsolatedContext { ctx =>
      val customPath = ctx.home / "snapshot.tar.gz"
      val backupRes = Maintenance.runBackup(ctx, List(customPath.toString))
      assert(backupRes.isRight)

      // Remove the config file before restoring
      os.remove.all(ctx.configDir / "sway")
      assert(!os.exists(ctx.configDir / "sway" / "config"))

      val restoreRes = Maintenance.runRestore(ctx, List(customPath.toString))
      assert(restoreRes.isRight, s"Restore failed: $restoreRes")
      assert(os.exists(ctx.configDir / "sway" / "config"))
      assertEquals(os.read(ctx.configDir / "sway" / "config"), "# test sway config\nset $mod Mod4\n")
    }

  test("Maintenance.runRestore handles missing file gracefully"):
    withIsolatedContext { ctx =>
      val res = Maintenance.runRestore(ctx, List((ctx.home / "non-existent-archive.tar.gz").toString))
      assert(res.isLeft)
    }

  test("Maintenance.runRestore rejects invocation with no arguments"):
    withIsolatedContext { ctx =>
      val res = Maintenance.runRestore(ctx, Nil)
      assert(res.isLeft)
    }

  test("Maintenance.runUpdate rejects non-git checkout"):
    withIsolatedContext { ctx =>
      val fakeRepoDir = ctx.home / "fake-dotfiles"
      os.makeDir.all(fakeRepoDir / "config")
      os.makeDir.all(fakeRepoDir / "zsh")
      val nonGitCtx = ctx.copy(dotfilesDir = fakeRepoDir)
      val res = Maintenance.runUpdate(nonGitCtx, Nil)
      assert(res.isLeft)
    }

  test("Maintenance.calculateNextVersion calculates semantic versions correctly"):
    assertEquals(Maintenance.calculateNextVersion("1.2.3", "--patch"), Some("1.2.4"))
    assertEquals(Maintenance.calculateNextVersion("1.2.3", "--minor"), Some("1.3.0"))
    assertEquals(Maintenance.calculateNextVersion("1.2.3", "--major"), Some("2.0.0"))
    assertEquals(Maintenance.calculateNextVersion("1.2.3", "4.0.0"), Some("4.0.0"))
    assertEquals(Maintenance.calculateNextVersion("1.2.3-SNAPSHOT", "--patch"), Some("1.2.4"))
    assertEquals(Maintenance.calculateNextVersion("invalid", "--patch"), None)
    assertEquals(Maintenance.calculateNextVersion("1.2.3", "not-a-version"), None)

  test("Maintenance.runRelease dry run updates nothing and succeeds"):
    withIsolatedContext { ctx =>
      val repoDir = ctx.home / "dotfiles-repo"
      os.makeDir.all(repoDir / "config")
      os.makeDir.all(repoDir / "zsh")
      os.proc("git", "init").call(cwd = repoDir)
      os.write(repoDir / "PKGBUILD", "pkgver=1.0.0\npkgrel=1\n")
      os.write(repoDir / ".SRCINFO", "\tpkgver = 1.0.0\n\tpkgrel = 1\n")

      val gitCtx = ctx.copy(dotfilesDir = repoDir)
      val res = Maintenance.runRelease(gitCtx, List("--minor", "--dry-run"))
      assert(res.isRight, s"Dry run release failed: $res")

      // Verify files remained unchanged
      assert(os.read(repoDir / "PKGBUILD").contains("pkgver=1.0.0"))
    }

  test("Maintenance.runRelease updates PKGBUILD, .SRCINFO and creates git tag"):
    withIsolatedContext { ctx =>
      val repoDir = ctx.home / "dotfiles-repo"
      os.makeDir.all(repoDir / "config")
      os.makeDir.all(repoDir / "zsh")
      os.proc("git", "init").call(cwd = repoDir)
      os.proc("git", "config", "user.name", "Test User").call(cwd = repoDir)
      os.proc("git", "config", "user.email", "test@example.com").call(cwd = repoDir)
      os.write(repoDir / "PKGBUILD", "pkgver=1.0.0\npkgrel=1\n")
      os.write(repoDir / ".SRCINFO", "\tpkgver = 1.0.0\n\tpkgrel = 1\n")
      os.write(repoDir / "build.sbt", "version := \"1.0.0\"\n")
      os.proc("git", "add", ".").call(cwd = repoDir)
      os.proc("git", "commit", "-m", "initial").call(cwd = repoDir)
      os.proc("git", "tag", "v1.0.0").call(cwd = repoDir)

      val gitCtx = ctx.copy(dotfilesDir = repoDir)
      val res = Maintenance.runRelease(gitCtx, List("--patch"))
      assert(res.isRight, s"Release failed: $res")

      // Verify files updated
      assert(os.read(repoDir / "PKGBUILD").contains("pkgver=1.0.1"))
      assert(os.read(repoDir / ".SRCINFO").contains("pkgver = 1.0.1"))
      assert(os.read(repoDir / "build.sbt").contains("version := \"1.0.1\""))

      // Verify git tag created
      val tagRes = os.proc("git", "tag", "-l", "v1.0.1").call(cwd = repoDir)
      assertEquals(tagRes.out.text().trim, "v1.0.1")
    }
