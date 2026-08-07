package dev.tddoc.gradle;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TddocPluginTest {

    @Test
    void registers_extension_and_site_task() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("dev.tddoc");

        assertNotNull(project.getExtensions().findByName("tddoc"));
        assertInstanceOf(TddocSiteTask.class, project.getTasks().getByName("tddocSite"));
    }

    @Test
    void extension_values_reach_the_task() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("dev.tddoc");

        var ext = project.getExtensions().getByType(TddocExtension.class);
        ext.getName().set("example");
        ext.getDocs().set(project.file("docs"));

        var task = (TddocSiteTask) project.getTasks().getByName("tddocSite");
        assertEquals("example", task.getSiteName().get());
        assertEquals(project.file("docs"), task.getDocs().get().getAsFile());
    }

    @Test
    void tddoc_yml_supplies_conventions_but_explicit_settings_win() throws Exception {
        Project project = ProjectBuilder.builder().build();
        java.nio.file.Files.writeString(project.file("tddoc.yml").toPath(), """
                name: fromconfig
                tagline: from the yml
                docs: doc-tests
                """);
        project.getPlugins().apply("dev.tddoc");

        var ext = project.getExtensions().getByType(TddocExtension.class);
        ext.getName().set("explicit");

        var task = (TddocSiteTask) project.getTasks().getByName("tddocSite");
        assertEquals("explicit", task.getSiteName().get());
        assertEquals("from the yml", task.getTagline().get());
        assertEquals(project.file("doc-tests"), task.getDocs().get().getAsFile());
    }

    @Test
    void site_task_runs_after_tests_when_java_is_applied() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply("dev.tddoc");

        var task = project.getTasks().getByName("tddocSite");
        var dependsOnTest = task.getDependsOn().stream()
                .anyMatch(d -> d.toString().contains("test"));
        assertEquals(true, dependsOnTest);
    }
}
