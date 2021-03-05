package org.cohorte.eclipse.felix.config.generator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.psem2m.utilities.CXException;
import org.psem2m.utilities.files.CXFileDir;
import org.psem2m.utilities.files.CXFileUtf8;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * class that read a launch configuration to generator a config.properties that
 * is use by felix to launch framework
 *
 * @author apisu
 *
 */
@Mojo(name = "generate-config", defaultPhase = LifecyclePhase.COMPILE)
public class ConfigGenerator extends AbstractMojo {
	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	MavenProject project;

	@Parameter(property = "scope")
	String pScope;
	// base new config file on this config file
	@Parameter(property = "base.felix.config.file.path")
	String pSourceConfigFile;

	@Parameter(property = "launch.eclipse.file.path")
	String pLaunchEclipseFile;

	@Parameter(property = "shell.felix.jar.file.path")
	String pShellFelixJarFilePath;

	@Parameter(property = "shell.felix.config.file.path")
	String pShellFelixConfigFilePath;

	@Parameter(property = "felix.cache.rootdir")
	String pFelixCacheRootDir;

	@Parameter(property = "shell.vmargument")
	String pOverrideShellArgument;

	@Parameter(property = "target.shell.file.path")
	String pTargetLaunchJvmFile;

	@Parameter(property = "target.config.file.path")
	String pPathTargerConfigFile;

	// can express multiple folder with ";" separator. the property express also
	// pair of path in local disk and path in target disk for config
	// eg cohorte-home/repo:opt/cohorte/repo; another path
	@Parameter(property = "bundle.jar.directories")
	String pPathBundleTarget;

	private final Properties pProperties = new Properties();

	private final Map<String, String> pMapSymbolicNameToJarPath = new HashMap<>();

	/**
	 * return the symolic bundle name in jar file if t's a bundle else null
	 *
	 * @return
	 * @throws IOException
	 */
	private String getBundleSymbolicNameFromJar(String aFilePath) throws IOException {
		getLog().debug(String.format("getBundleSymbolicNameFromJar file =[%s]", aFilePath));

		String wSymbolicName = null;
		final ZipFile wZipFile = new ZipFile(aFilePath);

		try {

			final Enumeration<? extends ZipEntry> wEntries = wZipFile.entries();

			while (wEntries.hasMoreElements()) {
				final ZipEntry wEntry = wEntries.nextElement();
				if (wEntry.getName().contains("MANIFEST.MF")) {
					final InputStream wStream = wZipFile.getInputStream(wEntry);
					final String wContent = new BufferedReader(new InputStreamReader(wStream)).lines()
							.collect(Collectors.joining("\n"));
					wStream.close();
					final String[] wLines = wContent.split("\n");
					for (int i = 0; i < wLines.length && wSymbolicName == null; i++) {
						final String wLine = wLines[i];
						if (wLine.contains("Bundle-SymbolicName: ")) {

							wSymbolicName = wLine.replace("Bundle-SymbolicName: ", "");
							getLog().debug(String.format("symbolic name=[%s] file =[%s]", wSymbolicName, aFilePath));

						}
					}
				}
			}

			return wSymbolicName;
		} finally {
			wZipFile.close();
		}
	}

	/**
	 * list bundle jar file in this directory with symbolic name
	 *
	 * @param aDir
	 *
	 * @return
	 * @throws IOException
	 */
	private void analyseDir(CXFileDir aDir) throws IOException {
		getLog().debug(String.format("analyseDir =[%s]", aDir.getAbsolutePath()));

		for (final String wFile : aDir.list()) {
			getLog().debug(String.format("dir=[%s] file =[%s]", aDir.getAbsolutePath(), wFile));

			if (wFile.endsWith(".jar")) {
				// check if it's a bundle
				final String wFullFilePath = aDir.getAbsolutePath() + File.separatorChar + wFile;
				final String wSymbolicBundleName = getBundleSymbolicNameFromJar(wFullFilePath);
				if (wSymbolicBundleName != null) {
					getLog().debug(
							String.format("symbolicName=[%s], path jar=[%s]", wSymbolicBundleName, wFullFilePath));

					pMapSymbolicNameToJarPath.put(wSymbolicBundleName, wFullFilePath);
				}
			} else {
				final CXFileDir wSubDir = new CXFileDir(aDir, wFile);
				if (wSubDir.exists() && wSubDir.isDirectory()) {
					analyseDir(wSubDir);
				}
			}
		}

	}

