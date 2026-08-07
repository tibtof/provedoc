package dev.tddoc.maven;

import dev.tddoc.SiteGen;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the tddoc article site from doc-tests by invoking
 * {@link SiteGen#main} in-process — the same entry point the CLI and the
 * copy-pasted single file use. Bound to {@code verify} so the suite has
 * passed before the site is built.
 */
@Mojo(name = "site", defaultPhase = LifecyclePhase.VERIFY)
public class SiteMojo extends AbstractMojo {

    /** Directory containing the {@code *DocTest.java} sources. */
    @Parameter(property = "tddoc.docs", defaultValue = "${project.basedir}/src/test/java")
    private File docs;

    /** Output directory for the generated site. */
    @Parameter(property = "tddoc.out", defaultValue = "${project.build.directory}/site")
    private File out;

    /** Site name shown in the header. */
    @Parameter(property = "tddoc.name")
    private String name;

    /** Tagline under the site name. */
    @Parameter(property = "tddoc.tagline")
    private String tagline;

    /** Repository URL for header and footer links. */
    @Parameter(property = "tddoc.repo")
    private String repo;

    /** One-letter favicon glyph. */
    @Parameter(property = "tddoc.glyph")
    private String glyph;

    /** Install snippet for the landing page; {@code {version}} is substituted. */
    @Parameter(property = "tddoc.install")
    private String install;

    /** Base URL for "edit this page" links. */
    @Parameter(property = "tddoc.editBase")
    private String editBase;

    /** Javadoc directory folded in under {@code /api/}. */
    @Parameter(property = "tddoc.javadoc")
    private File javadoc;

    /** Version rendered on the site; defaults to the project version. */
    @Parameter(property = "tddoc.version", defaultValue = "${project.version}")
    private String version;

    /** Path prefix for versioned deploys. */
    @Parameter(property = "tddoc.prefix")
    private String prefix;

    /** Version-selector label for this build. */
    @Parameter(property = "tddoc.channel")
    private String channel;

    @Override
    public void execute() throws MojoExecutionException {
        List<String> args = new ArrayList<>();
        args.add("--docs");
        args.add(docs.getPath());
        args.add("--out");
        args.add(out.getPath());
        addIf(args, "--name", name);
        addIf(args, "--tagline", tagline);
        addIf(args, "--repo", repo);
        addIf(args, "--glyph", glyph);
        addIf(args, "--install", install);
        addIf(args, "--editBase", editBase);
        addIf(args, "--version", version);
        addIf(args, "--prefix", prefix);
        addIf(args, "--channel", channel);
        if (javadoc != null) {
            args.add("--javadoc");
            args.add(javadoc.getPath());
        }
        try {
            SiteGen.main(args.toArray(String[]::new));
        } catch (Exception e) {
            throw new MojoExecutionException("tddoc site generation failed", e);
        }
        getLog().info("tddoc site: " + out);
    }

    private static void addIf(List<String> args, String flag, String value) {
        if (value != null && !value.isEmpty()) {
            args.add(flag);
            args.add(value);
        }
    }
}
