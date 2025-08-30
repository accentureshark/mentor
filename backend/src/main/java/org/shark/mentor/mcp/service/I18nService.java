package org.shark.mentor.mcp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.config.UiProperties;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * Service for handling internationalization and message formatting
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class I18nService {
    
    private final UiProperties uiProperties;
    
    public String getMessage(String key, Object... args) {
        String template = getMessageTemplate(key);
        if (args.length > 0) {
            // Use simple string format for compatibility
            return String.format(template, args);
        }
        return template;
    }
    
    private String getMessageTemplate(String key) {
        UiProperties.Messages messages = uiProperties.getMessages();
        
        return switch (key) {
            case "success.contact" -> messages.getSuccessPrefix() + " " + messages.getSuccessfulContact();
            case "response.from" -> messages.getSuccessPrefix() + " **" + messages.getResponseFrom() + "**\n\n";
            case "info.provided.by" -> "\n\n" + messages.getInfoPrefix() + " *" + messages.getInformationProvidedBy() + "*";
            case "tools.enabled" -> messages.getSuccessPrefix() + " " + messages.getDynamicToolsetsEnabled();
            case "context.mcp" -> messages.getMcpServerContext();
            case "instructions.formatting" -> messages.getFormattingInstructions();
            case "prefix.success" -> messages.getSuccessPrefix();
            case "prefix.error" -> messages.getErrorPrefix();
            case "prefix.warning" -> messages.getWarningPrefix();
            case "prefix.info" -> messages.getInfoPrefix();
            case "prefix.data" -> messages.getDataPrefix();
            case "prefix.tool" -> messages.getToolPrefix();
            case "prefix.code" -> messages.getCodePrefix();
            case "prefix.file" -> messages.getFilePrefix();
            case "prefix.structure" -> messages.getStructurePrefix();
            case "prefix.chart" -> messages.getChartPrefix();
            case "schemas.list.header" -> messages.getDataPrefix() + " **" + messages.getSchemasListHeader() + "**";
            case "schema.item.template" -> messages.getSchemasListHeader();
            default -> {
                log.warn("Unknown message key: {}", key);
                yield key;
            }
        };
    }
    
    public Locale getCurrentLocale() {
        return Locale.forLanguageTag(uiProperties.getLocale());
    }
    
    public boolean isSpanishLocale() {
        return "es".equals(uiProperties.getLocale()) || 
               "es-ES".equals(uiProperties.getLocale()) ||
               "es-MX".equals(uiProperties.getLocale()) ||
               "es-AR".equals(uiProperties.getLocale());
    }
}