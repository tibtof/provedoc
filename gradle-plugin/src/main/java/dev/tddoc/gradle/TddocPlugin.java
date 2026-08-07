package dev.tddoc.gradle;

import dev.tddoc.SiteGen;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Registers the {@code tddoc} extension and the {@code tddocSite} task. The
 * task runs after {@code test} (examples must pass before they publish) and
 * picks up {@code javadoc} output when the java plugin is present.
 *
 * <p>A {@code tddoc.yml} in the project root is the zero-config path: its
 * values become the extension's conventions (parsed by the same
 * {@link SiteGen#readConfig} the CLI uses), so applying the plugin needs no
 * {@code tddoc {}} block at all. Explicit extension values always win.
 */
public class TddocPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        var ext = project.getExtensions().create("tddoc", TddocExtension.class);
        ext.getDocs().convention(project.getLayout().getProjectDirectory().dir("src/test/java"));
        ext.getOut().convention(project.getLayout().getBuildDirectory().dir("site"));
        ext.getVersion().convention(project.provider(() -> String.valueOf(project.getVersion())));

        var yml = project.getLayout().getProjectDirectory().file("tddoc.yml").getAsFile();
        if (yml.isFile()) {
            Map<String, String> config;
            try {
                config = SiteGen.readConfig(yml.toPath());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            var dir = project.getLayout().getProjectDirectory();
            if (config.containsKey("docs")) {
                ext.getDocs().convention(dir.dir(config.get("docs")));
            }
            if (config.containsKey("out")) {
                ext.getOut().convention(dir.dir(config.get("out")));
            }
            if (config.containsKey("javadoc")) {
                ext.getJavadoc().convention(dir.dir(config.get("javadoc")));
            }
            if (config.containsKey("version")) {
                ext.getVersion().convention(config.get("version"));
            }
            if (config.containsKey("name")) {
                ext.getName().convention(config.get("name"));
            }
            if (config.containsKey("tagline")) {
                ext.getTagline().convention(config.get("tagline"));
            }
            if (config.containsKey("repo")) {
                ext.getRepo().convention(config.get("repo"));
            }
            if (config.containsKey("glyph")) {
                ext.getGlyph().convention(config.get("glyph"));
            }
            if (config.containsKey("install")) {
                ext.getInstall().convention(config.get("install"));
            }
            if (config.containsKey("editBase")) {
                ext.getEditBase().convention(config.get("editBase"));
            }
            if (config.containsKey("prefix")) {
                ext.getPrefix().convention(config.get("prefix"));
            }
            if (config.containsKey("channel")) {
                ext.getChannel().convention(config.get("channel"));
            }
        }

        var site = project.getTasks().register("tddocSite", TddocSiteTask.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Generates the tddoc article site from doc-tests.");
            task.getDocs().set(ext.getDocs());
            task.getOut().set(ext.getOut());
            task.getSiteName().set(ext.getName());
            task.getTagline().set(ext.getTagline());
            task.getRepo().set(ext.getRepo());
            task.getGlyph().set(ext.getGlyph());
            task.getInstall().set(ext.getInstall());
            task.getEditBase().set(ext.getEditBase());
            task.getJavadoc().set(ext.getJavadoc());
            task.getSiteVersion().set(ext.getVersion());
            task.getPrefix().set(ext.getPrefix());
            task.getChannel().set(ext.getChannel());
        });

        if (yml.isFile()) {
            site.configure(task -> task.getInputs().file(yml).withPathSensitivity(
                    org.gradle.api.tasks.PathSensitivity.RELATIVE));
        }
        project.getPlugins().withId("java", p -> site.configure(task ->
                task.dependsOn(project.getTasks().named("test"))));
    }
}
