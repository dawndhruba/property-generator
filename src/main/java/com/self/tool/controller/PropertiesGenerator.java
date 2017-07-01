package com.self.tool.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class PropertiesGenerator {

	private static final String PROJECT_VERSION = "develop-SNAPSHOT";

	public static void main(String[] args) {

		System.out.println("\n\n\n##########################################################################");
		System.out.println("######################## PropertyGenerator : start #######################");
		System.out.println("##########################################################################\n\n\n");

		System.out.println("[INFO] Searching for pom.xml...");

		try {
			String workspace = args[0];
			String projectName = args[1];
			System.out.println("\n[INFO] Received workspace path : " + workspace);
			System.out.println("[INFO] Received project name   : " + projectName);

			if (StringUtils.isEmpty(workspace) || StringUtils.isEmpty(projectName)) {
				workspace = "src\\main\\resources\\test-workspace";
				projectName = "my-project-lib";
				System.out.println("\n[WARN] Incorrect or missing arguments...");
				System.out.println("[WARN] Using hardcoded workspace, project: " + workspace + ", " + projectName);
			}

			String projectPath = null;

			if (workspace.endsWith("\\")) {
				projectPath = workspace + projectName;
			} else {
				projectPath = workspace + "\\" + projectName;
			}
			final String pomFilePath = projectPath + "\\pom.xml";
			final File pomFile = new File(pomFilePath);
			if (!pomFile.canRead()) {
				System.out.println("\n[ERROR] No pom found at: " + pomFilePath);
				System.out.println("[ERROR] Exiting...\n");
				return;
			}

			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document document = docBuilder.parse(pomFile);

			System.out.println("\n[INFO] Reading pom.xml...");


			Node projectNode = document.getFirstChild();
			NodeList dependencies = document.getElementsByTagName("dependency");

			boolean isPomUpdated = false;
			
			if (dependencies != null && dependencies.getLength() > 0) {
				createPOMBackupIfNotPresent(projectPath, pomFile);
				createPropertiesIfNotPresent(document, projectNode, pomFilePath);

				for (int in = 0; in < dependencies.getLength(); in++) {
					String artifactId = "";
					String version = "";
					Node dependency = dependencies.item(in);
					Node versionNode = null;

					for (int in2 = 0; in2 < dependency.getChildNodes().getLength(); in2++) {
						Node node = dependency.getChildNodes().item(in2);

						if (node != null && "artifactId".equals(node.getNodeName())) {
							artifactId = node.getTextContent();
						}

						if (node != null && "version".equals(node.getNodeName())) {
							version = node.getTextContent();
							versionNode = node;
						}

					}

					if (versionNode != null && version.startsWith("$")) {
						if ("${project.version}".equalsIgnoreCase(version)) {
							isPomUpdated = addProjectVersionProperty(document, projectNode, artifactId, versionNode, isPomUpdated);
						}
					} else if (versionNode != null) {
						isPomUpdated = addHardcodedVersionProperty(document, projectNode, artifactId, version,
								versionNode, isPomUpdated);
					}
				}
				
				//If anything is changed then only insert new line and tab
				if(isPomUpdated) {
					NodeList properties = document.getElementsByTagName("properties");
					Element propertiesNode = (Element) properties.item(0);
					propertiesNode.appendChild(document.createTextNode("\n\t"));
				}

				if(isPomUpdated) {
					saveChanges(pomFilePath, document);
					System.out.println("\n\n*****************[SUCCESS] pom.xml updated and saved**********************\n\n");
					
					buildChanges(projectPath, pomFile);
				} else {
					System.out.println("\n\n*****************[ALERT] PropertyGenarator did not find anything to work on**********************");
					System.out.println("*****************[ALERT] Looks like the pom is already updated***********************************\n\n");
				}
			} else {
				System.out.println("\n\n*****************No dependencies found.**********************\n\n");
				System.out.println("*****************pom.xml needs NO update**********************");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println("\n\n\n##########################################################################");
		System.out.println("######################### PropertyGenerator : end ########################");
		System.out.println("##########################################################################\n\n\n");
	}

	private static void createPOMBackupIfNotPresent(String projectPath, File infile)
			throws IOException {
		System.out.println("\n[INFO] Creating a backup of pom.xml...");
		FileInputStream ins = null;
		FileOutputStream outs = null;
		try {
			File outfile = new File(projectPath + "\\DO-NOT-COMMIT_backup_pom.xml");

			ins = new FileInputStream(infile);
			outs = new FileOutputStream(outfile);
			byte[] buffer = new byte[1024];
			int length;

			while ((length = ins.read(buffer)) > 0) {
				outs.write(buffer, 0, length);
			}
			ins.close();
			outs.close();
			System.out.println("\n[SUCCESS] Backup successful !!");
		} catch (IOException ioe) {
			System.out.println("\n[ALERT] : pom.xml backup failed !");
			throw ioe;
		}
	}

	private static boolean addHardcodedVersionProperty(Document document, Node projectNode,
			String artifactId, String version, Node versionNode, boolean isPomUpdated) throws Exception {
		String propertyName = createPropertyName(artifactId);
		System.out.println("\n[INFO] Adding : <" + propertyName + ">" + version + "</" + propertyName + ">");

		boolean isPropertyAdded = addProperties(document, projectNode, propertyName, version);
		
		//If anything is already updated do not need to check again
		if(!isPomUpdated && isPropertyAdded) {
			isPomUpdated = isPropertyAdded; 
		}
		
		//update <version> in <dependency>
		versionNode.setTextContent("${" + propertyName + "}");
		System.out.println("[SUCCESS] dependency version updated !");
		
		return isPomUpdated;
	}

	private static boolean addProjectVersionProperty(Document document, Node projectNode,
			String artifactId, Node versionNode, boolean isPomUpdated) throws Exception {
		String propertyName = createPropertyName(artifactId);
		System.out.println("\n[INFO] Adding : <" + propertyName + ">" + PROJECT_VERSION + "</" + propertyName + ">");
		
		boolean isPropertyAdded = addProperties(document, projectNode, propertyName, PROJECT_VERSION);

		//If anything is already updated do not need to check again
		if(!isPomUpdated && isPropertyAdded) {
			isPomUpdated = isPropertyAdded; 
		}
		
		//update <version> in <dependency>
		versionNode.setTextContent("${" + propertyName + "}");
		System.out.println("[SUCCESS] dependency version updated !");
		
		return isPomUpdated;
	}

	private static void createPropertiesIfNotPresent(Document doc, Node node1, String pomFilePath) throws Exception {
		System.out.println("\n[INFO] Inside <" + node1.getNodeName() + ">, looking for <properties>...");

		boolean isPropertiesPresent = false;

		for (int i = 0; i < node1.getChildNodes().getLength(); i++) {
			if (node1.getChildNodes().item(i).getNodeName().equalsIgnoreCase("properties")) {
				System.out.println("[INFO] <properties> located !");

				isPropertiesPresent = true;
				break;
			}
		}

		if (!isPropertiesPresent) {
			System.out.println("[ALERT] <properties> not found.");
			System.out.println("[INFO] Trying to create <properties>...");
			NodeList dependencies = doc.getElementsByTagName("dependencies");
			Element dependenciesNode = (Element) dependencies.item(0);
			Element element = doc.createElement("properties");
			// node1.appendChild(element);
			node1.insertBefore(element, dependenciesNode);
			node1.insertBefore(doc.createTextNode("\n\n\t"), dependenciesNode);

			saveChanges(pomFilePath, doc);
			System.out.println("[INFO] <properties> successfully created !");
		}
	}

	private static void saveChanges(String pomFilePath, Document doc) throws Exception {
		DOMSource source = new DOMSource(doc);
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		StreamResult result = new StreamResult(pomFilePath);
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.transform(source, result);
	}

	/**
	 * Build the updated pom to verify the changes
	 * 
	 * @param projectPath
	 * @param pomFile
	 * @throws MavenInvocationException
	 */
	private static void buildChanges(String projectPath, File pomFile) throws MavenInvocationException {
		System.out.println("[INFO] Trying to build the updated pom...");
		System.out.println("[INFO] This may take a few minutes, please wait...");
		final String buildOutputFileName = "DO-NOT-COMMIT_updated_pom_build_output.txt";

		final InvocationRequest request = new DefaultInvocationRequest();
		request.setPomFile(pomFile);
		request.setGoals(Arrays.asList("clean", "install", "-l " + buildOutputFileName));
		final Invoker invoker = new DefaultInvoker();
		final InvocationResult invocationResult = invoker.execute(request);

		final int exitCode = invocationResult.getExitCode();
		if (exitCode == 0) {
			System.out.println("[SUCCESS] Updated pom built successfully !");
			final File buildOutput = new File(projectPath + "\\" + buildOutputFileName);
			if (buildOutput.exists()) {
				buildOutput.delete();
			}
		} else {
			System.out.println("[ERROR] Updated pom build failed..!");
			System.out.println("[ERROR] Check build output for details: " + buildOutputFileName);
		}
	}

	/**
	 * This method adds an element inside <properties>
	 * 
	 * returns true if successful, else returns false
	 * 
	 * @param doc
	 * @param node1
	 * @param propertyName
	 * @param propertyValue
	 * @return
	 * @throws Exception
	 */
	private static boolean addProperties(Document doc, Node node1, String propertyName,
			String propertyValue) throws Exception {
		for (int i = 0; i < node1.getChildNodes().getLength(); i++) {
			if (node1.getChildNodes().item(i).getNodeName().equalsIgnoreCase("properties")) {
				Element properties = (Element) node1.getChildNodes().item(i);
				properties.appendChild(doc.createTextNode("\n\t\t"));
				Element element = doc.createElement(propertyName);
				element.setTextContent(propertyValue);
				properties.appendChild(element);
				return true;
			}
		}
		return false;
	}

	private static String createPropertyName(String artifactId) {
		String property = artifactId.replaceAll("-", ".");
		return property + ".version";
	}
}
