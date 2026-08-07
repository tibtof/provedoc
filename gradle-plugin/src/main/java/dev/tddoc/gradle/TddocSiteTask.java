package dev.tddoc.gradle;

import dev.tddoc.SiteGen;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates the article site by invoking {@link SiteGen#main} in-process —
 * the plugin adds build wiring (dependencies, up-to-date checks) on top of
 * the exact same entry point the CLI and the copy-pasted single file use.
 */
@DisableCachingByDefault(because = "generation is fast; caching buys nothing over up-to-date checks")
public abstract class TddocSiteTask extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getDocs();

    @OutputDirectory
    public abstract DirectoryProperty getOut();

    @Input
    @Optional
    public abstract Property<String> getSiteName();

    @Input
    @Optional
    public abstract Property<String> getTagline();

    @Input
    @Optional
    public abstract Property<String> getRepo();

    @Input
    @Optional
    public abstract Property<String> getGlyph();

    @Input
    @Optional
    public abstract Property<String> getInstall();

    @Input
    @Optional
    public abstract Property<String> getEditBase();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public abstract DirectoryProperty getJavadoc();

    @Input
    @Optional
    public abstract Property<String> getSiteVersion();

    @Input
    @Optional
    public abstract Property<String> getPrefix();

    @Input
    @Optional
    public abstract Property<String> getChannel();

    @TaskAction
    public void generate() throws Exception {
        List<String> args = new ArrayList<>();
        args.add("--docs");
        args.add(getDocs().get().getAsFile().getPath());
        args.add("--out");
        args.add(getOut().get().getAsFile().getPath());
        addIf(args, "--name", getSiteName());
        addIf(args, "--tagline", getTagline());
        addIf(args, "--repo", getRepo());
        addIf(args, "--glyph", getGlyph());
        addIf(args, "--install", getInstall());
        addIf(args, "--editBase", getEditBase());
        addIf(args, "--version", getSiteVersion());
        addIf(args, "--prefix", getPrefix());
        addIf(args, "--channel", getChannel());
        if (getJavadoc().isPresent()) {
            args.add("--javadoc");
            args.add(getJavadoc().get().getAsFile().getPath());
        }
        SiteGen.main(args.toArray(String[]::new));
    }

    private static void addIf(List<String> args, String flag, Property<String> value) {
        if (value.isPresent()) {
            args.add(flag);
            args.add(value.get());
        }
    }
}
