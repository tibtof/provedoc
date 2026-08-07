package dev.tddoc.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Registers the {@code tddoc} extension and the {@code tddocSite} task. The
 * task runs after {@code test} (examples must pass before they publish) and
 * picks up {@code javadoc} output when the java plugin is present.
 */
public class TddocPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        var ext = project.getExtensions().create("tddoc", TddocExtension.class);
        ext.getDocs().convention(project.getLayout().getProjectDirectory().dir("src/test/java"));
        ext.getOut().convention(project.getLayout().getBuildDirectory().dir("site"));
        ext.getVersion().convention(project.provider(() -> String.valueOf(project.getVersion())));

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

        project.getPlugins().withId("java", p -> site.configure(task ->
                task.dependsOn(project.getTasks().named("test"))));
    }
}
