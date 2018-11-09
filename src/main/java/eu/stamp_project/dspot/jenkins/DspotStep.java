package eu.stamp_project.dspot.jenkins;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.stamp_project.dspot.DSpot;
import eu.stamp_project.program.ConstantsProperties;
import eu.stamp_project.program.InputConfiguration;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.EditType;
import hudson.scm.ChangeLogSet.AffectedFile;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;

public class DspotStep extends Builder implements SimpleBuildStep {

	private static final Logger LOGGER = LoggerFactory.getLogger(DspotStep.class);

	@Nonnull
	private String projectPath = DescriptorImpl.defaultProjectPath;
	@Nonnull
	private String srcCode = DescriptorImpl.defaultSrcCode;
	@Nonnull
	private String testCode = DescriptorImpl.defaultTestCode;
	@Nonnull
	private String srcClasses = DescriptorImpl.defaultSrcClasses;
	@Nonnull
	private String testClasses = DescriptorImpl.defaultTestClasses;
	@Nonnull
	private String testFilter = DescriptorImpl.defaultTestFilter;
	@Nonnull
	private String outputDir = DescriptorImpl.defaultOutputDir;

	private boolean onlyChanges = false;

	private Properties init_properties;

	@DataBoundConstructor
	public DspotStep() {
		init_properties = new Properties();
	}

	public String getOutputDir() {
		return outputDir;
	}

	public String getProjectPath() {
		return projectPath;
	}

	public String getSrcClasses() {
		return srcClasses;
	}

	public String getSrcCode() {
		return srcCode;
	}

	public String getTestClasses() {
		return testClasses;
	}

	public String getTestCode() {
		return testCode;
	}

	public String getTestFilter() {
		return testFilter;
	}

	public boolean isOnlyChanges() {
		return onlyChanges;
	}

	@DataBoundSetter
	public void setOnlyChanges(boolean onlyChanges) {
		this.onlyChanges = onlyChanges;
	}

	@DataBoundSetter
	public void setOutputDir(@Nonnull String outputDir) {
		this.outputDir = outputDir;
	}

	@DataBoundSetter
	public void setProjectPath(@Nonnull String filepath) {
		projectPath = filepath;
	}

	@DataBoundSetter
	public void setSrcClasses(@Nonnull String srcClasses) {
		this.srcClasses = srcClasses;
	}

	@DataBoundSetter
	public void setSrcCode(@Nonnull String srcCode) {
		this.srcCode = srcCode;
	}

	@DataBoundSetter
	public void setTestClasses(@Nonnull String testClasses) {
		this.testClasses = testClasses;
	}

	@DataBoundSetter
	public void setTestCode(@Nonnull String testCode) {
		this.testCode = testCode;
	}

	@DataBoundSetter
	public void setTestFilter(@Nonnull String testFilter) {
		this.testFilter = testFilter;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void perform(Run<?, ?> run, FilePath wsp, Launcher arg2, TaskListener listener)
			throws InterruptedException, IOException {
		init_properties.setProperty(ConstantsProperties.PROJECT_ROOT_PATH.getName(),
				new FilePath(wsp, projectPath).getRemote());
		init_properties.setProperty(ConstantsProperties.SRC_CODE.getName(), new FilePath(wsp, srcCode).getRemote());
		init_properties.setProperty(ConstantsProperties.TEST_SRC_CODE.getName(),
				new FilePath(wsp, testCode).getRemote());
		init_properties.setProperty(ConstantsProperties.OUTPUT_DIRECTORY.getName(),
				new FilePath(wsp, outputDir).getRemote());
		init_properties.setProperty(ConstantsProperties.FILTER.getName(), testFilter);
		init_properties.setProperty(ConstantsProperties.TEST_CLASSES.getName(),
				new FilePath(wsp, testClasses).getRemote());
		init_properties.setProperty(ConstantsProperties.SRC_CLASSES.getName(),
				new FilePath(wsp, srcClasses).getRemote());

		InputConfiguration.initialize(init_properties).setUseWorkingDirectory(true).setVerbose(true);

		// analyze changes in workspace
		List<String> testList = new ArrayList<>();

		if (onlyChanges) {
			ChangeLogSet<? extends Entry> changes = null;
			if (AbstractBuild.class.isInstance(run)) {
				AbstractBuild build = (AbstractBuild) run;
				changes = build.getChangeSet();
			} else {
				try {
					// checking for WorkflowRun's getChangeSets method
					List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = (List<ChangeLogSet<? extends ChangeLogSet.Entry>>) run
							.getClass().getMethod("getChangeSets").invoke(run);
					if (!changeSets.isEmpty()) {
						changes = changeSets.get(0);
					}
				} catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
					// ignore
				}
			}
			if (changes != null) {
				Iterator<? extends ChangeLogSet.Entry> itrChangeSet = changes.iterator();
				while (itrChangeSet.hasNext()) {
					ChangeLogSet.Entry str = itrChangeSet.next();
					Collection<? extends ChangeLogSet.AffectedFile> affectedFiles = str.getAffectedFiles();
					Iterator<? extends ChangeLogSet.AffectedFile> affectedFilesItr = affectedFiles.iterator();
					while (affectedFilesItr.hasNext()) {
						AffectedFile file = affectedFilesItr.next();
						if (file.getEditType().equals(EditType.ADD) || file.getEditType().equals(EditType.EDIT)) {
							String path = file.getPath();
							if (path.startsWith(shouldUpdatePath.apply(testCode))) {
								testList.add(path);
								listener.getLogger().println("New test found at: " + path);
							}
						}
					}
				}
				if (testList.isEmpty())
					listener.getLogger().println("no tests changed. DSpot will not be run.");
			} else {
				listener.getLogger().println("could not get last changes. DSpot will run on the whole suite.");
				onlyChanges = false;
			}

		}

		try

		{
			DSpot dspot = new DSpot();
			InputConfiguration.get().getFactory().getEnvironment()
					.setInputClassLoader(DspotStep.class.getClassLoader());
			if (onlyChanges) {
				dspot.amplifyTestClasses(testList.stream().map(this::pathToQualifiedName).collect(Collectors.toList()));
			} else
				dspot.amplifyAllTests();
		} catch (Exception e) {
			listener.getLogger()
					.println("There was an error running DSpot on your project. Check the logs for details.");
			LOGGER.error("Build Failed", e);
			run.setResult(Result.UNSTABLE);
		}
		return;

	}

	public String pathToQualifiedName(String path) {
		String regex = File.separator.equals("/") ? "/" : "\\\\";
		return path.substring(getTestCode().length(), path.length() - ".java".length()).replaceAll(regex, ".");
	}

	public static final Function<String, String> shouldUpdatePath = string -> File.separator.equals("/") ? string
			: string.replaceAll("/", "\\\\");

	@Symbol("dspot")
	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

		public static final String defaultProjectPath = "";
		public static final String defaultSrcCode = ConstantsProperties.SRC_CODE.getDefaultValue();
		public static final String defaultTestCode = ConstantsProperties.TEST_SRC_CODE.getDefaultValue();
		public static final String defaultSrcClasses = ConstantsProperties.SRC_CLASSES.getDefaultValue();
		public static final String defaultTestClasses = ConstantsProperties.TEST_CLASSES.getDefaultValue();
		public static final String defaultTestFilter = ConstantsProperties.FILTER.getDefaultValue();
		public static final String defaultOutputDir = "dspot-out";
		public static final boolean defaultOnlyChanges = false;

		@Override
		public String getDisplayName() {
			return "STAMP DSpot";
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

	}

}