package com.self.tool.maven.utility;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.fuin.utils4j.InvokeMethodFailedException;
import org.fuin.utils4j.Utils4J;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class MavenPomReader {

	private MavenPomReader() {
		throw new UnsupportedOperationException("");
	}

	private static String createPath(final String groupId, final String artifactId) {
		return groupId.replace('.', '/') + "/" + artifactId;
	}

	/**
	 * Returns the repository path of an artifact.
	 * 
	 * @param groupId
	 *            Group ID - Cannot be <code>null</code>.
	 * @param artifactId
	 *            Artifact ID - Cannot be <code>null</code>.
	 * @param version
	 *            Version - Cannot be <code>null</code>.
	 * @param extension
	 *            File extension - Cannot be <code>null</code>.
	 * 
	 * @return Repository path.
	 */
	public static String createPathAndFilename(final String groupId, final String artifactId, final String version,
			final String extension) {
		final String filename = artifactId + "-" + version + "." + extension;
		return createPath(groupId, artifactId) + "/" + version + "/" + filename;
	}

	private static String toGetter(final String field) {
		return "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
	}

	private static String toIs(final String field) {
		return "is" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
	}

	private static String toSetter(final String field) {
		return "set" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
	}

	private static Object get(final Object src, final String field) {
		try {
			return Utils4J.invoke(src, toGetter(field), new Class[] {}, new Object[] {});
		} catch (InvokeMethodFailedException ex) {
			try {
				return Utils4J.invoke(src, toIs(field), new Class[] {}, new Object[] {});
			} catch (InvokeMethodFailedException ex2) {
				throw new RuntimeException("Error getting field '" + field + "' from '" + src + "'!", ex2);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void set(final Object dest, final String field, final Object value, final Class clasz) {
		try {
			Utils4J.invoke(dest, toSetter(field), new Class[] { clasz }, new Object[] { value });
		} catch (InvokeMethodFailedException ex) {
			throw new RuntimeException("Error setting value '" + value + "' for '" + field + "' in '" + dest + "'!",
					ex);
		}
	}

	private static void set(final Object dest, final String field, final Object value) {
		try {
			Utils4J.invoke(dest, toSetter(field), new Class[] { value.getClass() }, new Object[] { value });
		} catch (InvokeMethodFailedException ex) {
			if (value instanceof Boolean) {
				set(dest, field, value, boolean.class);
			} else if (value instanceof Integer) {
				set(dest, field, value, int.class);
			} else if (value instanceof Long) {
				set(dest, field, value, long.class);
			} else if (value instanceof Short) {
				set(dest, field, value, short.class);
			} else {
				throw new RuntimeException("Error setting value '" + value + "' for '" + field + "' in '" + dest + "'!",
						ex);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static Object createInstance(final Class clasz) {
		try {
			return clasz.newInstance();
		} catch (InstantiationException ex) {
			throw new RuntimeException("Error creating new instance '" + clasz + "'!", ex);
		} catch (IllegalAccessException ex) {
			throw new RuntimeException("Error creating new instance '" + clasz + "'!", ex);
		}
	}

	private static boolean isBaseObject(final Object obj) {
		if (obj instanceof String) {
			return true;
		}
		if (obj instanceof Integer) {
			return true;
		}
		if (obj instanceof Long) {
			return true;
		}
		if (obj instanceof Short) {
			return true;
		}
		if (obj instanceof Boolean) {
			return true;
		}
		if (obj.getClass() == int.class) {
			return true;
		}
		if (obj.getClass() == long.class) {
			return true;
		}
		if (obj.getClass() == short.class) {
			return true;
		}
		if (obj.getClass() == boolean.class) {
			return true;
		}
		return false;
	}

	private static Object copy(final Object from) {
		if (isBaseObject(from)) {
			return from;
		} else {
			final Object to = createInstance(from.getClass());
			copyObjectFields(from, to);
			return to;
		}
	}

	@SuppressWarnings("unchecked")
	private static void copyList(final List<Object> fromValue, final Object to, final String field) {
		List<Object> toList = (List<Object>) get(to, field);
		if (toList == null) {
			toList = (List<Object>) createInstance(fromValue.getClass());
			set(to, field, toList);
		}
		for (int i = 0; i < fromValue.size(); i++) {
			final Object fromObj = fromValue.get(i);
			if (fromObj != null) {
				toList.add(copy(fromObj));
			}
		}
	}

	private static void copyProperties(final Properties fromProps, final Object to, final String field) {
		Properties toProps = (Properties) get(to, field);
		if (toProps == null) {
			toProps = (Properties) createInstance(fromProps.getClass());
			set(to, field, toProps);
		}
		final Iterator<Object> it = fromProps.keySet().iterator();
		while (it.hasNext()) {
			final Object fromKey = it.next();
			final Object fromValue = fromProps.get(fromKey);
			if (fromValue != null) {
				final Object toKey = copy(fromKey);
				final Object toValue = copy(fromValue);
				copyObjectFields(fromValue, toValue);
				toProps.put(toKey, toValue);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static List<String> getFields(final Object obj) {
		final List<String> fields = new ArrayList<String>();
		final Method[] methods = obj.getClass().getMethods();
		for (int i = 0; i < methods.length; i++) {
			final String field;
			if (methods[i].getName().startsWith("get")) {
				field = methods[i].getName().substring(3);
			} else if (methods[i].getName().startsWith("is")) {
				field = methods[i].getName().substring(2);
			} else {
				field = null;
			}
			if (field != null) {
				try {
					final Class type = methods[i].getReturnType();
					obj.getClass().getMethod(toSetter(field), new Class[] { type });
					fields.add(field);
				} catch (final NoSuchMethodException ex) {
					// Ignore
				}
			}
		}
		return fields;
	}

	@SuppressWarnings("unchecked")
	private static void copyObjectFields(final Object from, final Object to) {
		if ((from != null) && (to != null)) {
			final List<String> fields = getFields(from);
			for (int i = 0; i < fields.size(); i++) {
				final String field = fields.get(i);
				final Object fromValue = get(from, field);
				if (fromValue != null) {
					if (isBaseObject(fromValue)) {
						set(to, field, fromValue);
					} else if (fromValue instanceof List) {
						copyList((List) fromValue, to, field);
					} else if (fromValue instanceof Properties) {
						copyProperties((Properties) fromValue, to, field);
					} else if (fromValue.getClass().getPackage().getName().equals("org.apache.maven.model")) {
						copyObjectField(fromValue, to, field);
					} else {
						if (fromValue instanceof Xpp3Dom) {
							set(to, field, fromValue, Object.class);
						} else {
							throw new IllegalArgumentException("Cannot copy field '" + field + "' of type '"
									+ fromValue.getClass().getName() + "' from '" + from + "' to '" + to + "'!");
						}
					}
				}
			}
		}
	}

	private static void copyObjectField(final Object fromValue, final Object to, final String field) {
		Object toValue = get(to, field);
		if (toValue == null) {
			toValue = createInstance(fromValue.getClass());
			set(to, field, fromValue);
		}
		copyObjectFields(fromValue, toValue);
	}

	private static Model merge(final Model parent, final Model child) {
		final Model newModel = new Model();
		copyObjectFields(parent, newModel);
		copyObjectFields(child, newModel);
		return newModel;
	}

	private static String findLatestVersion(final File repositoryDir, final String groupId, final String artifactId) {

		final File dir = new File(repositoryDir, createPath(groupId, artifactId));
		final File file = new File(dir, "maven-metadata-local.xml");
		if (!file.exists()) {
			throw new IllegalArgumentException("File '" + file + "' not found!");
		}

		final String path = "//versions/version/text()";

		String latest = null;
		try {
			final DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
			domFactory.setNamespaceAware(false);
			final DocumentBuilder builder = domFactory.newDocumentBuilder();
			final Document doc = builder.parse(file);
			final XPathFactory factory = XPathFactory.newInstance();
			final XPath xpath = factory.newXPath();
			final XPathExpression expr = xpath.compile(path);
			final Object result = expr.evaluate(doc, XPathConstants.NODESET);
			final NodeList nodes = (NodeList) result;
			if (nodes.getLength() == 0) {
				throw new IllegalStateException("Pfad '" + path + "' nicht gefunden in '" + file + "'!");
			}
			for (int i = 0; i < nodes.getLength(); i++) {
				final Node node = (Node) nodes.item(i);
				latest = node.getNodeValue();
			}
		} catch (final Exception ex) {
			throw new RuntimeException(ex);
		}

		return latest;
	}

	/**
	 * Read the latest version of a POM from a local repository.
	 * 
	 * @param repositoryDir
	 *            Repository path.
	 * @param groupId
	 *            Group ID.
	 * @param artifactId
	 *            Artifact ID.
	 * 
	 * @return Model created from POM and Super-POM.
	 */
	public static Model readModel(final File repositoryDir, final String groupId, final String artifactId) {

		return readModel(repositoryDir, groupId, artifactId, null);

	}

	/**
	 * Read a POM from a local repository.
	 * 
	 * @param repositoryDir
	 *            Repository path.
	 * @param groupId
	 *            Group ID.
	 * @param artifactId
	 *            Artifact ID.
	 * @param version
	 *            Version - If <code>null</code> the latest version will be
	 *            used.
	 * 
	 * @return Model created from POM and Super-POM.
	 */
	public static Model readModel(final File repositoryDir, final String groupId, final String artifactId,
			final String version) {

		final String latestVersion;
		if (version == null) {
			latestVersion = findLatestVersion(repositoryDir, groupId, artifactId);
			if (latestVersion == null) {
				throw new IllegalStateException(
						"Latest version for '" + groupId + ":" + artifactId + "' not found! [" + repositoryDir + "]");
			}
		} else {
			latestVersion = version;
		}

		final File pomXmlFile = new File(repositoryDir,
				createPathAndFilename(groupId, artifactId, latestVersion, "pom"));

		try {
			final Reader reader = new FileReader(pomXmlFile);
			final Model model;
			try {
				final MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
				model = xpp3Reader.read(reader);
			} finally {
				reader.close();
			}
			if (model.getParent() == null) {
				return model;
			} else {
				final String parentGroupId = model.getParent().getGroupId();
				final String parentArtifactId = model.getParent().getArtifactId();
				final String parentVersion = model.getParent().getVersion();
				final Model parentModel = readModel(repositoryDir, parentGroupId, parentArtifactId, parentVersion);
				return merge(parentModel, model);
			}
		} catch (XmlPullParserException ex) {
			throw new RuntimeException("Error parsing POM!", ex);
		} catch (final IOException ex) {
			throw new RuntimeException("Error reading POM!", ex);
		}

	}

	/**
	 * Read a POM from a workspace.
	 * 
	 * @param workspace
	 *            Repository path.
	 * @param groupId
	 *            Group ID.
	 * @param artifactId
	 *            Artifact ID.
	 * @param version
	 *            Version - If <code>null</code> the latest version will be
	 *            used.
	 * 
	 * @return Model created from POM and Super-POM.
	 */
	public static Model readModel(final File workspace, final String projectName) {

		final File pomXmlFile = new File(workspace, projectName + "\\sample_pom.xml");

		try {
			final Reader reader = new FileReader(pomXmlFile);
			final Model model;
			try {
				final MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
				model = xpp3Reader.read(reader);
			} finally {
				reader.close();
			}
			if (model.getParent() == null) {
				return model;
			} else {
				final String parentArtifactId = model.getParent().getArtifactId();
				final Model parentModel = readModel(workspace, parentArtifactId);
				return merge(parentModel, model);
			}
		} catch (XmlPullParserException ex) {
			throw new RuntimeException("Error parsing POM!", ex);
		} catch (final IOException ex) {
			throw new RuntimeException("Error reading POM!", ex);
		}

	}
}
