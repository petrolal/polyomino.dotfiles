package polyomino.dotfiles.sdd

import polyomino.dotfiles.context.Context
import polyomino.dotfiles.error.{CommandError, PolyominoError}

object SpecDrivenDev:
  def run(ctx: Context, args: List[String]): Either[PolyominoError, Unit] =
    println("[1;36m[polyomino sdd][0m Generating AI spec-driven development context...")

    val targetFile = args.find(!_.startsWith("-")).getOrElse("project-context.md")
    val candidatePaths = if targetFile.startsWith("/") then
      Seq(os.Path(targetFile))
    else
      Seq(
        ctx.dotfilesDir / targetFile,
        ctx.dotfilesDir / "docs" / targetFile,
        os.pwd / targetFile,
        os.pwd / "docs" / targetFile
      )
    val projectContextOpt = candidatePaths.find(os.exists)

    projectContextOpt match
      case Some(projectContext) =>
        val content = os.read(projectContext)
        val lines = content.linesIterator.toList
        val lineCount = lines.length
        val tokenEstimate = estimateTokens(content)

        println(s"   [32m[OK] [0m Loaded project context")
        println(s"   [32m[OK] [0m File: $projectContext")
        println(s"   [32m[OK] [0m Lines: $lineCount")
        println(s"   [32m[OK] [0m Estimated tokens: ~$tokenEstimate")
        println(s"   [32m[OK] [0m Bytes: ${content.length}")

        val showFull = args.contains("--full")
        val showTokens = args.contains("--tokens")

        if showFull then
          println("\n--- FULL CONTEXT ---")
          println(content)
          println("--- END CONTEXT ---")
        else if showTokens then
          println("\n--- TOKEN BREAKDOWN ---")
          val sections = content.split("\n#{1,6} ").drop(1)
          for section <- sections do
            val sectionName = section.takeWhile(_ != '\n')
            val sectionContent = section.dropWhile(_ != '\n').drop(1)
            val sectionTokens = estimateTokens(sectionContent)
            println(f"  $sectionName%-40s ~$sectionTokens%-5d tokens")
          println("--- END TOKEN BREAKDOWN ---")
        else
          println("\n--- SPEC DRIVEN CONTEXT HEAD (first 30 lines) ---")
          println(lines.take(30).mkString("\n"))
          println("...\n--- END HEAD ---")
          println("\nUse --full to see all content, --tokens for token breakdown")

        Right(())
      case None =>
        Left(CommandError(s"Project context file '$targetFile' not found in dotfiles or docs/", 1))

  private def estimateTokens(text: String): Int =
    val words = text.split("\\s+").length
    (text.length / 4) max ((words * 1.3).toInt)
