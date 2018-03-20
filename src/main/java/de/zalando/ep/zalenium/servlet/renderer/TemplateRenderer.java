package de.zalando.ep.zalenium.servlet.renderer;

import de.zalando.ep.zalenium.servlet.LivePreviewServlet;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("WeakerAccess")
public class TemplateRenderer {
    private final Logger logger = LoggerFactory.getLogger(LivePreviewServlet.class.getName());
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
                logger.error(e.toString(), e);
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
        String sectionCopy = section.replace("{", "").replace("}", "");
        String sectionBeginning = String.format("{{#%s}}", sectionCopy);
        String sectionEnding = String.format("{{/%s}}", sectionCopy);
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
        String sectionCopy = section.replace("{", "").replace("}", "");
        String sectionBeginning = String.format("{{#%s}}", sectionCopy);
        String sectionEnding = String.format("{{/%s}}", sectionCopy);
        return content.replace(sectionBeginning, "").replace(sectionEnding, "");
    }

    private String renderValue(String renderedTemplate, String key, String value) {
        String renderedTemplateCopy = renderedTemplate;
        if (renderedTemplateCopy.contains(key)) {
            renderedTemplateCopy = renderedTemplateCopy.replace(key, value);
        } else {
            renderedTemplateCopy = renderedTemplateCopy.replace(getTemplateSection(key, true), key);
            String sectionContent = filterSectionName(key, value);
            renderedTemplateCopy = renderedTemplateCopy.replace(key, sectionContent);
        }
        return renderedTemplateCopy;
    }

    public String renderSection(String section, Map<String, String> renderValues) {
        String templateSection = getTemplateSection(section, false);
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