	private Map<String, String> analyseDirectory(String aPathBundleTarget) throws MojoExecutionException {
		final Map<String, String> wDirsBundleLocation = new HashMap<>();

		try {
			final String wPathBundle = aPathBundleTarget.replaceAll("\n", "").replaceAll("\t", "").replaceAll(" ", "");
			if (wPathBundle.contains(";")) {
				for (final String wPathPair : wPathBundle.split(";")) {
					if (wPathPair.contains(":")) {
						final String wPathLocalDir = wPathPair.split(":")[0];
						final String wPathTargetDir = wPathPair.split(":")[1];

						final CXFileDir wDir = new CXFileDir(wPathLocalDir);
						if (wDir.isDirectory() && wDir.exists()) {
							getLog().info(String.format("add dir local=[%s]!", wPathLocalDir));
							getLog().info(String.format("add dir target=[%s]!", wPathTargetDir));
							analyseDir(wDir);

							wDirsBundleLocation.put(wPathLocalDir, wPathTargetDir);
						}
					}

				}
			} else {
				if (wPathBundle.contains(":")) {
					final String wPathLocalDir = wPathBundle.split(":")[0];
					final String wPathTargetDir = wPathBundle.split(":")[1];
					final CXFileDir wDir = new CXFileDir(wPathLocalDir);
					if (wDir.isDirectory() && wDir.exists()) {
						getLog().info(String.format("add dir local=[%s]!", wPathLocalDir));
						getLog().info(String.format("add dir target=[%s]!", wPathTargetDir));
						analyseDir(wDir);

						wDirsBundleLocation.put(wPathLocalDir, wPathTargetDir);
					}
				}
			}
			return wDirsBundleLocation;

		} catch (final Exception e) {
			getLog().error(String.format("fail to analyse directory %s error=[%s]!", pPathBundleTarget,
					CXException.eInString(e)));
			throw new MojoExecutionException(String.format("fail to analyse directory %s error=[%s]!",
					aPathBundleTarget, CXException.eInString(e)));
		}

	}

	private Document getDocumentFromLauncherFile(String aLaunchConfigFile)
			throws ParserConfigurationException, SAXException, IOException {
		final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder;
		dBuilder = dbFactory.newDocumentBuilder();

		return dBuilder.parse(aLaunchConfigFile);
	}

