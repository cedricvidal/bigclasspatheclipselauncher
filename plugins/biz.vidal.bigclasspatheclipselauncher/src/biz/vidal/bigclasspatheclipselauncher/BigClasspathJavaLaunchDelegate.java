package biz.vidal.bigclasspatheclipselauncher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.internal.launching.LaunchingMessages;
import org.eclipse.jdt.launching.ExecutionArguments;
import org.eclipse.jdt.launching.IVMRunner;
import org.eclipse.jdt.launching.JavaLaunchDelegate;
import org.eclipse.jdt.launching.VMRunnerConfiguration;

import biz.vidal.bigclasspatheclipselauncher.internal.Activator;

public class BigClasspathJavaLaunchDelegate extends JavaLaunchDelegate {
	@Override
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {

		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}

		monitor.beginTask(MessageFormat.format("{0}...", new String[] { configuration.getName() }), 3); //$NON-NLS-1$
		// check for cancellation
		if (monitor.isCanceled()) {
			return;
		}
		try {
			monitor.subTask(LaunchingMessages.JavaLocalApplicationLaunchConfigurationDelegate_Verifying_launch_attributes____1);

			String mainTypeName = verifyMainTypeName(configuration);
			String classworldsMainTypeName = "org.codehaus.classworlds.Launcher";
			IVMRunner runner = getVMRunner(configuration, mode);

			File workingDir = verifyWorkingDirectory(configuration);
			String workingDirName = null;
			if (workingDir != null) {
				workingDirName = workingDir.getAbsolutePath();
			}

			// Environment variables
			String[] envp = getEnvironment(configuration);

			File classworldsConfigurationFile = createTempClassworldsConfigurationFile();

			// Program & VM arguments
			String pgmArgs = getProgramArguments(configuration);
			String vmArgs = getVMArguments(configuration);
			vmArgs = (vmArgs != null ? vmArgs : "") + " -Dclassworlds.conf=" + classworldsConfigurationFile.getAbsolutePath();
			ExecutionArguments execArgs = new ExecutionArguments(vmArgs, pgmArgs);

			// VM-specific attributes
			Map vmAttributesMap = getVMSpecificAttributesMap(configuration);

			// Classpath
			String[] classpath = getClasspath(configuration);
			String[] classworldsClasspath = new String[] { getClassworldsJarFile() };
			writeClassworldsConfiguration(createOutputStream(classworldsConfigurationFile), mainTypeName, classpath);

			// Create VM config
			VMRunnerConfiguration runConfig = new VMRunnerConfiguration(classworldsMainTypeName, classworldsClasspath);
			runConfig.setProgramArguments(execArgs.getProgramArgumentsArray());
			runConfig.setEnvironment(envp);
			runConfig.setVMArguments(execArgs.getVMArgumentsArray());
			runConfig.setWorkingDirectory(workingDirName);
			runConfig.setVMSpecificAttributesMap(vmAttributesMap);

			// Bootpath
			runConfig.setBootClassPath(getBootpath(configuration));

			// check for cancellation
			if (monitor.isCanceled()) {
				return;
			}

			// stop in main
			prepareStopInMain(configuration);

			// done the verification phase
			monitor.worked(1);

			monitor.subTask(LaunchingMessages.JavaLocalApplicationLaunchConfigurationDelegate_Creating_source_locator____2);
			// set the default source locator if required
			setDefaultSourceLocator(launch, configuration);
			monitor.worked(1);

			// Launch the configuration - 1 unit of work
			runner.run(runConfig, launch, monitor);

			// check for cancellation
			if (monitor.isCanceled()) {
				return;
			}
		} finally {
			monitor.done();
		}
	}

	/**
	 * @return
	 * @throws IOException
	 */
	public String getClassworldsJarFile() {
		try {
			URL bundleUrl = Activator.getContext().getBundle().getResource("lib/classworlds-1.0.jar");
			URL fileURL = FileLocator.toFileURL(bundleUrl);
			String file = new File(fileURL.getFile()).getPath();
			return file;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param classworldsConfigurationFile
	 * @return
	 * @throws FileNotFoundException
	 */
	public FileOutputStream createOutputStream(File classworldsConfigurationFile) {
		try {
			return new FileOutputStream(classworldsConfigurationFile);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @return
	 * @throws IOException
	 */
	public File createTempClassworldsConfigurationFile() {
		try {
			return File.createTempFile("classworlds", null);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	protected String getClassworldsConfiguration(String mainTypeName, String[] classpath) {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		writeClassworldsConfiguration(os, mainTypeName, classpath);
		return os.toString();
	}

	/**
	 * @param os
	 * @param mainTypeName
	 * @param classpath
	 */
	public void writeClassworldsConfiguration(OutputStream os, String mainTypeName, String[] classpath) {
		PrintStream print = new PrintStream(os);
		print.println("main is " + mainTypeName + " from app");
		print.println("[app]");
		for (String entry : classpath) {
			print.println("\tload " + entry);
		}
	}

}
