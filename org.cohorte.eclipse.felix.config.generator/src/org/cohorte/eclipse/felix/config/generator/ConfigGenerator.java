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

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.psem2m.utilities.CXException;
import org.psem2m.utilities.files.CXFile;
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

	// base new config file on this config file
	@Parameter(property = "base.felix.config.file.path")
	String sourceConfigFile;

	@Parameter(property = "launch.eclipse.file.path")
	String launchEclipseFile;

	@Parameter(property = "shell.felix.jar.file.path")
	String shellFelixJarFilePath;

	@Parameter(property = "shell.felix.config.file.path")
	String shellFelixConfigFilePath;

	@Parameter(property = "felix.cache.rootdir")
	String felixCacheRootDir;

	@Parameter(property = "shell.vmarguments")
	String overrideShellArgument;

	@Parameter(property = "shell.vmarguments.file.path")
	String overrideShellArgumentFilePath;

	@Parameter(property = "target.shell.file.path")
	String targetLaunchJvmFile;

	@Parameter(property = "target.config.file.path")
	String pathTargerConfigFile;

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
				String wSymbolicBundleName = getBundleSymbolicNameFromJar(wFullFilePath);
				if (wSymbolicBundleName != null) {
					if (wSymbolicBundleName.contains(";")) {
						wSymbolicBundleName = wSymbolicBundleName.split(";")[0];
					}
					getLog().debug(String.format("===>symbolicName=[%s] \n, path jar=[%s]", wSymbolicBundleName,
							wFullFilePath));

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

							wDirsBundleLocation.put(wDir.getAbsolutePath(), wPathTargetDir);
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

						wDirsBundleLocation.put(wDir.getAbsolutePath(), wPathTargetDir);
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
		final Map<String, String> wMapOverrideArgument = new HashMap<>();
		if (overrideShellArgumentFilePath != null) {
			getLog().debug(String.format("file override argument vm %s", overrideShellArgumentFilePath));
			final CXFileUtf8 wFileContentOverrideVmArg = new CXFileUtf8(overrideShellArgumentFilePath);
			if (wFileContentOverrideVmArg.exists()) {

				overrideShellArgument = wFileContentOverrideVmArg.readAll();
				getLog().debug(String.format("content file override argument vm %s", overrideShellArgument));

			}
		} else {
			getLog().debug(String.format("no file override argument vm %s", overrideShellArgumentFilePath));

		}
		if (overrideShellArgument != null) {
			final List<String> wOverrideShellArgument = Arrays.asList(overrideShellArgument.split("\n"));

			for (final String wOverrideArg : wOverrideShellArgument) {
				if (wOverrideArg.contains("=")) {
					final String[] wSplit = wOverrideArg.split("=");
					String wArgumentKey = wSplit[0];
					getLog().debug(String.format("argument override  key %s", wArgumentKey));
					wArgumentKey = wArgumentKey.replaceAll(" ", "").replaceAll("\t", "");
					if (wArgumentKey.startsWith("-D")) {
						wArgumentKey = wArgumentKey.substring(2);
					}
					if (wSplit.length > 1) {
						wMapOverrideArgument.put(wArgumentKey, wSplit[1]);
					} else {
						wMapOverrideArgument.put(wArgumentKey, "");

					}
				}

			}
		} else {
			getLog().debug(String.format("no override argumet"));

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
					getLog().debug(String.format("argument key %s", wArgumentKey));

					if (wMapOverrideArgument.keySet().contains(wArgumentKey)) {
						// wVmArgUsableInShell += "# override launch eclipse vm argument by maven
						// task\n";
						wVmArgUsableInShell += "\t-D" + wArgumentKey + "=" + wMapOverrideArgument.get(wArgumentKey)
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
		final String wShellFormat = "#!/bin/sh\njava %s -Dfelix.config.properties=file:/%s -Dfile.encoding=UTF-8 -jar %s bundle-cache -consoleLog -console";
		final String wVmArgument = getVMParameter(aLauncherEclipseDom);
		final String wShell = String.format(wShellFormat, wVmArgument, shellFelixConfigFilePath, shellFelixJarFilePath);
		getLog().info(String.format("shell launch jvm =[%s]", wShell));
		if (targetLaunchJvmFile != null) {
			final CXFileUtf8 wShellFile = new CXFileUtf8(targetLaunchJvmFile);
			wShellFile.getParentDirectory().mkdirs();
			wShellFile.writeAll(wShell);
		}
	}

	private void createConfigFelixFile(String aFileBaseConfifPath, Document aLauncherEclipseDom)
			throws MojoExecutionException, IOException, SAXException, ParserConfigurationException {

		final Map<String, String> wDirsBundleLocation = analyseDirectory(pPathBundleTarget);

		if (aFileBaseConfifPath != null) {
			// load property from file
			if (aFileBaseConfifPath.startsWith("http://") || aFileBaseConfifPath.startsWith("https://")) {
				// get content by using http
				final HttpGet wGet = new HttpGet(aFileBaseConfifPath);
				final CloseableHttpClient wClient = HttpClientBuilder.create().build();
				final HttpResponse wResponse = wClient.execute(wGet);
				pProperties.load(wResponse.getEntity().getContent());
			} else {
				final CXFile wFileBaseProperty = new CXFile(aFileBaseConfifPath);
				if (wFileBaseProperty.exists()) {
					pProperties.load(wFileBaseProperty.getInputStream());
				}

			}

		} else {
			// set default value for properties if no base
			pProperties.put("org.osgi.framework.storage.clean", "none");
			pProperties.put("org.osgi.framework.storage", "bundle-cache");
			pProperties.put("org.osgi.framework.startlevel.beginning", "4");
			pProperties.put("felix.cache.rootdir",
					felixCacheRootDir != null ? felixCacheRootDir : "/opt/node/felix/rootdir");
		}

		final List<String> wListSymbolicBundleName = getListSymbolicBundleNameToAdd(aLauncherEclipseDom);
		final List<String> wTreatedSymbolicNames = new ArrayList<>();
		wTreatedSymbolicNames.addAll(wListSymbolicBundleName);

		// add felix framework that is not checked in eclipse configuration
		// get bundle property to add the new one
		String wListBundles = pProperties.getProperty("felix.auto.start.4");
		if (wListBundles == null) {
			wListBundles = "";
		}
		// wListBundles += "# add bundle from launch configuration \n";

		for (final String wSymbolicBundleToAdd : wListSymbolicBundleName) {

			if (pMapSymbolicNameToJarPath.containsKey(wSymbolicBundleToAdd)) {
				if (wListBundles.length() > 0) {
					wListBundles += " \\\n";
				}
				final String wAddBundle = "file:\\" + pMapSymbolicNameToJarPath.get(wSymbolicBundleToAdd);
				getLog().info(String.format("add bundle=[%s]!", wAddBundle));
				// todo replace path by new location
				wListBundles += wAddBundle;
				wTreatedSymbolicNames.remove(wSymbolicBundleToAdd);
			}
		}
		if (wTreatedSymbolicNames.size() > 0) {
			getLog().error("symbolicName no treated " + wTreatedSymbolicNames);
			throw new MojoExecutionException("symbolicName no treated " + wTreatedSymbolicNames);

		}
		// reploace all localDir by target dir
		for (final String wLocalDir : wDirsBundleLocation.keySet()) {
			wListBundles = wListBundles.replaceAll(wLocalDir, wDirsBundleLocation.get(wLocalDir));
		}
		getLog().info(String.format("felix.auto.start.4=[%s]!", wListBundles));
		pProperties.put("felix.auto.start.4", wListBundles);

		getLog().debug(String.format("properties file content=[%s]", pProperties.toString()));
		if (pathTargerConfigFile != null) {
			final CXFileUtf8 pFileTargerConfigFile = new CXFileUtf8(pathTargerConfigFile);
			pFileTargerConfigFile.getParentDirectory().mkdirs();
			pFileTargerConfigFile.openWrite();
			for (final Object wKey : pProperties.keySet()) {
				pFileTargerConfigFile.write(wKey.toString() + "=" + pProperties.getProperty(wKey.toString()) + "\n");

			}
			pFileTargerConfigFile.close();
		}
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		// TODO Auto-generated method stub
		getLog().info("execute ");

		if (launchEclipseFile != null) {
			final File wFileLaunchConfig = new File(launchEclipseFile);
			if (pPathBundleTarget != null) {

				if (!wFileLaunchConfig.exists()) {
					getLog().error(String.format("file %s no found !", wFileLaunchConfig.getAbsolutePath()));
					throw new MojoExecutionException(
							String.format("file %s no found !", wFileLaunchConfig.getAbsolutePath()));
				} else {
					try {

						getLog().info(String.format("launch file=[%s]!", wFileLaunchConfig.getAbsolutePath()));
						getLog().info(String.format("path directories jar=[%s]!", pPathBundleTarget));
						final Document wLauncherEclipseDom = getDocumentFromLauncherFile(launchEclipseFile);
						createConfigFelixFile(sourceConfigFile, wLauncherEclipseDom);
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
			getLog().info(String.format("no launch file %s!", launchEclipseFile));

		}
	}
}