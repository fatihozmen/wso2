/*
 * Copyright 2020 WSO2, Inc. (http://wso2.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.micro.integrator.log4j.plugins;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.wso2.micro.integrator.core.util.MicroIntegratorBaseUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;

@Plugin(name = "LogMaskConverter", category = "Converter")
@ConverterKeys({"mm"})
/**
 * Log Masking converter to mask logs.
 * Converter key mm used to change existing message pattern in log4j2.properties
 */
public class LogMaskConverter extends LogEventPatternConverter {

    private static final LogMaskConverter INSTANCE = new LogMaskConverter();
    private static final String DEFAULT_MASKING_PATTERNS_FILE_NAME = "wso2-log-masking.properties";
    private static final String DEFAULT_REPLACEMENT_PATTERNS_FILE_NAME = "wso2-log-masking-regex.properties";
    private static final String REPLACEMENT_STRING = "*";
    private static final String MASK_PATTERN = "mm";

    private final List<Pattern> logMaskingPatterns;
    private final List<Pattern> logReplacementPatterns;
    private static List<String> logReplacementStrings;
    private boolean isMaskAvailable = true;

    public static LogMaskConverter newInstance(String[] options) {
        return INSTANCE;
    }

    protected LogMaskConverter() {

        super(MASK_PATTERN, MASK_PATTERN);
        logMaskingPatterns = new ArrayList<>();
        logReplacementPatterns = new ArrayList<>();
        logReplacementStrings = new ArrayList<>();
        loadMaskingPatterns();
    }

    @Override
    public void format(LogEvent logEvent, StringBuilder stringBuilder) {

        String message = logEvent.getMessage().getFormat();

        // Check whether there are any masking patterns defined.
        if (this.isMaskAvailable) {
            Matcher matcher;
            Matcher matcher2;
            
            for (Pattern pattern : logMaskingPatterns) {
                matcher = pattern.matcher(message);
                StringBuffer stringBuffer = new StringBuffer();
                while (matcher.find()) {
                    String maskedSubString = message.substring(matcher.start(),matcher.end());
                    Pattern replacemenPattern = logReplacementPatterns.get(logMaskingPatterns.indexOf(pattern));
                    matcher2 = replacemenPattern.matcher(maskedSubString);
                    maskedSubString = matcher2.replaceAll(logReplacementStrings.get(logMaskingPatterns.indexOf(pattern)));
                    matcher.appendReplacement(stringBuffer, maskedSubString);
                }
                matcher.appendTail(stringBuffer);
                message = stringBuffer.toString();
                
            }
        }
        stringBuilder.append(message);
    }

    /**
     * Method to get the masking patterns (regex) from the properties file.
     */
    private void loadMaskingPatterns() {

        String defaultFile = MicroIntegratorBaseUtils.getCarbonConfigDirPath() + File.separatorChar +
                             DEFAULT_MASKING_PATTERNS_FILE_NAME;
        Properties properties = new Properties();
        InputStream propsStream = null;
        String replaceStr = ".REPLACE";
        String replacerStr = ".REPLACER";
        try {
            // If the masking file is not configured, load the configs from the default file.
            if (Files.exists(Paths.get(defaultFile))) {
                propsStream = new FileInputStream(defaultFile);
                properties.load(propsStream);
                HashMap<String, Pattern> mapPaterns = new HashMap<String, Pattern>();
                HashMap<String, String> mapReplacementStr = new HashMap<String, String>();
                for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                    if(((String) entry.getKey()).contains(replacerStr)){
                        mapReplacementStr.put(((String) entry.getKey()),((String) entry.getValue()));
                    }
                    else{
                        mapPaterns.put(((String) entry.getKey()),(Pattern.compile((String) entry.getValue())));
                    }
                    
                }
                for (String i : mapPaterns.keySet()) {
                    if(!i.contains(replaceStr)){
                        logMaskingPatterns.add(mapPaterns.get(i));
                        String tempStr = i+replaceStr;
                        logReplacementPatterns.add(mapPaterns.get(tempStr));
                        String tempStr2 = i+replacerStr;
                        if(mapReplacementStr.containsKey(tempStr2)){
                            logReplacementStrings.add(mapReplacementStr.get(tempStr2));}
                        else{
                            logReplacementStrings.add(REPLACEMENT_STRING);
                        }
                    }
                    
                }
                this.isMaskAvailable = true;
            }
        } catch (IOException e) {
            // If the masking patterns cannot be loaded print an error message.
            System.err.println("Error loading the masking patterns, due to : " + e.getMessage());
        } catch (java.lang.NullPointerException e) {
            // If the masking patterns cannot be loaded print an error message.
            System.err.println("Error loading the masking patterns, due to : " + e.getMessage());
        } finally {
            if (propsStream != null) {
                try {
                    propsStream.close();
                } catch (IOException ignore) {
                }
            }
        }
    }
}