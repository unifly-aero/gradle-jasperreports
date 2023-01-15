package com.github.gmazelier.plugins

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider

class JasperReportsExtension {

	Iterable<File> classpath = []
	DirectoryProperty srcDir;
	DirectoryProperty tmpDir;
	DirectoryProperty outDir;
	String srcExt = '.jrxml'
	String outExt = '.jasper'
	String compiler = 'net.sf.jasperreports.engine.design.JRJdtCompiler'
	boolean keepJava = false
	boolean validateXml = true
	boolean verbose = false
	boolean useRelativeOutDir = false

	private Project project

	JasperReportsExtension(Project project) {
		this.project = project
		this.srcDir= project.getObjects().directoryProperty();
		this.outDir= project.getObjects().directoryProperty();
		this.tmpDir= project.getObjects().directoryProperty();
		setDefaults();
	}

	void setDefaults() {
		srcDir.convention(project.layout.projectDirectory.dir("src/main/jasperreports"));
		outDir.convention(project.layout.buildDirectory.dir("classes/jasper/main"))
		tmpDir.convention(project.layout.buildDirectory.dir("jasperreports"));
	}
}
