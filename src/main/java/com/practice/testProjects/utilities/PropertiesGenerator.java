package com.practice.testProjects.utilities;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class PropertiesGenerator {

    private static final String PROJECT_VERSION = "develop-SNAPSHOT";

    public static void main (String[] args) {

        System.out.println ("\n\n\n##########################################################################");
        System.out.println ("######################## PropertyGenerator : start #######################");
        System.out.println ("##########################################################################\n\n\n");

        System.out.println ("Searching for pom.xml...");

        try {

            String workspace = args[0];
            String projectName = args[1];

            System.out.println ("\n*****Received workspace path : " + workspace);
            System.out.println ("*******Received project name : " + projectName);

            String pomFilePath = null;

            if (workspace.endsWith ("\\")) {
                pomFilePath = workspace + projectName + "\\pom.xml";
            } else {
                pomFilePath = workspace + "\\" + projectName + "\\pom.xml";
            }

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance ();
            DocumentBuilder docBuilder;
            docBuilder = docFactory.newDocumentBuilder ();
            Document doc = docBuilder.parse (pomFilePath);

            System.out.println ("\npom.xml parsing successfull !");

            Node node1 = doc.getFirstChild ();
            NodeList depen = doc.getElementsByTagName ("dependency");

            if (depen != null && depen.getLength () > 0) {
                createPropertiesIfNotPresent (doc, node1, pomFilePath);

                for (int in = 0; in < depen.getLength (); in++) {
                    String artifactId = "";
                    String version = "";

                    Node dependency = depen.item (in);

                    Node versionNode = null;

                    for (int in2 = 0; in2 < dependency.getChildNodes ().getLength (); in2++) {
                        Node node = dependency.getChildNodes ().item (in2);

                        if (node != null && "artifactId".equals (node.getNodeName ())) {
                            artifactId = node.getTextContent ();
                        }

                        if (node != null && "version".equals (node.getNodeName ())) {
                            version = node.getTextContent ();
                            versionNode = node;
                        }

                    }

                    if (versionNode != null && version.startsWith ("$")) {
                        if ("${project.version}".equalsIgnoreCase (version)) {
                            String propertyName = createPropertyName (artifactId);
                            System.out.println ("\nAdding : <" + propertyName + ">" + PROJECT_VERSION + "</" + propertyName + ">");
                            addProperties (doc, node1, pomFilePath, propertyName, PROJECT_VERSION);

                            System.out.println ("Trying to update dependency version...");

                            versionNode.setTextContent ("${" + propertyName + "}");
                        }
                    } else if (versionNode != null) {
                        String propertyName = createPropertyName (artifactId);
                        System.out.println ("Adding : <" + propertyName + ">" + version + "</" + propertyName + ">");
                        addProperties (doc, node1, pomFilePath, propertyName, version);

                        System.out.println ("Trying to update dependency version...");

                        versionNode.setTextContent ("${" + propertyName + "}");
                    }
                }

                updatePomFile (pomFilePath, doc);
                System.out.println ("\n\n*****************pom.xml updated successfully**********************\n\n");
            } else {
                System.out.println ("\n\n*****************No dependencies found.**********************\n\n");
                System.out.println ("*****************pom.xml needs NO update**********************");
            }
        } catch (Exception e) {
            e.printStackTrace ();
        }

        System.out.println ("\n\n\n##########################################################################");
        System.out.println ("######################### PropertyGenerator : end ########################");
        System.out.println ("##########################################################################\n\n\n");
    }

    private static void createPropertiesIfNotPresent (Document doc, Node node1, String pomFilePath) throws Exception {
        System.out.println ("\nInside <" + node1.getNodeName () + ">, looking for <properties>...");

        boolean isPropertiesPresent = false;

        for (int i = 0; i < node1.getChildNodes ().getLength (); i++) {
            if (node1.getChildNodes ().item (i).getNodeName ().equalsIgnoreCase ("properties")) {
                System.out.println ("<properties> located !");

                isPropertiesPresent = true;
                break;
            }
        }

        if (!isPropertiesPresent) {
            System.out.println ("<properties> not found. Trying to create <properties>...");

            Element element = doc.createElement ("properties");
            node1.appendChild (element);

            updatePomFile (pomFilePath, doc);
            System.out.println ("<properties> successfully created !");
        }
    }

    private static void updatePomFile (String pomFilePath, Document doc) throws Exception {
        DOMSource source = new DOMSource (doc);
        TransformerFactory transformerFactory = TransformerFactory.newInstance ();
        Transformer transformer = transformerFactory.newTransformer ();
        StreamResult result = new StreamResult (pomFilePath);
        transformer.transform (source, result);
    }

    private static void addProperties (Document doc, Node node1, String pomFilePath, String propertyName, String propertyValue) throws Exception {
        for (int i = 0; i < node1.getChildNodes ().getLength (); i++) {
            if (node1.getChildNodes ().item (i).getNodeName ().equalsIgnoreCase ("properties")) {
                Element properties = (Element) node1.getChildNodes ().item (i);
                Element element = doc.createElement (propertyName);
                element.setTextContent (propertyValue);
                properties.appendChild (element);
            }
        }
    }

    private static String createPropertyName (String artifactId) {
        String property = artifactId.replaceAll ("-", ".");
        return property + ".version";
    }
}
