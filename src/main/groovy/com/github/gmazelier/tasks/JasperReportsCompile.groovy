package com.github.gmazelier.tasks

import net.sf.jasperreports.engine.JasperCompileManager
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

import java.nio.file.Path
import java.nio.file.Paths

import static groovyx.gpars.GParsPool.withPool

@CacheableTask
abstract class JasperReportsCompile extends DefaultTask {

	@InputFiles
	@PathSensitive(PathSensitivity.RELATIVE)
	Iterable<File> classpath

	@InputDirectory
	@Incremental
	@PathSensitive(PathSensitivity.RELATIVE)
	abstract DirectoryProperty getSrcDir();

	@OutputDirectory
	abstract DirectoryProperty getOutDir();
	@Input
	def String srcExt
	@Input
	def String outExt
	@Input
	def boolean verbose
	@Input
	def boolean useRelativeOutDir
	@Console
	def log = getLogger()

	@TaskAction
	void execute(InputChanges inputChanges) {

		def dependencies = classpath.collect { dependency ->
			dependency?.toURI()?.toURL()
		}
		if (verbose) log.lifecycle "Additional classpath: ${dependencies}"

		def compilationTasks = []
		inputChanges.getFileChanges(srcDir).each { change ->
			if (change.changeType == ChangeType.REMOVED) {
				if (verbose) log.lifecycle "Removed file ${change.file.name}"
				def fileToRemove = outDir.file(change.normalizedPath.replaceAll(srcExt, outExt)).get().asFile
				fileToRemove.delete()
			} else {
				if (change.file.name.endsWith(srcExt)) {
					compilationTasks << [src: change.getFile(), out: outputFile(change.getFile()), deps: dependencies]
				}
			}
		}

		def start = System.currentTimeMillis()

		def results = []
		withPool {
			results = compilationTasks.collectParallel { task ->
				def src = task['src'] as File
				def out = task['out'] as File
				def deps = task['deps']

				if (verbose) log.lifecycle "Compiling file ${src.absolutePath} to ${out.absolutePath}"

				try {
					// Configure class loader with addtional dependencies
					ClassLoader originalLoader = Thread.currentThread().getContextClassLoader()
					URLClassLoader loader = new URLClassLoader(deps as URL[], originalLoader)
					Thread.currentThread().setContextClassLoader loader
					// Compile report
					JasperCompileManager.compileReportToFile src.absolutePath, out.absolutePath
					// Restore class loader
					Thread.currentThread().setContextClassLoader originalLoader
				} catch (any) {
					return [name: src.name, success: false, exception: any]
				}
				[name: src.name, success: true]
			}
		}

		def stop = System.currentTimeMillis()

		def failures = results.findAll { !it['success'] }
		if (failures) throw new GradleException(failureMessage(failures))

		log.lifecycle "${results.size()} designs compiled in ${stop - start} ms"
	}

	def File outputFile(File src) {

		// local variable so we aren't continuously appending to the outDir
		def useOutDir = outDir.get().asFile;

		if (useRelativeOutDir) {

			Path srcPath = src.toPath()
			Path srcDirPath = srcDir.getAbsoluteFile().toPath()
			Path relativePath = srcDirPath.relativize(srcPath)
			def parent = relativePath.parent != null ? relativePath.parent.toString() : ""
			def path = Paths.get(useOutDir.absolutePath, parent)

			useOutDir = path.toFile()
			if (!useOutDir.isDirectory()) {
				if (verbose) log.lifecycle "Create outDir: ${useOutDir.absolutePath}"
				useOutDir.mkdirs()
			}
		}

		new File(useOutDir, src.name.replaceAll(srcExt, outExt))

	}

	@Console
	def failureMessage = { List failures ->
		def stringBuilder = new StringBuilder()
		stringBuilder.append "Could not compile ${failures.size()} designs:\n"
		failures.each { failure ->
			stringBuilder.append "\t[${failure['name']}] ${failure['exception'].message}\n"
		}
		stringBuilder.toString()
	}

}
