package polyomino.dotfiles.maintenance

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}

object Maintenance:
  val ManagedConfigs: Seq[String] = Seq(
    "sway", "kitty", "waybar", "wofi", "rofi", "swaync", "mako", "fastfetch", "spotify-player"
  )

  def runBackup(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    val archivePath = args.headOption match
      case Some(custom) =>
        val p = os.Path(custom, os.pwd)
        os.makeDir.all(p / os.up)
        p
      case None =>
        val backupDir = ctx.shareDir / "backups"
        os.makeDir.all(backupDir)
        val timestamp = System.currentTimeMillis()
        backupDir / s"polyomino-backup-$timestamp.tar.gz"

    println(s"\u001b[1;36m[polyomino backup]\u001b[0m Creating configuration snapshot at $archivePath...")
    try
      val existing = ManagedConfigs.filter(dir => os.exists(ctx.configDir / dir))
      if existing.nonEmpty then
        val tarArgs: Seq[os.Shellable] = (Seq("tar", "-czf", archivePath.toString, "-C", ctx.configDir.toString) ++ existing).map(s => (s: os.Shellable))
        val res = os.proc(tarArgs*).call(check = false)
        if res.exitCode == 0 && os.exists(archivePath) then
          println(s"  \u001b[32m[OK]\u001b[0m Backup snapshot saved successfully (${os.size(archivePath)} bytes)")
          Right(())
        else
          Left(CommandError(s"tar archive creation failed with exit code ${res.exitCode}", res.exitCode))
      else
        Left(CommandError(s"No managed configuration paths found to backup in ${ctx.configDir}", 1))
    catch
      case e: Exception => Left(CommandError(s"Backup failed: ${e.getMessage}"))

  def runRestore(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    args.headOption match
      case None =>
        Left(CommandError("Usage: polyomino restore <path-to-archive.tar.gz>", 1))
      case Some(archiveStr) =>
        val archivePath = os.Path(archiveStr, os.pwd)
        println(s"\u001b[1;36m[polyomino restore]\u001b[0m Restoring configuration snapshot from $archivePath...")
        if os.exists(archivePath) then
          try
            os.makeDir.all(ctx.configDir)
            val res = os.proc("tar", "-xzf", archivePath.toString, "-C", ctx.configDir.toString).call(check = false)
            if res.exitCode == 0 then
              println(s"  \u001b[32m[OK]\u001b[0m Configuration restored to ${ctx.configDir}")
              Right(())
            else
              Left(CommandError(s"tar extraction failed with exit code ${res.exitCode}", res.exitCode))
          catch
            case e: Exception => Left(CommandError(s"Restore failed: ${e.getMessage}"))
        else
          Left(CommandError(s"Backup archive missing at $archivePath", 1))

  def runUpdate(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    println(s"\u001b[1;36m[polyomino update]\u001b[0m Pulling latest dotfiles updates in ${ctx.dotfilesDir}...")
    polyomino.dotfiles.install.DeployInstaller.ensureDotfilesRepo(ctx) match
      case Left(err) => Left(err)
      case Right(_) =>
        try
          val res = os.proc("git", "pull", "--rebase").call(cwd = ctx.dotfilesDir, check = false)
          if res.exitCode == 0 then
            println("  \u001b[32m[OK]\u001b[0m Git pull complete. Triggering installer redeployment...")
            polyomino.dotfiles.install.DeployInstaller.run(ctx, args)
          else
            Left(CommandError(s"git pull failed with exit code ${res.exitCode}", res.exitCode))
        catch
          case e: Exception => Left(CommandError(s"Update failed: ${e.getMessage}"))

  def runRelease(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    val dryRun = args.contains("--dry-run") || args.contains("-n")
    val filteredArgs = args.filterNot(a => a == "--dry-run" || a == "-n")

    println(s"\u001b[1;34m╔════════════════════════════════════════════════════════════════╗\u001b[0m")
    println(s"\u001b[1;34m║  polyomino.dotfiles Release Helper                              ║\u001b[0m")
    println(s"\u001b[1;34m╚════════════════════════════════════════════════════════════════╝\u001b[0m\n")

    polyomino.dotfiles.install.DeployInstaller.ensureDotfilesRepo(ctx) match
      case Left(err) => Left(err)
      case Right(_) =>
        val currentVersion = getCurrentVersion(ctx.dotfilesDir)
        println(s"Current version: \u001b[1;32m$currentVersion\u001b[0m\n")

        val targetType = filteredArgs.headOption.getOrElse("--patch")
        calculateNextVersion(currentVersion, targetType) match
          case None =>
            Left(CommandError(s"Invalid version or release type: '$targetType'. Expected --patch, --minor, --major, or semantic version (e.g. 1.2.3)", 1))
          case Some(newVersion) =>
            println(s"New version will be: \u001b[1;32m$newVersion\u001b[0m${if dryRun then " (dry-run)" else ""}\n")

            if dryRun then
              println(s"  \u001b[36m[DRY-RUN]\u001b[0m Would update PKGBUILD & .SRCINFO to version $newVersion")
              println(s"  \u001b[36m[DRY-RUN]\u001b[0m Would commit with message: 'chore: bump version to $newVersion'")
              println(s"  \u001b[36m[DRY-RUN]\u001b[0m Would create git tag: 'v$newVersion'")
              Right(())
            else
              try
                var updatedFiles = List.empty[String]

                // 1. Update PKGBUILD
                val pkgbuild = ctx.dotfilesDir / "PKGBUILD"
                if os.exists(pkgbuild) then
                  val content = os.read(pkgbuild)
                  val updated = content
                    .replaceAll("(?m)^pkgver=.*$", s"pkgver=$newVersion")
                    .replaceAll("(?m)^pkgrel=.*$", "pkgrel=1")
                  os.write.over(pkgbuild, updated)
                  updatedFiles = "PKGBUILD" :: updatedFiles
                  println(s"  \u001b[32m[OK]\u001b[0m PKGBUILD updated")

                // 2. Update .SRCINFO
                val srcinfo = ctx.dotfilesDir / ".SRCINFO"
                if os.exists(srcinfo) then
                  val content = os.read(srcinfo)
                  val updated = content
                    .replaceAll("(?m)^\\s*pkgver = .*$", s"\tpkgver = $newVersion")
                    .replaceAll("(?m)^\\s*pkgrel = .*$", "\tpkgrel = 1")
                  os.write.over(srcinfo, updated)
                  updatedFiles = ".SRCINFO" :: updatedFiles
                  println(s"  \u001b[32m[OK]\u001b[0m .SRCINFO updated")

                // 3. Update build.sbt if static version string is present
                val buildSbt = ctx.dotfilesDir / "build.sbt"
                if os.exists(buildSbt) then
                  val content = os.read(buildSbt)
                  if content.contains("version := \"") then
                    val updated = content.replaceAll("(?m)^version := \".*\"$", s"""version := "$newVersion"""")
                    os.write.over(buildSbt, updated)
                    updatedFiles = "build.sbt" :: updatedFiles
                    println(s"  \u001b[32m[OK]\u001b[0m build.sbt updated")

                if updatedFiles.nonEmpty then
                  val addArgs: Seq[os.Shellable] = (Seq("git", "add") ++ updatedFiles).map(s => (s: os.Shellable))
                  os.proc(addArgs*).call(cwd = ctx.dotfilesDir, check = true)

                // 4. Create git commit & tag
                val commitRes = os.proc("git", "commit", "-m", s"chore: bump version to $newVersion").call(cwd = ctx.dotfilesDir, check = false)
                if commitRes.exitCode != 0 then
                  println(s"  \u001b[33m[WARN]\u001b[0m Git commit skipped: ${commitRes.err.text().trim}")

                val tagRes = os.proc("git", "tag", "-a", s"v$newVersion", "-m", s"Release version $newVersion").call(cwd = ctx.dotfilesDir, check = false)
                if tagRes.exitCode != 0 then
                  return Left(CommandError(s"git tag failed: ${tagRes.err.text().trim}", tagRes.exitCode))

                println(s"  \u001b[32m[OK]\u001b[0m Created commit and git tag v$newVersion")
                println(s"\n\u001b[1;32m✓ Release prepared!\u001b[0m\n")
                println("Next steps:")
                println("  1. Review changes: git log -1")
                println("  2. Push to GitHub:")
                println("     \u001b[1;33mgit push origin master\u001b[0m")
                println("     \u001b[1;33mgit push origin --tags\u001b[0m")
                println("  3. Watch CI/CD pipeline:")
                println("     https://github.com/petrolal/polyomino.dotfiles/actions\n")
                Right(())
              catch
                case e: Exception => Left(CommandError(s"Release preparation failed: ${e.getMessage}"))

  def getCurrentVersion(dotfilesDir: os.Path): String =
    try
      val res = os.proc("git", "describe", "--tags", "--abbrev=0").call(cwd = dotfilesDir, check = false)
      val tag = res.out.text().trim.stripPrefix("v")
      if res.exitCode == 0 && tag.nonEmpty && tag.matches("""^\d+\.\d+\.\d+.*$""") then
        tag
      else
        val pkgbuild = dotfilesDir / "PKGBUILD"
        if os.exists(pkgbuild) then
          os.read.lines(pkgbuild).find(_.startsWith("pkgver=")).map(_.stripPrefix("pkgver=")).getOrElse("0.1.0")
        else
          "0.1.0"
    catch
      case _: Exception => "0.1.0"

  def calculateNextVersion(current: String, bumpType: String): Option[String] =
    val cleanCurrent = current.trim.stripPrefix("v").split('-').head
    val parts = cleanCurrent.split('.').flatMap(_.toIntOption)
    if parts.length == 3 then
      val major = parts(0)
      val minor = parts(1)
      val patch = parts(2)
      bumpType.toLowerCase match
        case "patch" | "--patch" | "1" => Some(s"$major.$minor.${patch + 1}")
        case "minor" | "--minor" | "2" => Some(s"$major.${minor + 1}.0")
        case "major" | "--major" | "3" => Some(s"${major + 1}.0.0")
        case custom =>
          val v = custom.stripPrefix("v")
          if v.matches("""^\d+\.\d+\.\d+$""") then Some(v) else None
    else
      val v = bumpType.stripPrefix("v")
      if v.matches("""^\d+\.\d+\.\d+$""") then Some(v) else None
