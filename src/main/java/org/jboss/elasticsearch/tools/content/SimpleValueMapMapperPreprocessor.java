/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.tools.content;

import java.util.Collection;
import java.util.Map;

import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.xcontent.support.XContentMapValues;

/**
 * Content preprocessor which allows to perform mapping of simple value from source field over configured Map mapping
 * structure to another or same target field. Optional default value can be used for values not found in mapping.
 * Example of configuration for this preprocessor:
 * 
 * <pre>
 * { 
 *     "name"     : "Status Normalizer",
 *     "class"    : "org.jboss.elasticsearch.tools.content.SimpleValueMapMapperPreprocessor",
 *     "settings" : {
 *         "source_field"  : "fields.status.name",
 *         "target_field"  : "dcp_issue_status",
 *         "value_default" : "In Progress",
 *         "value_mapping" : {
 *             "Open"      : "Open",
 *             "Resolved"  : "Closed",
 *             "Closed"    : "Closed"                     
 *         }
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
 * <li><code>value_default</code> - optional default value used if <code>value_mapping</code> Map do not provide
 * mapping. If not set then target field is leaved empty for values not found in mapping. You can use pattern for keys
 * replacement from other values in data in default value. Keys are enclosed in curly braces, dot notation for deeper
 * nesting may be used in keys. Special key '<code>__original</code>' means that original value from source field will
 * be used here. Example of default value with replacement keys ' <code>No mapping found for value {__original}.</code>
 * '.
 * <li><code>value_mapping</code> - Map structure for value mapping. Key is value from <code>source_field</code>, Value
 * is value for for <code>target_field</code>.
 * </ul>
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 * @see StructuredContentPreprocessorFactory
 * @see ValueUtils#processStringValuePatternReplacement(String, Map, Object)
 */
public class SimpleValueMapMapperPreprocessor extends StructuredContentPreprocessorBase {

	protected static final String CFG_SOURCE_FIELD = "source_field";
	protected static final String CFG_TARGET_FIELD = "target_field";
	protected static final String CFG_VALUE_DEFAULT = "value_default";
	protected static final String CFG_VALUE_MAPPING = "value_mapping";

	protected String fieldSource;
	protected String fieldTarget;
	protected String defaultValue = null;
	protected Map<String, String> valueMap = null;

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
		defaultValue = ValueUtils.trimToNull(XContentMapValues.nodeStringValue(settings.get(CFG_VALUE_DEFAULT), null));
		valueMap = (Map<String, String>) settings.get(CFG_VALUE_MAPPING);
		if (valueMap == null || valueMap.isEmpty()) {
			logger.warn("'settings/" + CFG_VALUE_MAPPING + "' is not defined for preprocessor '{}'", name);
		}
	}

	@Override
	public Map<String, Object> preprocessData(Map<String, Object> data) {
		if (data == null)
			return null;

		Object v = null;
		if (fieldSource.contains(".")) {
			v = XContentMapValues.extractValue(fieldSource, data);
		} else {
			v = data.get(fieldSource);
		}

		if (v == null) {
			putDefaultValue(data, null);
		} else if (v instanceof Map || v instanceof Collection || v.getClass().isArray()) {
			logger
					.warn("value for field '" + fieldSource
							+ "' is not simple value (but is List or Array or Map), so can't be processed by '" + name
							+ "' preprocessor");
		} else {
			String origValue = v.toString();
			String newVal = null;
			if (valueMap != null && !ValueUtils.isEmpty(origValue))
				newVal = valueMap.get(origValue);
			if (newVal != null) {
				putTargetValue(data, newVal);
			} else {
				putDefaultValue(data, origValue);
			}
		}
		return data;
	}

	private void putDefaultValue(Map<String, Object> data, String originalValue) {
		if (defaultValue != null) {
			putTargetValue(data, ValueUtils.processStringValuePatternReplacement(defaultValue, data, originalValue));
		}
	}

	protected void putTargetValue(Map<String, Object> data, String value) {
		StructureUtils.putValueIntoMapOfMaps(data, fieldTarget, value);
	}

	public String getFieldSource() {
		return fieldSource;
	}

	public String getFieldTarget() {
		return fieldTarget;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public Map<String, String> getValueMap() {
		return valueMap;
	}

}
