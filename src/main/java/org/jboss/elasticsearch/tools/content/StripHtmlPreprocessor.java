/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.tools.content;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 * Content preprocessor which takes String value from source field, strip html tags from it, unescape html entities (
 * <code>&amp;lt;</code>, <code>&amp;gt;</code>, <code>&amp;amp;</code> atd) and store result to another or same target
 * field. Example of configuration for this preprocessor:
 * 
 * <pre>
 * { 
 *     "name"     : "HTML content to text description convertor",
 *     "class"    : "org.jboss.elasticsearch.tools.content.StripHtmlPreprocessor",
 *     "settings" : {
 *         "source_field"  : "content",
 *         "target_field"  : "description"
 *     } 
 * }
 * </pre>
 * 
 * Options are:
 * <ul>
 * <li><code>source_field</code> - source field in input data. Dot notation for nested values can be used here (see
 * {@link XContentMapValues#extractValue(String, Map)}).
 * <li><code>target_field</code> - target field in data to store mapped value into. Can be same as input field. Dot
 * notation can be used here for structure nesting.
 * <li><code>source_bases</code> - list of fields in source data which are used as bases for stripping. If defined then
 * stripping is performed for each of this fields, <code>source_field</code> and <code>target_field</code> are resolved
 * relatively against this base. Base must provide object or list of objects.
 * </ul>
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 * @see StructuredContentPreprocessorFactory
 */
public class StripHtmlPreprocessor extends StructuredContentPreprocessorBase {

	protected static final String CFG_SOURCE_FIELD = "source_field";
	protected static final String CFG_TARGET_FIELD = "target_field";
	protected static final String CFG_source_bases = "source_bases";

	protected String fieldSource;
	protected String fieldTarget;
	protected List<String> sourceBases;

	@SuppressWarnings("unchecked")
	@Override
	public void init(Map<String, Object> settings) throws SettingsException {
		if (settings == null) {
			throw new SettingsException("'settings' section is not defined for preprocessor " + name);
		}
		fieldSource = XContentMapValues.nodeStringValue(settings.get(CFG_SOURCE_FIELD), null);
		validateConfigurationStringNotEmpty(fieldSource, CFG_SOURCE_FIELD);
		fieldTarget = XContentMapValues.nodeStringValue(settings.get(CFG_TARGET_FIELD), null);
		validateConfigurationStringNotEmpty(fieldTarget, CFG_TARGET_FIELD);
		sourceBases = (List<String>) settings.get(CFG_source_bases);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Object> preprocessData(Map<String, Object> data) {
		if (data == null)
			return null;

		if (sourceBases == null) {
			processOneSourceValue(data);
		} else {
			for (String base : sourceBases) {
				Object obj = XContentMapValues.extractValue(base, data);
				if (obj != null) {
					if (obj instanceof Map) {
						processOneSourceValue((Map<String, Object>) obj);
					} else if (obj instanceof Collection) {
						for (Object o : (Collection<Object>) obj) {
							if (o instanceof Map) {
								processOneSourceValue((Map<String, Object>) o);
							} else {
								logger.warn("Source base {} contains collection with invalid value to be processed {}", base, obj);
							}
						}
					} else {
						logger.warn("Source base {} contains invalid value to be processed {}", base, obj);
					}
				}
			}
		}
		return data;
	}

	private void processOneSourceValue(Map<String, Object> data) {
		Object v = null;
		if (fieldSource.contains(".")) {
			v = XContentMapValues.extractValue(fieldSource, data);
		} else {
			v = data.get(fieldSource);
		}

		if (v != null) {
			if (!(v instanceof String)) {
				logger.warn("value for field '" + fieldSource + "' is not String, so can't be processed by '" + name
						+ "' preprocessor");
			} else {
				String value = stripHtml(v.toString());
				StructureUtils.putValueIntoMapOfMaps(data, fieldTarget, value);
			}
		}
	}

	protected String stripHtml(String value) {
		if (value == null || value.trim().isEmpty())
			return value;
		Document doc = Jsoup.parse(Jsoup.clean(value, Whitelist.relaxed()));
		return convertNodeToText(doc.body());
	}

	protected String convertNodeToText(Element element) {
		if (element == null) return "";
		final StringBuilder buffer = new StringBuilder();
		new NodeTraversor(new NodeVisitor() {
			@Override
			public void head(Node node, int depth) {
				if (node instanceof TextNode) {
					TextNode textNode = (TextNode) node;
					String text = textNode.text().replace('\u00A0', ' ').trim(); // non breaking space
					if(!text.isEmpty())
					{
						buffer.append(text);
						if (!text.endsWith(" ")) {
							buffer.append(" "); // the last text gets appended the extra space too but we remove it later
						}
					}
				}
			}
			@Override
			public void tail(Node node, int depth) {}
		}).traverse(element);
		String output = buffer.toString();
		if (output.endsWith(" ")) { // removal of the last extra space
			output = output.substring(0, output.length() - 1);
		}
		return output;
	}

	public String getFieldSource() {
		return fieldSource;
	}

	public String getFieldTarget() {
		return fieldTarget;
	}

	public List<String> getSourceBases() {
		return sourceBases;
	}
}
