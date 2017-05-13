package de.zalando.ep.zalenium.servlet;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("WeakerAccess")
public class TemplateRenderer {
    private final Logger logger = Logger.getLogger(LivePreviewServlet.class.getName());
    private String templateFile;
    private String templateContents;

    public TemplateRenderer(String templateFile) {
        this.templateFile = templateFile;
        loadTemplate();
    }

    private void loadTemplate() {
        if (templateContents == null || templateContents.isEmpty()) {
            InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(templateFile);
            try {
                templateContents = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            } catch (IOException e) {
                templateContents = "";
                logger.log(Level.SEVERE, e.toString(), e);
            }
        }
    }

    // Just to avoid overwriting the original template variable by mistake
    @SuppressWarnings("UnnecessaryLocalVariable")
    private String getTemplateContents() {
        String copyOfTemplateContents = templateContents;
        return copyOfTemplateContents;
    }

    private String getTemplateSection(String section, boolean complete) {
        loadTemplate();
        section = section.replace("{", "").replace("}", "");
        String sectionBeginning = String.format("{{#%s}}", section);
        String sectionEnding = String.format("{{/%s}}", section);
        String templateSection = getTemplateContents();
        if (complete) {
            return templateSection.substring(templateSection.indexOf(sectionBeginning),
                    templateSection.indexOf(sectionEnding) + sectionEnding.length());
        } else {
            return templateSection.substring(templateSection.indexOf(sectionBeginning),
                    templateSection.indexOf(sectionEnding)).replace(sectionBeginning, "");
        }
    }

    private String filterSectionName(String section, String content) {
        section = section.replace("{", "").replace("}", "");
        String sectionBeginning = String.format("{{#%s}}", section);
        String sectionEnding = String.format("{{/%s}}", section);
        return content.replace(sectionBeginning, "").replace(sectionEnding, "");
    }

    private String renderValue(String renderedTemplate, String key, String value) {
        if (renderedTemplate.contains(key)) {
            renderedTemplate = renderedTemplate.replace(key, value);
        } else {
            renderedTemplate = renderedTemplate.replace(getTemplateSection(key, true), key);
            String sectionContent = filterSectionName(key, value);
            renderedTemplate = renderedTemplate.replace(key, sectionContent);
        }
        return renderedTemplate;
    }

    public String renderSection(String section, Map<String, String> renderValues) {
        String templateSection = getTemplateSection(section, false);
        logger.info("Section: " + section + ", value: " + templateSection);
        for (Map.Entry<String, String> mapEntry : renderValues.entrySet()) {
            templateSection = renderValue(templateSection, mapEntry.getKey(), mapEntry.getValue());
        }
        return templateSection;
    }

    public String renderTemplate(Map<String, String> renderValues) {
        String renderedTemplate = getTemplateContents();
        for (Map.Entry<String, String> mapEntry : renderValues.entrySet()) {
            renderedTemplate = renderValue(renderedTemplate, mapEntry.getKey(), mapEntry.getValue());
        }
        return renderedTemplate;
    }
}
