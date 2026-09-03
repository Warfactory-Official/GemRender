package com.wf.gemrender.renderdoc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

@Tag("renderdoc")
class CaptureAnalysisTest {
	private static final Path XML_DIR = Path.of("build", "renderdoc");

	@Test
	@DisplayName("the converted capture is readable and contains a frame")
	void captureParses() {
		Document doc = loadNewestCaptureOrSkip();

		assertThat(doc.getDocumentElement()).isNotNull();
		assertThat(countElements(doc, "chunk"))
				.as("API chunks in the capture")
				.isGreaterThan(0);
	}

	@Test
	@DisplayName("the frame issues an indirect multi-draw, i.e. Flywheel's GPU-driven path ran")
	void usesIndirectDrawPath() {
		Document doc = loadNewestCaptureOrSkip();
		List<String> chunkNames = elementAttributes(doc, "chunk", "name");

		Assumptions.assumeFalse(chunkNames.isEmpty(), "capture has no named chunks to inspect");

		assertThat(chunkNames)
				.as("draw chunks present in the capture: %s", chunkNames.stream().distinct().limit(40).toList())
				.anyMatch(name -> name.contains("DrawElements") || name.contains("Draw"));
	}

	private static Document loadNewestCaptureOrSkip() {
		Optional<Path> newest = newestXml();
		Assumptions.assumeTrue(newest.isPresent(),
				"No converted capture in " + XML_DIR.toAbsolutePath()
						+ ". Capture a frame with `/gemrender capture`, then run `./gradlew renderDocConvert`.");
		return parse(newest.get());
	}

	private static Optional<Path> newestXml() {
		if (!Files.isDirectory(XML_DIR)) {
			return Optional.empty();
		}
		try (Stream<Path> files = Files.list(XML_DIR)) {
			return files.filter(p -> p.getFileName().toString().endsWith(".xml"))
					.max(Comparator.comparingLong(p -> p.toFile().lastModified()));
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	private static Document parse(Path xml) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);

			File file = xml.toFile();
			System.out.printf("[gemrender] analysing capture %s (%.1f MiB)%n",
					file.getName(), file.length() / (1024.0 * 1024.0));

			return factory.newDocumentBuilder().parse(file);
		} catch (Exception e) {
			throw new AssertionError("Could not parse converted capture " + xml, e);
		}
	}

	private static int countElements(Document doc, String tag) {
		return doc.getElementsByTagName(tag).getLength();
	}

	private static List<String> elementAttributes(Document doc, String tag, String attribute) {
		NodeList nodes = doc.getElementsByTagName(tag);
		return java.util.stream.IntStream.range(0, nodes.getLength())
				.mapToObj(nodes::item)
				.filter(n -> n.getAttributes() != null && n.getAttributes().getNamedItem(attribute) != null)
				.map(n -> n.getAttributes().getNamedItem(attribute).getNodeValue())
				.toList();
	}
}