	private String getVMParameter(Document wDocLauncherEclipse)
			throws SAXException, IOException, ParserConfigurationException {

		String wVmArgument = null;

		final NodeList wNodeList = wDocLauncherEclipse.getElementsByTagName("stringAttribute");
		// checkk which string property describe list of bundle
		for (int x = 0; x < wNodeList.getLength() && wVmArgument == null; x++) {
			final Node wNode = wNodeList.item(x);
			if (wNode instanceof Element) {
				final Element wElement = (Element) wNode;
				final String wValue = null;
				if (wElement.getAttribute("key") != null) {
					if (wElement.getAttribute("key").equals("org.eclipse.jdt.launching.VM_ARGUMENTS")) {
						wVmArgument = wElement.getAttribute("value");

					}
				}

			}
		}
		String wVmArgUsableInShell = "";
		final String[] wLines = wVmArgument.split("\n");
		final List<String> wOverrideShellArgument = Arrays.asList(pOverrideShellArgument.split("\n"));
		final Map<String, String> wMapOverrideArgument = new HashMap<>();
		for (final String wOverrideArg : wOverrideShellArgument) {
			if (wOverrideArg.contains("=")) {
				String wArgumentKey = wOverrideArg.split("=")[0];
				if (wArgumentKey.startsWith("-D")) {
					wArgumentKey = wArgumentKey.substring(2);
				}
				wMapOverrideArgument.put(wArgumentKey, wOverrideArg.split("=")[1]);
			}

		}

		for (final String wLine : wLines) {
			if (wLine.trim().length() > 0) {
				String wArgumentKey = null;

				if (wLine.contains("=")) {
					wArgumentKey = wLine.split("=")[0];
					if (wArgumentKey.startsWith("-D")) {
						wArgumentKey = wArgumentKey.substring(2);
					}
				}
				if (wArgumentKey != null) {
					if (wMapOverrideArgument.keySet().contains(wArgumentKey)) {
						wVmArgUsableInShell += "\t -D" + wArgumentKey + "=" + wMapOverrideArgument.get(wArgumentKey)
								+ " \\\n";
					} else {
						wVmArgUsableInShell += "\t" + wLine + " \\\n";
					}
				} else {
					wVmArgUsableInShell += "\t" + wLine + " \\\n";

				}

			}
		}
		getLog().debug(String.format("vm arguments [%s]", wVmArgUsableInShell));

		return wVmArgUsableInShell;
	}

	private List<String> getListSymbolicBundleNameToAdd(Document wDocLauncherEclipse)
			throws SAXException, IOException, ParserConfigurationException {

		final List<String> wLisSymbolicBundleName = new ArrayList<>();

		final NodeList wNodeList = wDocLauncherEclipse.getElementsByTagName("stringAttribute");
		// checkk which string property describe list of bundle
		for (int x = 0; x < wNodeList.getLength(); x++) {
			final Node wNode = wNodeList.item(x);
			if (wNode instanceof Element) {
				final Element wElement = (Element) wNode;
				String wValue = null;
				if (wElement.getAttribute("key") != null) {
					if (wElement.getAttribute("key").equals("workspace_bundles")) {
						// bundle of the current project
						wValue = wElement.getAttribute("value");
					} else if (wElement.getAttribute("key").equals("target_bundles")) {
						// bundle of the current project
						wValue = wElement.getAttribute("value");
					}
				}
				if (wValue != null) {
					for (final String wBundleName : wValue.split(",")) {
						if (wBundleName.contains("@")) {
							wLisSymbolicBundleName.add(wBundleName.split("@")[0]);
						}
					}
				}
				// search bundle path in repo

			}
		}
		getLog().debug(String.format("symbolic name bundle to add [%s]", wLisSymbolicBundleName));

		return wLisSymbolicBundleName;
	}

	private void createJvmShell(Document aLauncherEclipseDom)
			throws MojoExecutionException, IOException, SAXException, ParserConfigurationException {
		final String wShellFormat = "java %s -Dfelix.config.properties=%s -Dfile.encoding=UTF-8 -jar %s bundle-cache -consoleLog -console";
		final String wVmArgument = getVMParameter(aLauncherEclipseDom);
		final String wShell = String.format(wShellFormat, wVmArgument, pShellFelixConfigFilePath,
				pShellFelixJarFilePath);
		getLog().info(String.format("shell launch jvm =[%s]", wShell));
		if (pTargetLaunchJvmFile != null) {
			final CXFileUtf8 wShellFile = new CXFileUtf8(pTargetLaunchJvmFile);
			wShellFile.writeAll(wShell);
		}
	}

