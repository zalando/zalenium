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
        logger.info("Template contents: " + templateSection);
        if (complete) {
            return templateSection.substring(templateSection.indexOf(sectionBeginning),
                    templateSection.indexOf(sectionEnding) + sectionEnding.length());
        } else {
            return templateSection.substring(templateSection.indexOf(sectionBeginning),
                    templateSection.indexOf(sectionEnding)).replace(sectionBeginning, "");
        }
    }

    public String renderSection(String section, Map<String, String> renderValues) {
        String templateSection = getTemplateSection(section, false);
        logger.info("Section: " + section + ", value: " + templateSection);
        for (Map.Entry<String, String> mapEntry : renderValues.entrySet()) {
            templateSection = templateSection.replace(mapEntry.getKey(), mapEntry.getValue());
        }
        return templateSection;
    }

    public String renderTemplate(Map<String, String> renderValues) {
        String renderedTemplate = getTemplateContents();
        for (Map.Entry<String, String> mapEntry : renderValues.entrySet()) {
            if (renderedTemplate.contains(mapEntry.getKey())) {
                renderedTemplate = renderedTemplate.replace(mapEntry.getKey(), mapEntry.getValue());
            } else {
                logger.info("Replacing section: " + mapEntry.getKey());
                logger.info("Section contents: " + getTemplateSection(mapEntry.getKey(), true));
                renderedTemplate = renderedTemplate.replace(getTemplateSection(mapEntry.getKey(), true), mapEntry.getKey());
                renderedTemplate = renderedTemplate.replace(mapEntry.getKey(), mapEntry.getValue());
            }
        }
        return renderedTemplate;
    }
}
