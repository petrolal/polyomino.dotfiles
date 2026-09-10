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