	private void createConfigFelixFile(File aFileBaseConfigTest, Document aLauncherEclipseDom)
			throws MojoExecutionException, IOException, SAXException, ParserConfigurationException {

		final Map<String, String> wDirsBundleLocation = analyseDirectory(pPathBundleTarget);

		// set default value for properties if no base
		pProperties.put("org.osgi.framework.storage.clean", "none");
		pProperties.put("org.osgi.framework.storage", "bundle-cache");
		pProperties.put("org.osgi.framework.startlevel.beginning", 4);
		pProperties.put("felix.cache.rootdir",
				pFelixCacheRootDir != null ? pFelixCacheRootDir : "/opt/node/felix/rootdir");

		if (aFileBaseConfigTest != null && aFileBaseConfigTest.exists()) {
			//

			final CXFileUtf8 wFileBaseConfig = new CXFileUtf8(aFileBaseConfigTest);
			getLog().info(String.format("base config=[%s]!", wFileBaseConfig.getAbsolutePath()));

			pProperties.load(wFileBaseConfig.getInputStream());
		}
		final List<String> wListSymbolicBundleName = getListSymbolicBundleNameToAdd(aLauncherEclipseDom);
		// get bundle property to add the new one
		String wListBundles = pProperties.getProperty("felix.auto.start.4");
		if (wListBundles == null) {
			wListBundles = "";
		}
		for (final String wSymbolicBundleToAdd : wListSymbolicBundleName) {

			if (pMapSymbolicNameToJarPath.containsKey(wSymbolicBundleToAdd)) {
				if (wListBundles.length() > 0) {
					wListBundles += " \\\n";
				}
				final String wAddBundle = "file:\\" + pMapSymbolicNameToJarPath.get(wSymbolicBundleToAdd);
				getLog().info(String.format("add bundle=[%s]!", wAddBundle));
				// todo replace path by new location
				wListBundles += wAddBundle;
			}
		}
		// reploace all localDir by target dir
		for (final String wLocalDir : wDirsBundleLocation.keySet()) {
			wListBundles = wListBundles.replaceAll(wLocalDir, wDirsBundleLocation.get(wLocalDir));
		}
		getLog().info(String.format("felix.auto.start.4=[%s]!", wListBundles));
		pProperties.put("felix.auto.start.4", wListBundles);

		getLog().debug(String.format("properties file content=[%s]", pProperties.toString()));
		if (pPathTargerConfigFile != null) {
			final CXFileUtf8 pFileTargerConfigFile = new CXFileUtf8(pPathTargerConfigFile);
			for (final Object wKey : pProperties.keySet()) {
				pFileTargerConfigFile.write(wKey.toString() + "=" + pProperties.getProperty(wKey.toString()));

			}
			pFileTargerConfigFile.close();
		}
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		// TODO Auto-generated method stub
		getLog().info("execute ");

		if (pLaunchEclipseFile != null) {
			final File wFileLaunchConfig = new File(pLaunchEclipseFile);
			final File wFileBaseConfig = pSourceConfigFile != null ? new File(pSourceConfigFile) : null;
			if (pPathBundleTarget != null) {

				if (!wFileLaunchConfig.exists()) {
					getLog().error(String.format("file %s no found !", wFileLaunchConfig.getAbsolutePath()));
					throw new MojoExecutionException(
							String.format("file %s no found !", wFileLaunchConfig.getAbsolutePath()));
				} else {
					try {

						getLog().info(String.format("launch file=[%s]!", wFileLaunchConfig.getAbsolutePath()));
						getLog().info(String.format("path directories jar=[%s]!", pPathBundleTarget));
						final Document wLauncherEclipseDom = getDocumentFromLauncherFile(pLaunchEclipseFile);
						createConfigFelixFile(wFileBaseConfig, wLauncherEclipseDom);
						createJvmShell(wLauncherEclipseDom);
					} catch (final Exception e) {
						getLog().error(String.format("fail to parse xml file %s error=[%s]!",
								wFileLaunchConfig.getAbsolutePath(), CXException.eInString(e)));
						throw new MojoExecutionException(String.format("fail to parse xml file %s error=[%s]!",
								wFileLaunchConfig.getAbsolutePath(), CXException.eInString(e)));
					}
				}
			} else {
				getLog().info(String.format("no path bundle directory !"));

			}
		} else {
			getLog().info(String.format("no launch file !"));

		}
	}
}